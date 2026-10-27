package com.cheesecomer.cartmate.data.firestore

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cheesecomer.cartmate.domain.list.ListDisplay
import com.cheesecomer.cartmate.domain.model.HouseholdId
import com.cheesecomer.cartmate.domain.model.Item
import com.cheesecomer.cartmate.domain.model.ItemId
import com.cheesecomer.cartmate.domain.model.ItemStatus
import com.cheesecomer.cartmate.domain.model.ListId
import com.cheesecomer.cartmate.domain.model.ListStatus
import com.cheesecomer.cartmate.domain.model.NewItem
import com.cheesecomer.cartmate.domain.model.NewList
import com.cheesecomer.cartmate.domain.model.UserId
import com.cheesecomer.cartmate.domain.model.Visibility
import com.cheesecomer.cartmate.domain.repository.CompleteResult
import com.cheesecomer.cartmate.domain.repository.DeleteListResult
import com.cheesecomer.cartmate.domain.repository.JoinResult
import com.cheesecomer.cartmate.domain.repository.RejectedCheck
import com.cheesecomer.cartmate.domain.repository.ReopenResult
import com.cheesecomer.cartmate.testing.EmulatorTest
import com.google.common.truth.Truth.assertThat
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Collections

/**
 * 2台の端末での同時実行（docs/testing.md「同時実行」の 1〜4, 7〜10）。アプリと同じリポジトリで書き込み、
 * 書き込みがすべて落ち着いてから、サーバーの状態と、それぞれの端末に返った結果を確かめる
 */
@EmulatorTest
@RunWith(AndroidJUnit4::class)
class ConcurrencyTest {

    private val clients = mutableListOf<EmulatorClient>()
    /** 通知の購読。テストの本体とは別に動かし、終わったら止める */
    private val collectors = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Before
    fun onlyWhenRequested() {
        EmulatorClient.assumeRequested()
    }

    @After
    fun tearDown(): Unit = runBlocking {
        collectors.cancel()
        clients.forEach { runCatching { it.close() } }
    }

    /** taro の家族に hanako が参加し、共有のリストに未購入の「牛乳」がある。両方の端末に反映済み */
    private class Family(val taro: EmulatorClient, val hanako: EmulatorClient, val householdId: HouseholdId, val listId: ListId, val itemId: ItemId)

    private suspend fun family(): Family {
        val taro = EmulatorClient.signIn("taro").also(clients::add)
        val hanako = EmulatorClient.signIn("hanako").also(clients::add)
        val householdId = taro.households.createHousehold(taro.account)
        taro.awaitHousehold(householdId)
        val invite = taro.households.createInvite("太郎")
        assertThat(hanako.households.join(invite, hanako.account)).isEqualTo(JoinResult.Joined)
        hanako.awaitHousehold(householdId)
        val listId = taro.store.createList(NewList("スーパー", memo = null, Visibility.SHARED))
        val itemId = taro.store.addItem(NewItem(listId, "牛乳", 1.0, "本", memo = null, Visibility.SHARED))
        taro.settle()
        // チェックはリスト名を端末のキャッシュから写すので、リストも届くまで待つ（アプリではリスト詳細を開いている）
        hanako.store.observeLists().awaitFirst { lists -> lists.any { it.id == listId } }
        hanako.store.observeOpenItems().awaitFirst { items -> items.any { it.id == itemId } }
        return Family(taro, hanako, householdId, listId, itemId)
    }

