package com.cheesecomer.cartmate.ui.listdetail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import app.cash.turbine.turbineScope
import com.cheesecomer.cartmate.data.memory.InMemoryStore
import com.cheesecomer.cartmate.domain.input.AddItemEvent
import com.cheesecomer.cartmate.domain.input.AddItemState
import com.cheesecomer.cartmate.domain.model.ItemStatus
import com.cheesecomer.cartmate.domain.model.ListId
import com.cheesecomer.cartmate.domain.model.UserId
import com.cheesecomer.cartmate.domain.model.Visibility
import com.cheesecomer.cartmate.domain.repository.ShoppingListRepository
import com.cheesecomer.cartmate.domain.repository.DeleteListResult
import com.cheesecomer.cartmate.testing.MainDispatcherExtension
import com.cheesecomer.cartmate.testing.MutableClock
import com.cheesecomer.cartmate.testing.awaitUntil
import com.cheesecomer.cartmate.testing.createList
import com.cheesecomer.cartmate.testing.newItem
import com.cheesecomer.cartmate.ui.itementry.ItemEntryResult
import com.cheesecomer.cartmate.ui.itementry.LastVisibilityStore
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.flowOf

class ListDetailViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val clock = MutableClock()
    private val store = InMemoryStore(clock) { UserId("taro") }
    private val listId: ListId = runBlocking { store.createList("スーパー") }
    private val otherListId: ListId = runBlocking { store.createList("薬局") }

    private val lastVisibility = LastVisibilityStore()
    private val viewModel = viewModelFor(listId)

    private fun viewModelFor(listId: ListId) = ListDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("listId" to listId.value)),
        listRepository = store,
        itemRepository = store,
        lastVisibility = lastVisibility,
        clock = clock,
        memberDirectory = { flowOf(emptyList()) },
    )

    @Test
    fun `シートで商品名を入れて Enter を押すと数量1・単位「個」で追加され、シートは開いたまま入力欄が空になる`() = runTest {
        viewModel.uiState.test {
            assertThat(awaitUntil { !it.loading }.listName).isEqualTo("スーパー")

            viewModel.onAddItemEvent(AddItemEvent.FieldFocused)
            viewModel.onAddItemEvent(AddItemEvent.TextChanged("牛乳"))
            viewModel.onAddItemEvent(AddItemEvent.Submitted)

            val state = awaitUntil { it.items.isNotEmpty() }
            assertThat(state.addItem).isEqualTo(AddItemState.Open(""))
            val item = state.items.single()
            assertThat(item.inputName).isEqualTo("牛乳")
            assertThat(item.quantity).isEqualTo(1.0)
            assertThat(item.unit).isEqualTo("個")
            assertThat(item.visibility).isEqualTo(Visibility.SHARED)
        }
    }

    @Test
    fun `Enter による追加は、そのリストで直前に選んだ公開範囲を使う`() = runTest {
        lastVisibility.set(listId, Visibility.PRIVATE)
        lastVisibility.set(otherListId, Visibility.SHARED)
        viewModel.onAddItemEvent(AddItemEvent.FieldFocused)
        viewModel.onAddItemEvent(AddItemEvent.TextChanged("プレゼント"))
        viewModel.onAddItemEvent(AddItemEvent.Submitted)

        viewModel.uiState.test {
            assertThat(awaitUntil { it.items.isNotEmpty() }.items.single().visibility).isEqualTo(Visibility.PRIVATE)
        }
        assertThat(lastVisibility.get(listId)).isEqualTo(Visibility.PRIVATE)
    }

    @Test
    fun `プライベートのリストでは、Enter による追加はプライベートになる`() = runTest {
        val privateList = store.createList("自分用", Visibility.PRIVATE)
        val viewModel = viewModelFor(privateList)
        viewModel.onAddItemEvent(AddItemEvent.FieldFocused)
        viewModel.onAddItemEvent(AddItemEvent.TextChanged("目薬"))
        viewModel.onAddItemEvent(AddItemEvent.Submitted)

        viewModel.uiState.test {
            assertThat(awaitUntil { it.items.isNotEmpty() }.items.single().visibility).isEqualTo(Visibility.PRIVATE)
        }
    }

    @Test
    fun `「数量を設定して追加」で商品詳細入力画面を開き、やめて戻るとシートに戻る`() = runTest {
        turbineScope {
            val states = viewModel.uiState.testIn(backgroundScope)
            val navigation = viewModel.navigation.testIn(backgroundScope)
            viewModel.onAddItemEvent(AddItemEvent.FieldFocused)
            viewModel.onAddItemEvent(AddItemEvent.TextChanged("豆乳"))
            viewModel.onAddItemEvent(AddItemEvent.DetailRequested)

            assertThat(navigation.awaitItem()).isEqualTo(DetailNavigation.OpenEntry("豆乳", 1.0, "個"))
            states.awaitUntil { it.addItem == AddItemState.Entering("豆乳") }

            viewModel.onEntryResult(ItemEntryResult.CANCELLED)
            states.awaitUntil { it.addItem == AddItemState.Open("豆乳") }

            viewModel.onAddItemEvent(AddItemEvent.DetailRequested)
            navigation.awaitItem()
            viewModel.onEntryResult(ItemEntryResult.ADDED)
            states.awaitUntil { it.addItem == AddItemState.Idle("") }
        }
    }

    @Test
    fun `ほかのリストの項目は表示しない`() = runTest {
        store.addItem(newItem(otherListId, "目薬"))
        store.addItem(newItem(listId, "牛乳"))

        viewModel.uiState.test {
            assertThat(awaitUntil { !it.loading }.items.map { it.inputName }).containsExactly("牛乳")
        }
    }

    @Test
    fun `チェックしても同じ位置に残り、元に戻せるメッセージが出る。元に戻すと購入が無効になる`() = runTest {
        store.addItem(newItem(listId, "牛乳"))
        clock.advance(Duration.ofSeconds(1))
        store.addItem(newItem(listId, "卵"))
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
        val itemId = store.addItem(newItem(listId, "牛乳"))
        val item = store.observeOpenItems().first().single()
        store.completeItem(itemId, clock.now)

        viewModel.messages.test {
            viewModel.check(item)
            assertThat(awaitItem()).isEqualTo(DetailMessage.AlreadyCompleted(item))
        }
    }

    @Test
    fun `削除すると元に戻せるメッセージが出る。元に戻すと項目が復活する`() = runTest {
        store.addItem(newItem(listId, "牛乳"))
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
    fun `編集が拒否されて戻ると、その旨のメッセージが出る`() = runTest {
        viewModel.messages.test {
            viewModel.onEntryResult(ItemEntryResult.EDIT_REJECTED_COMPLETED)
            assertThat(awaitItem()).isEqualTo(DetailMessage.CannotEditCompleted)
            viewModel.onEntryResult(ItemEntryResult.EDIT_REJECTED_NOT_FOUND)
            assertThat(awaitItem()).isEqualTo(DetailMessage.EditedItemNotFound)
        }
    }

    @Test
    fun `リスト名と備考を変更できる。空白だけの名前には変更しない`() = runTest {
        viewModel.uiState.test {
            awaitUntil { !it.loading }

            viewModel.updateList("   ", "")
            viewModel.updateList(" イオン ", " 火曜市 ")

            val state = awaitUntil { it.listName != "スーパー" }
            assertThat(state.listName).isEqualTo("イオン")
            assertThat(state.listMemo).isEqualTo("火曜市")
        }
    }

    @Test
    fun `のこりの商品があるリストは削除できず、理由のメッセージが出る`() = runTest {
        store.addItem(newItem(listId, "牛乳"))
        turbineScope {
            val states = viewModel.uiState.testIn(backgroundScope)
            val messages = viewModel.messages.testIn(backgroundScope)
            states.awaitUntil { it.items.isNotEmpty() }

            assertThat(viewModel.canDeleteList()).isFalse()
            assertThat(messages.awaitItem()).isEqualTo(DetailMessage.CannotDeleteList)
        }
    }

    @Test
    fun `のこりの商品がないリストは削除でき、一覧に戻る`() = runTest {
        turbineScope {
            val states = viewModel.uiState.testIn(backgroundScope)
            val navigation = viewModel.navigation.testIn(backgroundScope)
            states.awaitUntil { !it.loading }

            assertThat(viewModel.canDeleteList()).isTrue()
            viewModel.deleteList()

            assertThat(navigation.awaitItem()).isEqualTo(DetailNavigation.ListDeleted)
            // 削除したリストは表示しなくなる
            states.awaitUntil { it.listName.isEmpty() }
        }
    }

    @Test
    fun `項目を動かすと並び順が変わる`() = runTest {
        val milk = store.addItem(newItem(listId, "牛乳"))
        val egg = store.addItem(newItem(listId, "卵"))
        viewModel.uiState.test {
            awaitUntil { it.items.size == 2 }

            viewModel.move(egg, previous = null, next = milk)

            awaitUntil { state -> state.items.map { it.inputName } == listOf("卵", "牛乳") }
        }
    }

    /** リストの削除だけを [delete] に差し替えた ViewModel */
    private fun viewModelDeleting(delete: suspend (ListId) -> DeleteListResult) = ListDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("listId" to listId.value)),
        listRepository = object : ShoppingListRepository by store {
            override suspend fun deleteList(id: ListId): DeleteListResult = delete(id)
        },
        itemRepository = store,
        lastVisibility = lastVisibility,
        clock = clock,
        memberDirectory = { flowOf(emptyList()) },
    )

    @Test
    fun `リストの削除に失敗しても落ちずに失敗を知らせる`() = runTest {
        // オフラインで「リストを削除」を押すと、サーバーへの問い合わせが例外で失敗する
        val viewModel = viewModelDeleting { throw IllegalStateException("Failed to get document from server") }
        turbineScope {
            val states = viewModel.uiState.testIn(backgroundScope)
            val messages = viewModel.messages.testIn(backgroundScope)
            states.awaitUntil { !it.loading }

            viewModel.deleteList()

            assertThat(messages.awaitItem()).isEqualTo(DetailMessage.Failed)
        }
    }

    @Test
    fun `オフラインのときはリストを削除せず、理由を知らせる`() = runTest {
        val viewModel = viewModelDeleting { DeleteListResult.Offline }
        turbineScope {
            val states = viewModel.uiState.testIn(backgroundScope)
            val messages = viewModel.messages.testIn(backgroundScope)
            val navigation = viewModel.navigation.testIn(backgroundScope)
            states.awaitUntil { !it.loading }

            viewModel.deleteList()

            assertThat(messages.awaitItem()).isEqualTo(DetailMessage.Offline)
            navigation.expectNoEvents()
        }
    }
}
