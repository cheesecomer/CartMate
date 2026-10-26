package com.cheesecomer.cartmate.ui.listdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cheesecomer.cartmate.domain.input.AddItemEffect
import com.cheesecomer.cartmate.domain.input.AddItemEvent
import com.cheesecomer.cartmate.domain.input.AddItemReducer
import com.cheesecomer.cartmate.domain.input.AddItemState
import com.cheesecomer.cartmate.domain.model.Item
import com.cheesecomer.cartmate.domain.model.ItemId
import com.cheesecomer.cartmate.domain.model.ItemStatus
import com.cheesecomer.cartmate.domain.model.ListId
import com.cheesecomer.cartmate.domain.model.ListStatus
import com.cheesecomer.cartmate.domain.model.NewItem
import com.cheesecomer.cartmate.domain.model.ShoppingList
import com.cheesecomer.cartmate.domain.model.Visibility
import com.cheesecomer.cartmate.domain.repository.CompleteResult
import com.cheesecomer.cartmate.domain.repository.DeleteItemResult
import com.cheesecomer.cartmate.domain.repository.DeleteListResult
import com.cheesecomer.cartmate.domain.repository.ItemRepository
import com.cheesecomer.cartmate.domain.repository.ReopenResult
import com.cheesecomer.cartmate.domain.repository.ShoppingListRepository
import com.cheesecomer.cartmate.domain.visibility.VisibilityDefaults
import com.cheesecomer.cartmate.ui.itementry.ItemEntryResult
import com.cheesecomer.cartmate.ui.itementry.LastVisibilityStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import com.cheesecomer.cartmate.domain.list.ShoppingRound
import com.cheesecomer.cartmate.domain.list.ListDisplay

data class ListDetailUiState(
    val listName: String = "",
    val listMemo: String? = null,
    val listVisibility: Visibility = Visibility.SHARED,
    /** 未購入の項目と、直近24時間にチェックした項目（並び順。チェック済みも元の位置に表示する） */
    val items: List<Item> = emptyList(),
    val addItem: AddItemState = AddItemState.Initial,
    val loading: Boolean = true,
    /** Enter と「＋」で追加するときの公開範囲。サジェストのシートに表示し、共有のリストでは切り替えられる（§3.4） */
    val addVisibility: Visibility = Visibility.SHARED,
) {
    val addVisibilitySelectable: Boolean get() = VisibilityDefaults.selectable(listVisibility)

    val openCount: Int get() = items.count { it.status == ItemStatus.OPEN }
    val completedCount: Int get() = items.count { it.status == ItemStatus.COMPLETED }
}

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
    data object CannotEditCompleted : DetailMessage
    /** 編集中に、ほかの操作で削除されていた */
    data object EditedItemNotFound : DetailMessage
    /** のこりの商品があるため、リストを削除できない */
    data object CannotDeleteList : DetailMessage
    data object Failed : DetailMessage
}

/** 画面の移動 */
sealed interface DetailNavigation {
    /** 新しい商品の商品詳細入力画面を開く */
    data class OpenEntry(val name: String, val quantity: Double?, val unit: String?) : DetailNavigation
    /** リストを削除したので一覧に戻る */
    data object ListDeleted : DetailNavigation
}