    @Test
    fun 同じ項目を同時にチェックしても有効な購入の記録は1件で後の端末に先にチェックした人と時刻が返る(): Unit = runBlocking {
        val f = family()
        val rejected = Collections.synchronizedList(mutableListOf<Pair<String, RejectedCheck>>())
        listOf(f.taro, f.hanako).forEach { c -> collect(c.store.observeRejectedChecks()) { rejected += c.uid to it } }
        val now = Instant.now()

        val results = listOf(f.taro, f.hanako).map { c -> async(Dispatchers.IO) { c.uid to c.store.completeItem(f.itemId, now) } }.awaitAll()
        settle(f.taro, f.hanako)

        val item = f.taro.serverItem(f)
        val purchase = f.taro.serverPurchase(f)
        assertThat(item.status).isEqualTo(ItemStatus.COMPLETED)
        assertThat(purchase.getBoolean(F.ACTIVE)).isTrue()
        assertThat(purchase.getString(F.PURCHASED_BY)).isEqualTo(item.completedBy!!.value)
        // 後からチェックした側には、書き込む前に分かれば AlreadyCompleted が、書き込みが拒否されれば通知が返る
        val loser = listOf(f.taro, f.hanako).single { it.uid != item.completedBy!!.value }
        val returned = results.single { it.first == loser.uid }.second
        val (by, at) = if (returned is CompleteResult.AlreadyCompleted) {
            returned.by to returned.at
        } else {
            assertThat(returned).isEqualTo(CompleteResult.Completed)
            val notice = awaitValue { rejected.firstOrNull { it.first == loser.uid }?.second }
            check(notice is RejectedCheck.AlreadyCompleted) { "AlreadyCompleted ではない: $notice" }
            notice.by to notice.at
        }
        assertThat(by).isEqualTo(item.completedBy)
        assertThat(at).isEqualTo(item.completedAt)
    }

    @Test
    fun チェックのあとにもう一方がUndoしてから最初の端末もUndoするとAlreadyOpenになる(): Unit = runBlocking {
        val f = family()
        f.taro.store.completeItem(f.itemId, Instant.now())
        f.taro.settle()
        f.hanako.awaitItem(f) { it.status == ItemStatus.COMPLETED }

        assertThat(f.hanako.store.reopenItem(f.itemId)).isEqualTo(ReopenResult.Reopened)
        f.hanako.settle()
        f.taro.awaitItem(f) { it.status == ItemStatus.OPEN }

        assertThat(f.taro.store.reopenItem(f.itemId)).isEqualTo(ReopenResult.AlreadyOpen)
        assertThat(f.taro.serverPurchase(f).getBoolean(F.ACTIVE)).isFalse()
    }

    @Test
    fun Undoと再チェックを同時に行っても項目の状態と購入の記録の有効がそろう(): Unit = runBlocking {
        val f = family()
        repeat(ROUNDS) { round ->
            // 交互に、チェック済みからの「Undo と、Undo してからの再チェック」と、未購入からの「チェックと Undo と、チェック」を競わせる
            val completed = round % 2 == 0
            if (completed) {
                f.taro.store.completeItem(f.itemId, Instant.now())
                f.taro.settle()
            }
            val status = if (completed) ItemStatus.COMPLETED else ItemStatus.OPEN
            f.taro.awaitItem(f) { it.status == status }
            f.hanako.awaitItem(f) { it.status == status }

            listOf(
                async(Dispatchers.IO) {
                    if (!completed) f.taro.store.completeItem(f.itemId, Instant.now())
                    f.taro.store.reopenItem(f.itemId)
                },
                async(Dispatchers.IO) {
                    if (completed) f.hanako.store.reopenItem(f.itemId)
                    f.hanako.store.completeItem(f.itemId, Instant.now())
                },
            ).awaitAll()
            settle(f.taro, f.hanako)

            val item = f.taro.serverItem(f)
            val purchase = f.taro.serverPurchase(f, mustExist = false)
            val active = purchase.exists() && purchase.getBoolean(F.ACTIVE) == true
            assertThat(active).isEqualTo(item.status == ItemStatus.COMPLETED)

            // 次の回のために未購入に戻す
            if (item.status == ItemStatus.COMPLETED) {
                f.taro.awaitItem(f) { it.status == ItemStatus.COMPLETED }
                f.taro.store.reopenItem(f.itemId)
                f.taro.settle()
            }
            f.hanako.awaitItem(f) { it.status == ItemStatus.OPEN }
        }
    }

