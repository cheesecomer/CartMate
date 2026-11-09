package com.cheesecomer.cartmate.data.firestore

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cheesecomer.cartmate.domain.model.HouseholdId
import com.cheesecomer.cartmate.domain.model.ItemStatus
import com.cheesecomer.cartmate.domain.model.NewItem
import com.cheesecomer.cartmate.domain.model.NewList
import com.cheesecomer.cartmate.domain.model.Visibility
import com.cheesecomer.cartmate.domain.repository.JoinResult
import com.cheesecomer.cartmate.testing.EmulatorTest
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * リスト一覧を表示している間に読んでおく、家族の未購入と最近チェックした項目（observeRecentItems）。
 * リスト詳細は、項目の購読が届くまでこれを表示する（フィードバック #021）
 */
@EmulatorTest
@RunWith(AndroidJUnit4::class)
class RecentItemsTest {

    private val clients = mutableListOf<EmulatorClient>()

    @Before
    fun onlyWhenRequested() {
        EmulatorClient.assumeRequested()
    }

    @After
    fun tearDown(): Unit = runBlocking {
        clients.forEach { runCatching { it.close() } }
    }

    /** taro の家族に hanako が参加している */
    private suspend fun family(): Triple<EmulatorClient, EmulatorClient, HouseholdId> {
        val taro = EmulatorClient.signIn("taro").also(clients::add)
        val hanako = EmulatorClient.signIn("hanako").also(clients::add)
        val householdId = taro.households.createHousehold(taro.account)
        taro.awaitHousehold(householdId)
        val invite = taro.households.createInvite("太郎")
        assertThat(hanako.households.join(invite, hanako.account)).isEqualTo(JoinResult.Joined)
        hanako.awaitHousehold(householdId)
        return Triple(taro, hanako, householdId)
    }

    @Test
    fun すべてのリストの未購入とチェックした項目が届き_ほかのメンバーのプライベートな項目は含まない(): Unit = runBlocking {
        val (taro, hanako, _) = family()
        val supermarket = taro.store.createList(NewList("スーパー", memo = null, Visibility.SHARED))
        val pharmacy = taro.store.createList(NewList("薬局", memo = null, Visibility.SHARED))
        val milk = taro.store.addItem(NewItem(supermarket, "牛乳", 1.0, "本", memo = null, Visibility.SHARED)).id
        taro.store.addItem(NewItem(pharmacy, "目薬", 1.0, "個", memo = null, Visibility.SHARED))
        taro.store.addItem(NewItem(pharmacy, "ばんそうこう", 1.0, "箱", memo = null, Visibility.PRIVATE))
        taro.store.completeItem(milk, checkedAt = Instant.now())
        hanako.store.addItem(NewItem(supermarket, "プレゼント", 1.0, "個", memo = null, Visibility.PRIVATE))
        taro.db.waitForPendingWrites().await()
        hanako.db.waitForPendingWrites().await()

        val items = taro.store.observeRecentItems().awaitFirst { items -> items.any { it.inputName == "牛乳" && it.status == ItemStatus.COMPLETED } }
        assertThat(items.map { it.inputName }).containsExactly("牛乳", "目薬", "ばんそうこう")
        // hanako には、自分のプライベートな項目が届く
        val hanakoItems = hanako.store.observeRecentItems().awaitFirst { items -> items.any { it.inputName == "プレゼント" } }
        assertThat(hanakoItems.map { it.inputName }).containsExactly("牛乳", "目薬", "プレゼント")
    }

    private suspend fun <T> Flow<T>.awaitFirst(predicate: (T) -> Boolean): T = withTimeout(15_000) { first(predicate) }
}
