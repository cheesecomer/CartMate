package com.cheesecomer.cartmate.data.firestore

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cheesecomer.cartmate.domain.model.HouseholdId
import com.cheesecomer.cartmate.domain.model.ListId
import com.cheesecomer.cartmate.domain.model.NewItem
import com.cheesecomer.cartmate.domain.model.NewList
import com.cheesecomer.cartmate.domain.model.Product
import com.cheesecomer.cartmate.domain.model.ProductId
import com.cheesecomer.cartmate.domain.model.Visibility
import com.cheesecomer.cartmate.domain.product.ProductGraph
import com.cheesecomer.cartmate.domain.product.ProductHiding
import com.cheesecomer.cartmate.domain.product.ProductUsage
import com.cheesecomer.cartmate.domain.product.ProductMerge
import com.cheesecomer.cartmate.domain.repository.JoinResult
import com.cheesecomer.cartmate.testing.EmulatorTest
import com.google.common.truth.Truth.assertThat
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/**
 * 商品の統合と解除の同時実行（docs/design/sync.md「商品の統合の競合」）。バッチの書き込みは原子的でも、読み取った統合関係が
 * 書き込む時点でも正しいとは限らない。古い統合関係を前提にした書き込みがルールで拒否され、整合性が保たれることを確かめる。
 * 届く順番を決めるため、後に届く側をオフラインにして書き込み、先の側が届いてからつなぐ
 */
@EmulatorTest
@RunWith(AndroidJUnit4::class)
class ProductMergeConcurrencyTest {