@HiltViewModel
class ListDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val listRepository: ShoppingListRepository,
    private val itemRepository: ItemRepository,
    private val lastVisibility: LastVisibilityStore,
    private val clock: Clock,
) : ViewModel() {

    private val listId = ListId(checkNotNull(savedStateHandle.get<String>(ListDetailRoute::listId.name)))

    private val addItemState = MutableStateFlow(AddItemState.Initial)
    private val _messages = Channel<DetailMessage>(Channel.BUFFERED)
    val messages: Flow<DetailMessage> = _messages.receiveAsFlow()
    private val _navigation = Channel<DetailNavigation>(Channel.BUFFERED)
    val navigation: Flow<DetailNavigation> = _navigation.receiveAsFlow()

    val uiState: StateFlow<ListDetailUiState> = combine(
        listRepository.observeLists().map { lists -> lists.firstOrNull { it.id == listId } },
        itemRepository.observeListItems(listId, completedSince = clock.instant().minus(COMPLETED_WINDOW)),
        addItemState,
    ) { listOrDeleted, items, addItem ->
        // 削除したリストでも、未購入の項目が残っている間は開ける（docs/design/sync.md「リスト削除の競合」）
        val list = listOrDeleted?.takeIf { ListDisplay.isShown(it, items.count { item -> item.status == ItemStatus.OPEN }) }
        ListDetailUiState(
            listName = list?.name.orEmpty(),
            listMemo = list?.memo,
            listVisibility = list?.visibility ?: Visibility.SHARED,
            // 前回の買い物のチェック済みは、片付けたあとは表示しない
            items = ShoppingRound.visible(items, windowStart = clock.instant().minus(COMPLETED_WINDOW), clearedAt = list?.clearedAt),
            addItem = addItem,
            loading = false,
        )
    }.combine(lastVisibility.observe(listId)) { state, remembered ->
            state.copy(addVisibility = VisibilityDefaults.initial(state.listVisibility, remembered))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ListDetailUiState())

    /** サジェストのシートで公開範囲を切り替える。そのリストの次の追加の初期値になる（docs/requirements.md §3.4） */
    fun setAddVisibility(visibility: Visibility) {
        if (uiState.value.addVisibilitySelectable) lastVisibility.set(listId, visibility)
    }

    fun onAddItemEvent(event: AddItemEvent) {
        val transition = AddItemReducer.reduce(addItemState.value, event)
        addItemState.value = transition.state
        when (val effect = transition.effect) {
            // Enter と候補の「＋」は、そのリストの直前の公開範囲で追加する（直前の公開範囲は変えない）
            is AddItemEffect.AddItem -> viewModelScope.launch {
                val list = listRepository.observeLists().first().firstOrNull { it.id == listId } ?: return@launch
                val visibility = VisibilityDefaults.initial(list.visibility, lastVisibility.get(listId))
                itemRepository.addItem(NewItem(listId, effect.name, effect.quantity, effect.unit, memo = null, visibility))
            }
            is AddItemEffect.OpenEntry -> _navigation.trySend(DetailNavigation.OpenEntry(effect.name, effect.quantity, effect.unit))
            null -> Unit
        }
    }

    /** 商品詳細入力画面から戻った */
    fun onEntryResult(result: ItemEntryResult) {
        when (result) {
            ItemEntryResult.ADDED -> onAddItemEvent(AddItemEvent.EntryClosed(added = true))
            ItemEntryResult.CANCELLED -> onAddItemEvent(AddItemEvent.EntryClosed(added = false))
            ItemEntryResult.UPDATED -> Unit
            ItemEntryResult.EDIT_REJECTED_COMPLETED -> _messages.trySend(DetailMessage.CannotEditCompleted)
            ItemEntryResult.EDIT_REJECTED_NOT_FOUND -> _messages.trySend(DetailMessage.EditedItemNotFound)
        }
    }

    fun check(item: Item) {
        viewModelScope.launch {
            when (itemRepository.completeItem(item.id, checkedAt = clock.instant())) {
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

    fun move(id: ItemId, previous: ItemId?, next: ItemId?) {
        viewModelScope.launch { itemRepository.moveItem(id, previous, next) }
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

    fun updateList(name: String, memo: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.length > ShoppingList.MAX_NAME_LENGTH) return
        val trimmedMemo = memo.trim().take(ShoppingList.MAX_MEMO_LENGTH).ifEmpty { null }
        viewModelScope.launch { listRepository.updateList(listId, trimmed, trimmedMemo) }
    }

    /** 「リストを削除」を選んだ。のこりの商品があれば理由を伝え、なければ true を返して確認ダイアログを出させる */
    fun canDeleteList(): Boolean {
        if (uiState.value.openCount == 0) return true
        _messages.trySend(DetailMessage.CannotDeleteList)
        return false
    }

    fun deleteList() {
        viewModelScope.launch {
            when (runCatching { listRepository.deleteList(listId) }.getOrNull()) {
                DeleteListResult.Deleted, DeleteListResult.NotFound -> _navigation.send(DetailNavigation.ListDeleted)
                DeleteListResult.NotEmpty -> _messages.send(DetailMessage.CannotDeleteList)
                // 通信の問題など
                null -> _messages.send(DetailMessage.Failed)
            }
        }
    }

    private companion object {
        val COMPLETED_WINDOW: Duration = Duration.ofHours(24)
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
