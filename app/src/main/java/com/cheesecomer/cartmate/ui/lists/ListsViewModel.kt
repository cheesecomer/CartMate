package com.cheesecomer.cartmate.ui.lists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cheesecomer.cartmate.domain.model.ListId
import com.cheesecomer.cartmate.domain.model.ListStatus
import com.cheesecomer.cartmate.domain.model.ShoppingList
import com.cheesecomer.cartmate.domain.repository.ItemRepository
import com.cheesecomer.cartmate.domain.repository.PurchaseRepository
import com.cheesecomer.cartmate.domain.repository.ShoppingListRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import javax.inject.Inject

data class ListSummary(
    val id: ListId,
    val name: String,
    val openCount: Int,
    /** このリストで購入（チェック）した有効な記録があるか */
    val hasPurchases: Boolean,
) {
    val canDelete: Boolean get() = openCount == 0

    /** まだ何も買っていない新しいリスト。未購入が0件でも、買い終わったリストとは区別する */
    val isNew: Boolean get() = openCount == 0 && !hasPurchases
}

data class ListsUiState(
    val lists: List<ListSummary> = emptyList(),
    val loading: Boolean = true,
)

@HiltViewModel
class ListsViewModel @Inject constructor(
    private val listRepository: ShoppingListRepository,
    itemRepository: ItemRepository,
    purchaseRepository: PurchaseRepository,
    clock: Clock,
) : ViewModel() {

    val uiState: StateFlow<ListsUiState> =
        combine(
            listRepository.observeLists(),
            itemRepository.observeOpenItems(),
            // 予測と同じ範囲（直近365日）の購入の記録で、新しいリストかどうかを判定する
            purchaseRepository.observePurchases(since = clock.instant().minus(PURCHASE_WINDOW)),
        ) { lists, openItems, purchases ->
            val openCounts = openItems.groupingBy { it.listId }.eachCount()
            val listsWithPurchases = purchases.filter { it.active }.mapTo(HashSet()) { it.listId }
            ListsUiState(
                // 削除したリストの表示ルール（未購入が残っていれば表示）はフェーズ3で実装する
                lists = lists.filter { it.status == ListStatus.ACTIVE }
                    .map { ListSummary(it.id, it.name, openCounts[it.id] ?: 0, it.id in listsWithPurchases) },
                loading = false,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ListsUiState())

    fun createList(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.length > ShoppingList.MAX_NAME_LENGTH) return
        viewModelScope.launch { listRepository.createList(trimmed) }
    }

    fun renameList(id: ListId, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.length > ShoppingList.MAX_NAME_LENGTH) return
        viewModelScope.launch { listRepository.renameList(id, trimmed) }
    }

    fun moveList(id: ListId, previous: ListId?, next: ListId?) {
        viewModelScope.launch { listRepository.moveList(id, previous, next) }
    }

    fun deleteList(id: ListId) {
        viewModelScope.launch { listRepository.deleteList(id) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        val PURCHASE_WINDOW: Duration = Duration.ofDays(365)
    }
}
