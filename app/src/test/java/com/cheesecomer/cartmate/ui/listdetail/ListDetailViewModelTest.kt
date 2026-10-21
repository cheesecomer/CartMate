package com.cheesecomer.cartmate.ui.listdetail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import app.cash.turbine.turbineScope
import com.cheesecomer.cartmate.data.memory.InMemoryStore
import com.cheesecomer.cartmate.domain.input.AddItemEvent
import com.cheesecomer.cartmate.domain.input.AddItemState
import com.cheesecomer.cartmate.domain.model.ItemStatus
import com.cheesecomer.cartmate.domain.model.ListId
import com.cheesecomer.cartmate.domain.model.NewItem
import com.cheesecomer.cartmate.domain.model.UserId
import com.cheesecomer.cartmate.testing.MainDispatcherExtension
import com.cheesecomer.cartmate.testing.MutableClock
import com.cheesecomer.cartmate.testing.awaitUntil
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration
import java.time.Instant

class ListDetailViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val clock = MutableClock()
    private val store = InMemoryStore(clock) { UserId("taro") }
    private val listId: ListId = runBlocking { store.createList("スーパー") }
    private val otherListId: ListId = runBlocking { store.createList("薬局") }

    private val viewModel = ListDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("listId" to listId.value)),
        listRepository = store,
        itemRepository = store,
        clock = clock,
    )

    @Test
    fun `商品名、Enter、確定で項目が追加され、商品名の入力に戻る`() = runTest {
        viewModel.uiState.test {
            assertThat(awaitUntil { !it.loading }.listName).isEqualTo("スーパー")

            viewModel.onAddItemEvent(AddItemEvent.NameFocusChanged(true))
            viewModel.onAddItemEvent(AddItemEvent.NameChanged("牛乳"))
            viewModel.onAddItemEvent(AddItemEvent.NameSubmitted)
            awaitUntil { it.addItem is AddItemState.Detail }

            viewModel.onAddItemEvent(AddItemEvent.UnitChanged("本"))
            viewModel.onAddItemEvent(AddItemEvent.Confirmed)

            val state = awaitUntil { it.items.isNotEmpty() }
            assertThat(state.addItem).isEqualTo(AddItemState.FocusedEmpty)
            val item = state.items.single()
            assertThat(item.inputName).isEqualTo("牛乳")
            assertThat(item.quantity).isEqualTo(1.0)
            assertThat(item.unit).isEqualTo("本")
        }
    }

    @Test
    fun `ほかのリストの項目は表示しない`() = runTest {
        store.addItem(NewItem(otherListId, "目薬", null, null))
        store.addItem(NewItem(listId, "牛乳", null, null))

        viewModel.uiState.test {
            assertThat(awaitUntil { !it.loading }.items.map { it.inputName }).containsExactly("牛乳")
        }
    }

    @Test
    fun `チェックしても同じ位置に残り、元に戻せるメッセージが出る。元に戻すと購入が無効になる`() = runTest {
        store.addItem(NewItem(listId, "牛乳", null, null))
        clock.advance(Duration.ofSeconds(1))
        store.addItem(NewItem(listId, "卵", null, null))
        turbineScope {
            val states = viewModel.uiState.testIn(backgroundScope)
            val messages = viewModel.messages.testIn(backgroundScope)
            val item = states.awaitUntil { it.items.size == 2 }.items.first()

            viewModel.check(item)

            assertThat(messages.awaitItem()).isEqualTo(DetailMessage.Checked(item))
            val afterCheck = states.awaitUntil { it.items.first().status == ItemStatus.COMPLETED }
            assertThat(afterCheck.items.map { it.inputName }).containsExactly("牛乳", "卵").inOrder()

            viewModel.uncheck(item)

            states.awaitUntil { it.items.first().status == ItemStatus.OPEN }
            assertThat(store.observePurchases(Instant.EPOCH).first().single().active).isFalse()
        }
    }

    @Test
    fun `すでにチェック済みの項目をチェックすると、その旨のメッセージが出る`() = runTest {
        val itemId = store.addItem(NewItem(listId, "牛乳", null, null))
        val item = store.observeOpenItems().first().single()
        store.completeItem(itemId, clock.now)

        viewModel.messages.test {
            viewModel.check(item)
            assertThat(awaitItem()).isEqualTo(DetailMessage.AlreadyCompleted(item))
        }
    }

    @Test
    fun `削除すると元に戻せるメッセージが出る。元に戻すと項目が復活する`() = runTest {
        store.addItem(NewItem(listId, "牛乳", null, null))
        turbineScope {
            val states = viewModel.uiState.testIn(backgroundScope)
            val messages = viewModel.messages.testIn(backgroundScope)
            val item = states.awaitUntil { it.items.isNotEmpty() }.items.single()

            viewModel.delete(item)

            assertThat(messages.awaitItem()).isEqualTo(DetailMessage.Deleted(item))
            states.awaitUntil { it.items.isEmpty() }

            viewModel.undoDelete(item)

            assertThat(states.awaitUntil { it.items.isNotEmpty() }.items.map { it.id }).containsExactly(item.id)
        }
    }


    @Test
    fun `項目をタップして編集し、確定すると内容が変わる`() = runTest {
        store.addItem(NewItem(listId, "牛乳", 1.0, null))
        viewModel.uiState.test {
            val item = awaitUntil { it.items.isNotEmpty() }.items.single()

            viewModel.onAddItemEvent(AddItemEvent.EditRequested(item))
            awaitUntil { it.addItem is AddItemState.Editing }
            viewModel.onAddItemEvent(AddItemEvent.QuantityChanged("2"))
            viewModel.onAddItemEvent(AddItemEvent.UnitChanged("本"))
            viewModel.onAddItemEvent(AddItemEvent.Confirmed)

            val updated = awaitUntil { it.items.singleOrNull()?.unit == "本" }
            assertThat(updated.items.single().quantity).isEqualTo(2.0)
            assertThat(updated.addItem).isEqualTo(AddItemState.Idle(""))
        }
    }

    @Test
    fun `編集中にチェックされた項目は更新せず、その旨のメッセージが出る`() = runTest {
        val itemId = store.addItem(NewItem(listId, "牛乳", 1.0, null))
        val item = store.observeOpenItems().first().single()
        viewModel.messages.test {
            viewModel.onAddItemEvent(AddItemEvent.EditRequested(item))
            store.completeItem(itemId, clock.now)
            viewModel.onAddItemEvent(AddItemEvent.NameChanged("低脂肪乳"))
            viewModel.onAddItemEvent(AddItemEvent.Confirmed)

            assertThat(awaitItem()).isEqualTo(DetailMessage.CannotEditCompleted("低脂肪乳"))
        }
    }

    @Test
    fun `項目を動かすと並び順が変わる`() = runTest {
        val milk = store.addItem(NewItem(listId, "牛乳", null, null))
        val egg = store.addItem(NewItem(listId, "卵", null, null))
        viewModel.uiState.test {
            awaitUntil { it.items.size == 2 }

            viewModel.move(egg, previous = null, next = milk)

            awaitUntil { state -> state.items.map { it.inputName } == listOf("卵", "牛乳") }
        }
    }
}
