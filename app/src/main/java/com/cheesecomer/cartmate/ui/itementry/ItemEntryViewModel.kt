package com.cheesecomer.cartmate.ui.itementry

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cheesecomer.cartmate.domain.input.ItemEntry
import com.cheesecomer.cartmate.domain.model.Item
import com.cheesecomer.cartmate.domain.model.ItemId
import com.cheesecomer.cartmate.domain.model.ItemStatus
import com.cheesecomer.cartmate.domain.model.ListId
import com.cheesecomer.cartmate.domain.model.NewItem
import com.cheesecomer.cartmate.domain.model.Visibility
import com.cheesecomer.cartmate.domain.repository.ItemRepository
import com.cheesecomer.cartmate.domain.repository.ItemUpdate
import com.cheesecomer.cartmate.domain.repository.ShoppingListRepository
import com.cheesecomer.cartmate.domain.repository.UpdateItemResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.Clock
import java.time.Duration
import javax.inject.Inject

/**
 * 商品詳細入力画面。[itemId] があれば編集、なければ [name]・[quantity]・[unit] を初期値にした新規。
 * [quantity] は Navigation の引数にするため文字列で渡す
 */
@Serializable
data class ItemEntryRoute(
    val listId: String,
    val itemId: String? = null,
    val name: String = "",
    val quantity: String? = null,
    val unit: String? = null,
)

data class ItemEntryUiState(
    val listName: String = "",
    val listVisibility: Visibility = Visibility.SHARED,
    /** 読み込み中は null */
    val entry: ItemEntry? = null,
)

@HiltViewModel
class ItemEntryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val listRepository: ShoppingListRepository,
    private val itemRepository: ItemRepository,
    private val lastVisibility: LastVisibilityStore,
    private val clock: Clock,
) : ViewModel() {

    private val listId = ListId(checkNotNull(savedStateHandle.get<String>(ItemEntryRoute::listId.name)))
    private val itemId = savedStateHandle.get<String>(ItemEntryRoute::itemId.name)?.let(::ItemId)

    private val _uiState = MutableStateFlow(ItemEntryUiState())
    val uiState: StateFlow<ItemEntryUiState> = _uiState.asStateFlow()

    private val _results = Channel<ItemEntryResult>(Channel.BUFFERED)
    /** 画面を閉じるときの結果 */
    val results: Flow<ItemEntryResult> = _results.receiveAsFlow()

    init {
        viewModelScope.launch {
            val list = listRepository.observeLists().first().firstOrNull { it.id == listId }
            if (list == null) {
                _results.send(ItemEntryResult.CANCELLED)
                return@launch
            }
            val entry = if (itemId == null) {
                ItemEntry.forNew(
                    name = savedStateHandle.get<String>(ItemEntryRoute::name.name).orEmpty(),
                    quantity = savedStateHandle.get<String>(ItemEntryRoute::quantity.name)?.toDoubleOrNull(),
                    unit = savedStateHandle.get<String>(ItemEntryRoute::unit.name),
                    listVisibility = list.visibility,
                    remembered = lastVisibility.get(listId),
                )
            } else {
                val item = itemRepository.observeListItems(listId, completedSince = clock.instant().minus(COMPLETED_WINDOW))
                    .first()
                    .firstOrNull { it.id == itemId }
                when {
                    item == null -> return@launch _results.send(ItemEntryResult.EDIT_REJECTED_NOT_FOUND)
                    item.status == ItemStatus.COMPLETED -> return@launch _results.send(ItemEntryResult.EDIT_REJECTED_COMPLETED)
                    else -> ItemEntry.forEdit(item)
                }
            }
            _uiState.value = ItemEntryUiState(listName = list.name, listVisibility = list.visibility, entry = entry)
        }
    }

    fun onNameChange(text: String) = updateEntry { it.copy(name = text.take(Item.MAX_NAME_LENGTH)) }

    fun onQuantityChange(text: String) = updateEntry { it.copy(quantity = text) }

    fun increment() = updateEntry(ItemEntry::increment)

    fun decrement() = updateEntry(ItemEntry::decrement)

    fun onUnitChange(text: String) = updateEntry { it.copy(unit = text.take(Item.MAX_UNIT_LENGTH)) }

    fun onMemoChange(text: String) = updateEntry { it.copy(memo = text.take(Item.MAX_MEMO_LENGTH)) }

    fun onVisibilityChange(visibility: Visibility) = updateEntry { it.withVisibility(visibility) }

    /** 確定したあとの2回目のタップで、同じ商品を二重に追加しないようにする */
    private var confirmed = false

    fun confirm() {
        val entry = _uiState.value.entry ?: return
        val values = entry.confirm() ?: return
        if (confirmed) return
        confirmed = true
        viewModelScope.launch {
            val editing = entry.editing
            if (editing == null) {
                // 選んだ公開範囲を、このリストでの次の追加の初期値にする
                if (entry.visibilitySelectable) lastVisibility.set(listId, values.visibility)
                itemRepository.addItem(NewItem(listId, values.name, values.quantity, values.unit, values.memo, values.visibility))
                _results.send(ItemEntryResult.ADDED)
            } else {
                val update = ItemUpdate(values.name, values.quantity, values.unit, values.memo)
                val result = when (itemRepository.updateItem(editing, update)) {
                    UpdateItemResult.Updated -> ItemEntryResult.UPDATED
                    UpdateItemResult.AlreadyCompleted -> ItemEntryResult.EDIT_REJECTED_COMPLETED
                    UpdateItemResult.NotFound -> ItemEntryResult.EDIT_REJECTED_NOT_FOUND
                }
                _results.send(result)
            }
        }
    }

    private fun updateEntry(transform: (ItemEntry) -> ItemEntry) {
        _uiState.update { state -> state.entry?.let { state.copy(entry = transform(it)) } ?: state }
    }

    private companion object {
        val COMPLETED_WINDOW: Duration = Duration.ofHours(24)
    }
}
