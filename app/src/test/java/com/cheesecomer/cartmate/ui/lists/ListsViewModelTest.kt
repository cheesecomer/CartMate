package com.cheesecomer.cartmate.ui.lists

import app.cash.turbine.test
import com.cheesecomer.cartmate.data.memory.InMemoryStore
import com.cheesecomer.cartmate.domain.model.NewItem
import com.cheesecomer.cartmate.domain.model.UserId
import com.cheesecomer.cartmate.testing.MainDispatcherExtension
import com.cheesecomer.cartmate.testing.MutableClock
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
    private val viewModel = ListsViewModel(store, store)

    @Test
    fun `リストを作成すると、作成順に未購入件数とともに表示される`() = runTest {
        viewModel.uiState.test {
            assertThat(awaitItem().loading).isTrue()
            assertThat(awaitItem().lists).isEmpty()

            viewModel.createList(" スーパー ")
            val supermarket = awaitItem().lists.single()
            assertThat(supermarket.name).isEqualTo("スーパー")

            clock.advance(Duration.ofSeconds(1))
            viewModel.createList("薬局")
            awaitItem()
            store.addItem(NewItem(supermarket.id, "牛乳", null, null))

            val lists = awaitItem().lists
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
            skipItems(2)
            viewModel.createList("スーパー")
            val list = awaitItem().lists.single()

            viewModel.deleteList(list.id)
            assertThat(awaitItem().lists).isEmpty()
        }
    }
}