    /**
     * リストの削除と項目の追加を同時に行う。どちらが先にサーバーに届くかを決めるため、後に届く側をオフラインにしておき、
     * 先の側の書き込みが届いてからつなぐ
     */
    private suspend fun deleteListWhileAdding(deleteFirst: Boolean) {
        val f = family()
        // 未購入が0件のリスト（買い終わったリスト）だけを削除できるので、先にチェックしておく
        f.taro.store.completeItem(f.itemId, Instant.now())
        f.taro.settle()
        f.hanako.awaitItem(f) { it.status == ItemStatus.COMPLETED }

        val (first, second) = if (deleteFirst) f.taro to f.hanako else f.hanako to f.taro
        second.offline()
        val deleted = f.taro.store.deleteList(f.listId)
        val added = f.hanako.store.addItem(NewItem(f.listId, "卵", 1.0, "パック", memo = null, Visibility.SHARED))
        first.settle()
        second.online()
        settle(f.taro, f.hanako)

        assertThat(deleted).isEqualTo(DeleteListResult.Deleted)
        val item = checkNotNull(Paths.items(f.taro.db, f.householdId).document(added.value).get(Source.SERVER).await().toItem())
        assertThat(item.status).isEqualTo(ItemStatus.OPEN)
        for (client in listOf(f.taro, f.hanako)) {
            val list = client.store.observeLists().combine(client.store.observeOpenItems()) { lists, open ->
                lists.find { it.id == f.listId }?.let { it to open.count { item -> item.listId == f.listId } }
            }.awaitFirst { it != null && it.first.status == ListStatus.DELETED && it.second > 0 }!!
            assertThat(ListDisplay.isShown(list.first, list.second)).isTrue()
        }
    }

    @Test
    fun リストの削除が先に届いても同時に追加した項目は残りリストは一覧に表示されたまま(): Unit = runBlocking {
        deleteListWhileAdding(deleteFirst = true)
    }

    @Test
    fun 項目の追加が先に届いてもリストの削除と同時ならリストは一覧に表示されたまま(): Unit = runBlocking {
        deleteListWhileAdding(deleteFirst = false)
    }

    @Test
    fun オフラインのチェックはアプリを終了しても接続後に確定しチェックした時刻はオフライン中の時刻(): Unit = runBlocking {
        val f = family()
        f.taro.offline()
        val checkedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        assertThat(f.taro.store.completeItem(f.itemId, checkedAt)).isEqualTo(CompleteResult.Completed)

        // しばらくオフラインのままにしてから、アプリのプロセスが終わって起動し直したのと同じように Firestore を作り直す
        // （起動し直すと、つながった状態になる）
        delay(OFFLINE_FOR_MILLIS)
        f.taro.restart()
        f.taro.settle()

        val item = f.taro.serverItem(f)
        val purchase = f.taro.serverPurchase(f)
        assertThat(item.status).isEqualTo(ItemStatus.COMPLETED)
        assertThat(purchase.getBoolean(F.ACTIVE)).isTrue()
        assertThat(purchase.getTimestamp(F.CHECKED_AT)!!.toDate().toInstant()).isEqualTo(checkedAt)
        // 購入日時はサーバーに届いた時刻
        assertThat(purchase.getTimestamp(F.PURCHASED_AT)!!.toDate().toInstant()).isAtLeast(checkedAt.plusMillis(OFFLINE_FOR_MILLIS))
    }

    @Test
    fun オフラインの間にもう一方がチェックすると接続後に有効な購入の記録は1件でオフラインだった側にAlreadyCompletedが返る(): Unit = runBlocking {
        val f = family()
        val rejected = Collections.synchronizedList(mutableListOf<RejectedCheck>())
        collect(f.taro.store.observeRejectedChecks()) { rejected += it }

        f.taro.offline()
        f.taro.store.completeItem(f.itemId, Instant.now())
        assertThat(f.hanako.store.completeItem(f.itemId, Instant.now())).isEqualTo(CompleteResult.Completed)
        f.hanako.settle()
        f.taro.online()
        f.taro.settle()

        val item = f.taro.serverItem(f)
        val purchase = f.taro.serverPurchase(f)
        assertThat(item.completedBy).isEqualTo(UserId(f.hanako.uid))
        assertThat(purchase.getString(F.PURCHASED_BY)).isEqualTo(f.hanako.uid)
        assertThat(purchase.getBoolean(F.ACTIVE)).isTrue()
        val notice = awaitValue { rejected.firstOrNull() }
        assertThat(notice).isEqualTo(RejectedCheck.AlreadyCompleted("牛乳", UserId(f.hanako.uid), item.completedAt!!))
    }

