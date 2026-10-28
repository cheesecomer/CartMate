package com.cheesecomer.cartmate.ui.products

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.cheesecomer.cartmate.data.memory.InMemoryStore
import com.cheesecomer.cartmate.domain.model.Member
import com.cheesecomer.cartmate.domain.model.MemberRole
import com.cheesecomer.cartmate.domain.model.UserId
import com.cheesecomer.cartmate.domain.product.NormalizationDictionary
import com.cheesecomer.cartmate.testing.MainDispatcherExtension
import com.cheesecomer.cartmate.testing.MutableClock
import com.cheesecomer.cartmate.testing.awaitUntil
import com.cheesecomer.cartmate.testing.createList
import com.cheesecomer.cartmate.testing.newItem
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration
import java.time.Instant

class PurchaseHistoryViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val clock = MutableClock()
    /** store を操作している人。ほかのメンバーの購入を再現するときに切り替える */
    private var actor = UserId("taro")
    private val store = InMemoryStore(clock) { actor }
    private val listId = runBlocking { store.createList("スーパー") }

    private suspend fun buy(name: String) {
        val id = store.addItem(newItem(listId, name, quantity = 1.0, unit = "本"))
        store.completeItem(id, clock.instant())
        clock.advance(Duration.ofDays(1))
    }

    private suspend fun viewModel(name: String): PurchaseHistoryViewModel {
        val id = store.observeProducts().first().first { it.displayName == name }.id
        return PurchaseHistoryViewModel(
            savedStateHandle = SavedStateHandle(mapOf("productId" to id.value)),
            productRepository = store,
            purchaseRepository = store,
            itemRepository = store,
            currentUser = { actor },
            memberDirectory = { flowOf(listOf(Member(UserId("taro"), MemberRole.OWNER, "山田 太郎", null, Instant.EPOCH))) },
            clock = clock,
            dictionaryProvider = { flowOf(NormalizationDictionary.EMPTY) },
        )
    }

    @Test
    fun `購入の記録を新しい順に出し、予測に使わないにした人と日を添える`() = runTest {
        buy("牛乳")
        buy("牛乳")
        buy("豆乳")
        val viewModel = viewModel("牛乳")
        viewModel.state.test {
            val rows = awaitUntil { it.rows.size == 2 }.rows
            assertThat(rows.first().date).isGreaterThan(rows.last().date)
            assertThat(rows.first().checkerName).isEqualTo("山田 太郎")

            viewModel.setExcluded(rows.last().purchase.itemId, true)
            val excluded = awaitUntil { state -> state.rows.any { it.purchase.excludedFromPrediction } }.rows.last()
            assertThat(excluded.excludedByName).isEqualTo("山田 太郎")
            assertThat(excluded.excludedDate).isNotNull()

            viewModel.setExcluded(excluded.purchase.itemId, false)
            val restored = awaitUntil { state -> state.rows.none { it.purchase.excludedFromPrediction } }.rows.last()
            assertThat(restored.excludedByName).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `自分がチェックした購入だけを削除でき、削除すると項目ごと履歴から消える`() = runTest {
        buy("ケーキ")
        actor = UserId("hanako")
        buy("ケーキ")
        actor = UserId("taro")
        val viewModel = viewModel("ケーキ")
        viewModel.state.test {
            val rows = awaitUntil { it.rows.size == 2 }.rows
            val mine = rows.single { it.purchase.purchasedBy == UserId("taro") }
            assertThat(mine.deletable).isTrue()
            assertThat(rows.single { it.purchase.purchasedBy == UserId("hanako") }.deletable).isFalse()

            viewModel.delete(mine.purchase.itemId)
            assertThat(awaitUntil { it.rows.size == 1 }.rows.single().purchase.purchasedBy).isEqualTo(UserId("hanako"))
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(store.observeListItems(listId, Instant.EPOCH).first().map { it.completedBy }).containsExactly(UserId("hanako"))
    }

    @Test
    fun `記録があった状態から最後の購入を削除すると、0件になったことを商品名とともに知らせる`() = runTest {
        buy("ケーキ")
        val viewModel = viewModel("ケーキ")
        viewModel.state.test {
            val row = awaitUntil { it.rows.size == 1 }.also { assertThat(it.emptied).isFalse() }.rows.single()

            viewModel.delete(row.purchase.itemId)
            val emptied = awaitUntil { it.emptied }
            assertThat(emptied.productName).isEqualTo("ケーキ")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `最初から記録がない購入履歴は、0件になったとはみなさない`() = runTest {
        buy("ケーキ")
        val id = store.observePurchases(Instant.EPOCH).first().single().itemId
        val viewModel = viewModel("ケーキ")
        store.deletePurchase(id)
        viewModel.state.test {
            val state = awaitUntil { !it.loading }
            assertThat(state.rows).isEmpty()
            assertThat(state.emptied).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
