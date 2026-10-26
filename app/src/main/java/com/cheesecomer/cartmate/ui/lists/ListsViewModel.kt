package com.cheesecomer.cartmate.ui.lists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cheesecomer.cartmate.domain.list.ListProgress
import com.cheesecomer.cartmate.domain.model.Account
import com.cheesecomer.cartmate.domain.model.ListId
import com.cheesecomer.cartmate.domain.model.ListStatus
import com.cheesecomer.cartmate.domain.model.NewList
import com.cheesecomer.cartmate.domain.model.ShoppingList
import com.cheesecomer.cartmate.domain.model.Visibility
import com.cheesecomer.cartmate.domain.repository.AccountRepository
import com.cheesecomer.cartmate.domain.repository.ItemRepository
import com.cheesecomer.cartmate.domain.repository.OnlineStatus
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
import com.cheesecomer.cartmate.domain.list.ShoppingRound

data class ListSummary(
    val id: ListId,
    val name: String,
    val memo: String?,
    val visibility: Visibility,
    val progress: ListProgress,
)

data class ListsUiState(
    val lists: List<ListSummary> = emptyList(),
    val loading: Boolean = true,
    /** オフラインならバナーを表示する */
    val offline: Boolean = false,
    /** 上部バーのアバター。タップで家族画面を開く */
    val account: Account? = null,
)

@HiltViewModel
class ListsViewModel @Inject constructor(
    private val listRepository: ShoppingListRepository,
    itemRepository: ItemRepository,
    purchaseRepository: PurchaseRepository,
    clock: Clock,
    accountRepository: AccountRepository,
    onlineStatus: OnlineStatus,
) : ViewModel() {

    val uiState: StateFlow<ListsUiState> =
        combine(
            listRepository.observeLists(),
            itemRepository.observeOpenItems(),
            // 予測と同じ範囲（直近365日）の購入の記録で、買い終わったリストかどうかを判定する
            purchaseRepository.observePurchases(since = clock.instant().minus(PURCHASE_WINDOW)),
        ) { lists, openItems, purchases ->
            val openCounts = openItems.groupingBy { it.listId }.eachCount()
            val active = purchases.filter { it.active }
            val listsWithPurchases = active.mapTo(HashSet()) { it.listId }
            // 有効な購入はチェック済みの項目と1対1なので、直近24時間に完了した件数は購入の記録から数える
            // 前回の買い物を片付けたリストでは、片付けたあとの完了だけを数える
            val windowStart = clock.instant().minus(COMPLETED_WINDOW)
            val clearedAt = lists.associate { it.id to it.clearedAt }
            val completedCounts = active
                .filter { ShoppingRound.isShown(it.purchasedAt, windowStart, clearedAt[it.listId]) }
                .groupingBy { it.listId }.eachCount()
            ListsUiState(
                // 削除したリストの表示ルール（未購入が残っていれば表示）はフェーズ3で実装する
                lists = lists.filter { it.status == ListStatus.ACTIVE }.map {
                    ListSummary(
                        id = it.id,
                        name = it.name,
                        memo = it.memo,
                        visibility = it.visibility,
                        progress = ListProgress(
                            openCount = openCounts[it.id] ?: 0,
                            completedCount = completedCounts[it.id] ?: 0,
                            hasPurchases = it.id in listsWithPurchases,
                        ),
                    )
                },
                loading = false,
            )
        }.combine(accountRepository.observeAccount()) { state, account -> state.copy(account = account) }
            .combine(onlineStatus.observeOnline()) { state, online -> state.copy(offline = !online) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ListsUiState())

    fun createList(name: String, memo: String, visibility: Visibility) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.length > ShoppingList.MAX_NAME_LENGTH) return
        val trimmedMemo = memo.trim().take(ShoppingList.MAX_MEMO_LENGTH).ifEmpty { null }
        viewModelScope.launch { listRepository.createList(NewList(trimmed, trimmedMemo, visibility)) }
    }

    fun moveList(id: ListId, previous: ListId?, next: ListId?) {
        viewModelScope.launch { listRepository.moveList(id, previous, next) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        val PURCHASE_WINDOW: Duration = Duration.ofDays(365)
        val COMPLETED_WINDOW: Duration = Duration.ofHours(24)
    }
}
