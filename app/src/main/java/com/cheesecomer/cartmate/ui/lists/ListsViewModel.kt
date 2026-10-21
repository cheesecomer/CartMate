package com.cheesecomer.cartmate.ui.lists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cheesecomer.cartmate.domain.model.ListId
import com.cheesecomer.cartmate.domain.model.ListStatus
import com.cheesecomer.cartmate.domain.model.ShoppingList
import com.cheesecomer.cartmate.domain.repository.ItemRepository
import com.cheesecomer.cartmate.domain.repository.ShoppingListRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ListSummary(val id: ListId, val name: String, val openCount: Int) {
    val canDelete: Boolean get() = openCount == 0
}

data class ListsUiState(
    val lists: List<ListSummary> = emptyList(),
    val loading: Boolean = true,
)

@HiltViewModel
class ListsViewModel @Inject constructor(
    private val listRepository: ShoppingListRepository,
    itemRepository: ItemRepository,
) : ViewModel() {

    val uiState: StateFlow<ListsUiState> =
        combine(listRepository.observeLists(), itemRepository.observeOpenItems()) { lists, openItems ->
            val openCounts = openItems.groupingBy { it.listId }.eachCount()
            ListsUiState(
                // 削除したリストの表示ルール（未購入が残っていれば表示）はフェーズ3で実装する
                lists = lists.filter { it.status == ListStatus.ACTIVE }
                    .map { ListSummary(it.id, it.name, openCounts[it.id] ?: 0) },
                loading = false,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ListsUiState())

    fun createList(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.length > ShoppingList.MAX_NAME_LENGTH) return
        viewModelScope.launch { listRepository.createList(trimmed) }
    }

    fun deleteList(id: ListId) {
        viewModelScope.launch { listRepository.deleteList(id) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
