package com.cheesecomer.cartmate.ui.listdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cheesecomer.cartmate.domain.input.AddItemEffect
import com.cheesecomer.cartmate.domain.input.AddItemEvent
import com.cheesecomer.cartmate.domain.input.AddItemReducer
import com.cheesecomer.cartmate.domain.input.AddItemState
import com.cheesecomer.cartmate.domain.model.Item
import com.cheesecomer.cartmate.domain.model.ListId
import com.cheesecomer.cartmate.domain.model.ListStatus
import com.cheesecomer.cartmate.domain.model.NewItem
import com.cheesecomer.cartmate.domain.repository.CompleteResult
import com.cheesecomer.cartmate.domain.repository.DeleteItemResult
import com.cheesecomer.cartmate.domain.repository.ItemRepository
import com.cheesecomer.cartmate.domain.repository.ItemUpdate
import com.cheesecomer.cartmate.domain.repository.ReopenResult
import com.cheesecomer.cartmate.domain.repository.ShoppingListRepository
import com.cheesecomer.cartmate.domain.repository.UpdateItemResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import javax.inject.Inject

data class ListDetailUiState(
    val listName: String = "",
    /** 未購入の項目と、直近24時間にチェックした項目（追加した順。チェック済みも元の位置に表示する） */
    val items: List<Item> = emptyList(),
    val addItem: AddItemState = AddItemState.Initial,
    val loading: Boolean = true,
)

/** Snackbar で一度だけ表示するメッセージ */
sealed interface DetailMessage {
    /** チェックした。元に戻せる */
    data class Checked(val item: Item) : DetailMessage
    /** 削除した。元に戻せる */
    data class Deleted(val item: Item) : DetailMessage
    data class AlreadyCompleted(val item: Item) : DetailMessage
    data object AlreadyOpen : DetailMessage
    data class CannotDeleteCompleted(val item: Item) : DetailMessage
    /** 編集中に、ほかの操作でチェックされていた */
    data class CannotEditCompleted(val name: String) : DetailMessage
    /** 編集中に、ほかの操作で削除されていた */
    data object EditedItemNotFound : DetailMessage
    data object Failed : DetailMessage
}

@HiltViewModel
class ListDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    listRepository: ShoppingListRepository,
    private val itemRepository: ItemRepository,
    private val clock: Clock,
) : ViewModel() {

    private val listId = ListId(checkNotNull(savedStateHandle.get<String>(ListDetailRoute::listId.name)))

    private val addItemState = MutableStateFlow(AddItemState.Initial)
    private val _messages = Channel<DetailMessage>(Channel.BUFFERED)
    val messages: Flow<DetailMessage> = _messages.receiveAsFlow()

    val uiState: StateFlow<ListDetailUiState> = combine(
        listRepository.observeLists().map { lists ->
            lists.firstOrNull { it.id == listId && it.status == ListStatus.ACTIVE }?.name.orEmpty()
        },
        itemRepository.observeListItems(listId, completedSince = clock.instant().minus(COMPLETED_WINDOW)),
        addItemState,
    ) { listName, items, addItem ->
        ListDetailUiState(
            listName = listName,
            items = items,
            addItem = addItem,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ListDetailUiState())

    fun onAddItemEvent(event: AddItemEvent) {
        val transition = AddItemReducer.reduce(addItemState.value, event)
        addItemState.value = transition.state
        when (val effect = transition.effect) {
            is AddItemEffect.AddItem -> viewModelScope.launch {
                itemRepository.addItem(NewItem(listId, effect.name, effect.quantity, effect.unit))
            }
            is AddItemEffect.UpdateItem -> viewModelScope.launch {
                val update = ItemUpdate(effect.name, effect.quantity, effect.unit)
                when (itemRepository.updateItem(effect.itemId, update)) {
                    UpdateItemResult.Updated -> Unit
                    UpdateItemResult.AlreadyCompleted -> _messages.send(DetailMessage.CannotEditCompleted(effect.name))
                    UpdateItemResult.NotFound -> _messages.send(DetailMessage.EditedItemNotFound)
                }
            }
            null -> Unit
        }
    }

    fun check(item: Item) {
        viewModelScope.launch {
            when (val result = itemRepository.completeItem(item.id, checkedAt = clock.instant())) {
                CompleteResult.Completed -> _messages.send(DetailMessage.Checked(item))
                is CompleteResult.AlreadyCompleted -> _messages.send(DetailMessage.AlreadyCompleted(item))
                is CompleteResult.Retry, is CompleteResult.Rejected -> _messages.send(DetailMessage.Failed)
            }
        }
    }

    /** チェックを外す（Undo を含む） */
    fun uncheck(item: Item) {
        viewModelScope.launch {
            when (itemRepository.reopenItem(item.id)) {
                ReopenResult.Reopened -> Unit
                ReopenResult.AlreadyOpen -> _messages.send(DetailMessage.AlreadyOpen)
                is ReopenResult.Retry, is ReopenResult.Rejected -> _messages.send(DetailMessage.Failed)
            }
        }
    }

    fun delete(item: Item) {
        viewModelScope.launch {
            when (itemRepository.deleteItem(item.id)) {
                DeleteItemResult.Deleted -> _messages.send(DetailMessage.Deleted(item))
                DeleteItemResult.AlreadyCompleted -> _messages.send(DetailMessage.CannotDeleteCompleted(item))
                DeleteItemResult.NotFound -> Unit
            }
        }
    }

    fun undoDelete(item: Item) {
        viewModelScope.launch { itemRepository.restoreItem(item) }
    }

    private companion object {
        val COMPLETED_WINDOW: Duration = Duration.ofHours(24)
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