    private val clients = mutableListOf<EmulatorClient>()
    private val collectors = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Before
    fun onlyWhenRequested() {
        EmulatorClient.assumeRequested()
        // 確定していない統合の記録（FirestoreProductRepository）は端末に1つなので、前のテストの分を消しておく
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("product_merge_pending", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun tearDown(): Unit = runBlocking {
        collectors.cancel()
        clients.forEach { runCatching { it.close() } }
    }

    private class Family(val taro: EmulatorClient, val hanako: EmulatorClient, val householdId: HouseholdId)

    /** taro の家族に hanako が参加し、[names] の商品（共有の項目）が両方の端末に届いている */
    private suspend fun family(vararg names: String): Family {
        val taro = EmulatorClient.signIn("taro").also(clients::add)
        val hanako = EmulatorClient.signIn("hanako").also(clients::add)
        val householdId = taro.households.createHousehold(taro.account)
        taro.awaitHousehold(householdId)
        val invite = taro.households.createInvite("太郎")
        assertThat(hanako.households.join(invite, hanako.account)).isEqualTo(JoinResult.Joined)
        hanako.awaitHousehold(householdId)
        val listId: ListId = taro.store.createList(NewList("スーパー", memo = null, Visibility.SHARED))
        names.forEach { taro.store.addItem(NewItem(listId, it, 1.0, "個", memo = null, Visibility.SHARED)) }
        taro.db.waitForPendingWrites().await()
        for (client in listOf(taro, hanako)) client.awaitProducts { it.size == names.size }
        return Family(taro, hanako, householdId)
    }

    private suspend fun EmulatorClient.awaitProducts(predicate: (List<Product>) -> Boolean): List<Product> =
        withTimeout(TIMEOUT_MILLIS) { products.observeProducts().first(predicate) }

    private suspend fun EmulatorClient.id(name: String): ProductId = awaitProducts { true }.first { it.displayName == name }.id

    /** この端末の画面に見えている統合関係から、統合の変更を作って書き込む（オフラインなら書き込みのキューに積まれる） */
    private suspend fun EmulatorClient.merge(target: String, vararg selected: String) {
        val all = awaitProducts { true }
        products.applyMergeChanges(checkNotNull(ProductMerge.mergeChanges(id(target), selected.map { id(it) }, all)))
    }

    private suspend fun EmulatorClient.unmerge(name: String) {
        products.applyMergeChanges(checkNotNull(ProductMerge.unmergeChanges(id(name), awaitProducts { true })))
    }

    /** サーバーの統合関係（商品の名前 → 統合先の名前） */
    private suspend fun Family.serverRelations(): Map<String, String?> {
        val products = Paths.products(taro.db, householdId).get(Source.SERVER).await().documents.mapNotNull { it.toProduct() }
        val names = products.associate { it.id to it.displayName }
        return products.associate { it.displayName to it.mergedInto?.let(names::get) }
    }

    /** 拒否の通知の件数。書き込む前に購読を始める */
    private fun EmulatorClient.countRejections(): AtomicInteger {
        val count = AtomicInteger()
        collectors.launch(start = CoroutineStart.UNDISPATCHED) { products.observeRejectedMergeChanges().collect { count.incrementAndGet() } }
        return count
    }

    private suspend fun awaitCount(count: AtomicInteger, expected: Int) {
        withTimeout(TIMEOUT_MILLIS) { while (count.get() < expected) delay(50) }
    }

    /** 「豚バラ」→「豚ばら肉」→「豚バラ肉」の多段の統合を作る（以前の画面で作られたものと同じ形） */
    private suspend fun Family.chain() {
        // 「商品をまとめる」は1段にまとめるので、以前の画面と同じく1件ずつ統合して作る
        taro.products.applyMergeChanges(listOf(ProductMerge.Change.Merge(taro.id("豚バラ"), taro.id("豚ばら肉"))))
        taro.products.applyMergeChanges(listOf(ProductMerge.Change.Merge(taro.id("豚ばら肉"), taro.id("豚バラ肉"))))
        taro.db.waitForPendingWrites().await()
        hanako.awaitProducts { products -> ProductGraph(products).resolve(products.first { it.displayName == "豚バラ" }.id) != products.first { it.displayName == "豚バラ" }.id }
        assertThat(serverRelations()).containsExactly("豚バラ肉", null, "豚ばら肉", "豚バラ肉", "豚バラ", "豚ばら肉")
    }

    @Test
    fun 下の商品が先に解除されていると中間の商品の解除は拒否され知らされる(): Unit = runBlocking {
        val f = family("豚バラ肉", "豚ばら肉", "豚バラ")
        f.chain()
        val rejections = f.taro.countRejections()

        // taro は古い統合関係（豚バラ → 豚ばら肉）を見たまま、「豚ばら肉」を解除する
        f.taro.offline()
        f.taro.unmerge("豚ばら肉")
        // その間に hanako が「豚バラ」を解除した
        f.hanako.unmerge("豚バラ")
        f.hanako.db.waitForPendingWrites().await()
        f.taro.online()
        f.taro.db.waitForPendingWrites().await()

        // taro の書き込みはまるごと拒否され、「豚バラ」をまとめ直すことはない
        assertThat(f.serverRelations()).containsExactly("豚バラ肉", null, "豚ばら肉", "豚バラ肉", "豚バラ", null)
        awaitCount(rejections, 1)
    }

    @Test
    fun 中間の商品の解除が先に届いても下の商品の解除は反映される(): Unit = runBlocking {
        val f = family("豚バラ肉", "豚ばら肉", "豚バラ")
        f.chain()
        val rejections = f.hanako.countRejections()

        f.hanako.offline()
        f.hanako.unmerge("豚バラ")
        f.taro.unmerge("豚ばら肉")
        f.taro.db.waitForPendingWrites().await()
        f.hanako.online()
        f.hanako.db.waitForPendingWrites().await()

        // 2人が解除した2件とも外れる
        assertThat(f.serverRelations()).containsExactly("豚バラ肉", null, "豚ばら肉", null, "豚バラ", null)
        delay(1_000)
        assertThat(rejections.get()).isEqualTo(0)
    }

    @Test
    fun 逆向きに同時にまとめても後から届いた方は拒否され循環しない(): Unit = runBlocking {
        val f = family("牛乳", "ぎゅうにゅう")
        val rejections = f.hanako.countRejections()

        f.hanako.offline()
        f.hanako.merge("ぎゅうにゅう", "牛乳")
        f.taro.merge("牛乳", "ぎゅうにゅう")
        f.taro.db.waitForPendingWrites().await()
        f.hanako.online()
        f.hanako.db.waitForPendingWrites().await()

        assertThat(f.serverRelations()).containsExactly("牛乳", null, "ぎゅうにゅう", "牛乳")
        awaitCount(rejections, 1)
    }

    @Test
    fun まとめる商品の下の商品が先に解除されていると一括統合はまるごと拒否される(): Unit = runBlocking {
        val f = family("豚バラ肉", "豚ばら肉", "豚バラ", "ぶたばら")
        f.taro.merge("豚ばら肉", "豚バラ")
        f.taro.db.waitForPendingWrites().await()
        f.hanako.awaitProducts { products -> products.any { it.displayName == "豚バラ" && it.mergedInto != null } }
        val rejections = f.hanako.countRejections()

        // hanako は「豚ばら肉」（豚バラをまとめている）と「ぶたばら」を「豚バラ肉」にまとめる
        f.hanako.offline()
        f.hanako.merge("豚バラ肉", "豚ばら肉", "ぶたばら")
        // その間に taro が「豚バラ」を解除した
        f.taro.unmerge("豚バラ")
        f.taro.db.waitForPendingWrites().await()
        f.hanako.online()
        f.hanako.db.waitForPendingWrites().await()

        // 一部だけ（「ぶたばら」だけ）がまとまることはない
        assertThat(f.serverRelations()).containsExactly("豚バラ肉", null, "豚ばら肉", null, "豚バラ", null, "ぶたばら", null)
        awaitCount(rejections, 1)
    }

    @Test
    fun アプリを終了している間に拒否された統合は次の起動で知らされる(): Unit = runBlocking {
        val f = family("牛乳", "ぎゅうにゅう")

        f.hanako.offline()
        f.hanako.merge("ぎゅうにゅう", "牛乳")
        // アプリのプロセスが終わったのと同じように、Firestore とリポジトリを作り直す（書き込みのキューは端末に残る）
        f.hanako.restart(offline = true)
        f.taro.merge("牛乳", "ぎゅうにゅう")
        f.taro.db.waitForPendingWrites().await()
        val rejections = f.hanako.countRejections()
        f.hanako.online()
        f.hanako.products.verifyPendingMergeChanges()

        assertThat(f.serverRelations()).containsExactly("牛乳", null, "ぎゅうにゅう", "牛乳")
        awaitCount(rejections, 1)
    }

    @Test
    fun 誤字のまま完了した1件目の記録は名前の訂正で正しい名前の商品にまとまり家族にも届く(): Unit = runBlocking {
        val f = family("牛入")
        val typo = f.taro.id("牛入")
        val changes = checkNotNull(ProductMerge.renameChanges(typo, "牛乳", f.taro.awaitProducts { true }))
        f.taro.products.applyMergeChanges(changes)
        f.taro.db.waitForPendingWrites().await()

        assertThat(f.serverRelations()).containsExactly("牛乳", null, "牛入", "牛乳")
        f.hanako.awaitProducts { products -> products.any { it.displayName == "牛入" && it.mergedInto != null } }
    }

    @Test
    fun 非表示は家族に届き同じ名前をまた追加すると書き込みなしでまた表示される(): Unit = runBlocking {
        val f = family("テスト", "牛乳")
        val test = f.taro.id("テスト")
        f.taro.products.applyMergeChanges(checkNotNull(ProductMerge.hideChanges(test, f.taro.awaitProducts { true })))
        f.taro.db.waitForPendingWrites().await()

        // hanako の端末でも非表示になる
        val products = f.hanako.awaitProducts { products -> products.any { it.id == test && it.hiddenAt != null } }
        val usage = ProductUsage.of(f.hanako.store.observeOpenItems().first(), emptyList())
        assertThat(ProductHiding.hiddenRoots(products, usage)).containsExactly(test)

        // hanako が同じ名前をまた追加すると、また表示される（非表示の時刻は残ったまま）
        val listId = f.hanako.store.observeLists().first().first().id
        f.hanako.store.addItem(NewItem(listId, "テスト", 1.0, "個", memo = null, Visibility.SHARED))
        f.hanako.db.waitForPendingWrites().await()
        // 追加した時刻は、サーバーに届くまでは端末の時計の見積もり。届いたあとはサーバーの時刻で比べる
        val all = f.hanako.awaitProducts { true }
        withTimeout(TIMEOUT_MILLIS) {
            f.hanako.store.observeOpenItems().first { items ->
                items.count { it.productId == test } == 2 && ProductHiding.hiddenRoots(all, ProductUsage.of(items, emptyList())).isEmpty()
            }
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 15_000L
    }
}
