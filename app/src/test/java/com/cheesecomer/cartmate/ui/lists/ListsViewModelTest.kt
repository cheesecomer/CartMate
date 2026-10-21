package com.cheesecomer.cartmate.ui.lists

import app.cash.turbine.test
import com.cheesecomer.cartmate.data.memory.InMemoryStore
import com.cheesecomer.cartmate.domain.model.NewItem
import com.cheesecomer.cartmate.domain.model.UserId
import com.cheesecomer.cartmate.testing.MainDispatcherExtension
import com.cheesecomer.cartmate.testing.MutableClock
import com.cheesecomer.cartmate.testing.awaitUntil
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration

class ListsViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val clock = MutableClock()
    private val store = InMemoryStore(clock) { UserId("taro") }
    private val viewModel = ListsViewModel(store, store, store, clock)

    @Test
    fun `リストを作成すると、作成順に未購入件数とともに表示される`() = runTest {
        // 状態が届く回数や順番は合成するデータの数で変わるので、期待する状態が届くまで待つ
        viewModel.uiState.test {
            assertThat(awaitUntil { !it.loading }.lists).isEmpty()

            viewModel.createList(" スーパー ")
            val supermarket = awaitUntil { it.lists.isNotEmpty() }.lists.single()
            assertThat(supermarket.name).isEqualTo("スーパー")

            clock.advance(Duration.ofSeconds(1))
            viewModel.createList("薬局")
            awaitUntil { it.lists.size == 2 }
            store.addItem(NewItem(supermarket.id, "牛乳", null, null))

            val lists = awaitUntil { state -> state.lists.any { it.openCount == 1 } }.lists
            assertThat(lists.map { it.name to it.openCount }).containsExactly("スーパー" to 1, "薬局" to 0).inOrder()
            assertThat(lists.map { it.canDelete }).containsExactly(false, true).inOrder()
        }
    }

    @Test
    fun `空白だけの名前ではリストを作成しない`() = runTest {
        viewModel.createList("   ")
        viewModel.uiState.test {
            assertThat(expectMostRecentItem().lists).isEmpty()
        }
    }

    @Test
    fun `空のリストを削除すると一覧から消える`() = runTest {
        viewModel.uiState.test {
            awaitUntil { !it.loading }
            viewModel.createList("スーパー")
            val list = awaitUntil { it.lists.isNotEmpty() }.lists.single()

            viewModel.deleteList(list.id)
            awaitUntil { it.lists.isEmpty() }
        }
    }

    @Test
    fun `作ったばかりのリストは新しいリストとして扱い、購入したあとに空になったリストは買い終わりとして扱う`() = runTest {
        val newList = store.createList("新しい")
        val doneList = store.createList("買い終わり")
        val doneItem = store.addItem(NewItem(doneList, "牛乳", null, null))
        store.completeItem(doneItem, clock.now)
        val openList = store.createList("未購入あり")
        store.addItem(NewItem(openList, "卵", null, null))

        viewModel.uiState.test {
            val lists = awaitUntil { !it.loading }.lists.associateBy { it.name }
            assertThat(lists.getValue("新しい").isNew).isTrue()
            assertThat(lists.getValue("買い終わり").isNew).isFalse()
            assertThat(lists.getValue("未購入あり").isNew).isFalse()
        }
    }

    @Test
    fun `購入を取り消して記録がなくなったリストは、新しいリストに戻る`() = runTest {
        val listId = store.createList("スーパー")
        val itemId = store.addItem(NewItem(listId, "牛乳", null, null))
        store.completeItem(itemId, clock.now)
        store.reopenItem(itemId)
        store.deleteItem(itemId)

        viewModel.uiState.test {
            assertThat(awaitUntil { !it.loading }.lists.single().isNew).isTrue()
        }
    }

    @Test
    fun `リスト名を変更できる。空白だけの名前には変更しない`() = runTest {
        val listId = store.createList("スーパー")
        viewModel.uiState.test {
            awaitUntil { !it.loading }

            viewModel.renameList(listId, "   ")
            viewModel.renameList(listId, " イオン ")

            assertThat(awaitUntil { it.lists.single().name != "スーパー" }.lists.single().name).isEqualTo("イオン")
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
