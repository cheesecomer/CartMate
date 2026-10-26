package com.cheesecomer.cartmate.ui.lists

import app.cash.turbine.test
import com.cheesecomer.cartmate.data.memory.InMemoryStore
import com.cheesecomer.cartmate.data.memory.FixedAccountRepository
import com.cheesecomer.cartmate.domain.list.ListProgress
import com.cheesecomer.cartmate.domain.model.UserId
import com.cheesecomer.cartmate.domain.model.Visibility
import com.cheesecomer.cartmate.testing.MainDispatcherExtension
import com.cheesecomer.cartmate.testing.MutableClock
import com.cheesecomer.cartmate.testing.awaitUntil
import com.cheesecomer.cartmate.testing.createList
import com.cheesecomer.cartmate.testing.newItem
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration
import kotlinx.coroutines.flow.flowOf

class ListsViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val clock = MutableClock()
    private var user = UserId("taro")
    private val store = InMemoryStore(clock) { user }
    private val viewModel = ListsViewModel(store, store, store, clock, FixedAccountRepository(null), { flowOf(true) })

    @Test
    fun `リストを作成すると、作成順に名前・備考・公開範囲・件数とともに表示される`() = runTest {
        // 状態が届く回数や順番は合成するデータの数で変わるので、期待する状態が届くまで待つ
        viewModel.uiState.test {
            assertThat(awaitUntil { !it.loading }.lists).isEmpty()

            viewModel.createList(" スーパー ", " 週末の買い出し ", Visibility.SHARED)
            val supermarket = awaitUntil { it.lists.isNotEmpty() }.lists.single()
            assertThat(supermarket.name).isEqualTo("スーパー")
            assertThat(supermarket.memo).isEqualTo("週末の買い出し")

            viewModel.createList("薬局", "  ", Visibility.PRIVATE)
            awaitUntil { it.lists.size == 2 }
            store.addItem(newItem(supermarket.id, "牛乳"))

            val lists = awaitUntil { state -> state.lists.any { it.progress.openCount == 1 } }.lists
            assertThat(lists.map { it.name }).containsExactly("スーパー", "薬局").inOrder()
            assertThat(lists.map { it.visibility }).containsExactly(Visibility.SHARED, Visibility.PRIVATE).inOrder()
            assertThat(lists[1].memo).isNull()
            assertThat(lists.map { it.progress.openCount }).containsExactly(1, 0).inOrder()
        }
    }

    @Test
    fun `空白だけの名前ではリストを作成しない`() = runTest {
        viewModel.createList("   ", "", Visibility.SHARED)
        viewModel.uiState.test {
            assertThat(awaitUntil { !it.loading }.lists).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `のこり・登録なし・買い終わったの状態と、直近24時間の完了の件数を表示する`() = runTest {
        store.createList("新しい")
        val doneList = store.createList("買い終わり")
        val doneItem = store.addItem(newItem(doneList, "牛乳"))
        store.completeItem(doneItem, clock.now)
        val openList = store.createList("のこりあり")
        store.addItem(newItem(openList, "卵"))
        val checked = store.addItem(newItem(openList, "パン"))
        store.completeItem(checked, clock.now)

        viewModel.uiState.test {
            val lists = awaitUntil { !it.loading }.lists.associate { it.name to it.progress }
            assertThat(lists.getValue("新しい")).isEqualTo(ListProgress(0, 0, hasPurchases = false))
            assertThat(lists.getValue("買い終わり")).isEqualTo(ListProgress(0, 1, hasPurchases = true))
            assertThat(lists.getValue("のこりあり")).isEqualTo(ListProgress(1, 1, hasPurchases = true))
        }
    }

    @Test
    fun `24時間より前の完了は件数に数えないが、買い終わったリストのまま`() = runTest {
        val listId = store.createList("スーパー")
        val itemId = store.addItem(newItem(listId, "牛乳"))
        store.completeItem(itemId, clock.now)
        clock.advance(Duration.ofHours(25))
        val viewModel = ListsViewModel(store, store, store, clock, FixedAccountRepository(null), { flowOf(true) })

        viewModel.uiState.test {
            val progress = awaitUntil { !it.loading }.lists.single().progress
            assertThat(progress).isEqualTo(ListProgress(0, 0, hasPurchases = true))
            assertThat(progress.status).isEqualTo(ListProgress.Status.DONE)
        }
    }

    @Test
    fun `購入を取り消して記録がなくなったリストは、登録なしに戻る`() = runTest {
        val listId = store.createList("スーパー")
        val itemId = store.addItem(newItem(listId, "牛乳"))
        store.completeItem(itemId, clock.now)
        store.reopenItem(itemId)
        store.deleteItem(itemId)

        viewModel.uiState.test {
            assertThat(awaitUntil { !it.loading }.lists.single().progress.status)
                .isEqualTo(ListProgress.Status.NOT_REGISTERED)
        }
    }

    @Test
    fun `他のメンバーのプライベートな項目は件数に数えない`() = runTest {
        val listId = store.createList("スーパー")
        store.addItem(newItem(listId, "牛乳"))
        store.addItem(newItem(listId, "プレゼント", visibility = Visibility.PRIVATE))
        user = UserId("hanako")

        viewModel.uiState.test {
            assertThat(awaitUntil { !it.loading }.lists.single().progress.openCount).isEqualTo(1)
        }
    }

    @Test
    fun `リストを動かすと並び順が変わる`() = runTest {
        val a = store.createList("A")
        val b = store.createList("B")
        viewModel.uiState.test {
            awaitUntil { it.lists.size == 2 }

            viewModel.moveList(b, previous = null, next = a)

            awaitUntil { state -> state.lists.map { it.name } == listOf("B", "A") }
        }
    }
}