    @Test
    fun オフラインでチェックしてオフラインのままUndoすると接続後に有効な購入の記録は残らない(): Unit = runBlocking {
        val f = family()
        f.taro.offline()
        f.taro.store.completeItem(f.itemId, Instant.now())
        assertThat(f.taro.store.reopenItem(f.itemId)).isEqualTo(ReopenResult.Reopened)
        f.taro.online()
        f.taro.settle()

        assertThat(f.taro.serverItem(f).status).isEqualTo(ItemStatus.OPEN)
        // チェックと Undo の2つの書き込みが順に送られる（docs/design/sync.md「Undo」）。購入の記録は無効で残る
        val purchase = f.taro.serverPurchase(f, mustExist = false)
        assertThat(purchase.exists() && purchase.getBoolean(F.ACTIVE) == true).isFalse()
    }

    @Test
    fun チェック済みの項目をオフラインでUndoすると接続後に購入の記録が無効になる(): Unit = runBlocking {
        val f = family()
        f.taro.store.completeItem(f.itemId, Instant.now())
        f.taro.settle()

        f.taro.offline()
        assertThat(f.taro.store.reopenItem(f.itemId)).isEqualTo(ReopenResult.Reopened)
        f.taro.online()
        f.taro.settle()

        assertThat(f.taro.serverItem(f).status).isEqualTo(ItemStatus.OPEN)
        assertThat(f.taro.serverPurchase(f).getBoolean(F.ACTIVE)).isFalse()
        f.hanako.awaitItem(f) { it.status == ItemStatus.OPEN }
    }

    private fun collect(flow: Flow<RejectedCheck>, onEach: (RejectedCheck) -> Unit) {
        // 通知は購読している間にしか届かないので、書き込む前に購読を始める
        collectors.launch(start = CoroutineStart.UNDISPATCHED) { flow.collect(onEach) }
    }

    private suspend fun EmulatorClient.settle() {
        db.waitForPendingWrites().await()
    }

    private suspend fun settle(vararg clients: EmulatorClient) = clients.forEach { it.settle() }

    private suspend fun EmulatorClient.serverItem(f: Family): Item =
        checkNotNull(Paths.items(db, f.householdId).document(f.itemId.value).get(Source.SERVER).await().toItem())

    private suspend fun EmulatorClient.serverPurchase(f: Family, mustExist: Boolean = true): DocumentSnapshot =
        Paths.purchases(db, f.householdId).document(f.itemId.value).get(Source.SERVER).await()
            .also { if (mustExist) check(it.exists()) { "購入の記録がない" } }

    /** この端末のリスト詳細に見えている項目が [predicate] を満たすまで待つ */
    private suspend fun EmulatorClient.awaitItem(f: Family, predicate: (Item) -> Boolean) {
        store.observeListItems(f.listId, Instant.EPOCH).awaitFirst { items -> items.any { it.id == f.itemId && predicate(it) } }
    }

    private suspend fun <T> Flow<T>.awaitFirst(predicate: (T) -> Boolean): T = withTimeout(TIMEOUT_MILLIS) { first(predicate) }

    private suspend fun <T : Any> awaitValue(value: () -> T?): T = withTimeout(TIMEOUT_MILLIS) {
        var found = value()
        while (found == null) {
            delay(50)
            found = value()
        }
        found
    }

    private companion object {
        const val TIMEOUT_MILLIS = 15_000L
        const val OFFLINE_FOR_MILLIS = 2_000L
        const val ROUNDS = 6
    }
}
