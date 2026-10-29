package com.cheesecomer.cartmate.ui.products

import com.cheesecomer.cartmate.domain.repository.CurrentUserProvider
import com.cheesecomer.cartmate.domain.repository.ItemRepository
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cheesecomer.cartmate.domain.model.ItemId
import com.cheesecomer.cartmate.domain.model.ProductId
import com.cheesecomer.cartmate.domain.model.Purchase
import com.cheesecomer.cartmate.domain.repository.DictionaryProvider
import com.cheesecomer.cartmate.domain.repository.MemberDirectory
import com.cheesecomer.cartmate.domain.repository.ProductRepository
import com.cheesecomer.cartmate.domain.repository.PurchaseRepository
import com.cheesecomer.cartmate.domain.suggest.PurchaseHistory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import javax.inject.Inject

/**
 * 商品ごとの購入履歴（docs/requirements.md §4.1）。直近365日の、自分に見える購入の記録を新しい順に出し、
 * 1件ずつ「予測に使わない」を切り替える
 */
@HiltViewModel
class PurchaseHistoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    productRepository: ProductRepository,
    private val purchaseRepository: PurchaseRepository,
    private val itemRepository: ItemRepository,
    currentUser: CurrentUserProvider,
    memberDirectory: MemberDirectory,
    private val clock: Clock,
    dictionaryProvider: DictionaryProvider,
) : ViewModel() {

    private val productId = ProductId(checkNotNull(savedStateHandle.get<String>(PurchaseHistoryRoute::productId.name)))

    data class Row(
        val purchase: Purchase,
        /** 端末のタイムゾーンでの購入日（チェックした日） */
        val date: LocalDate,
        /** チェックした人。家族にいない人は null（「退会したメンバー」） */
        val checkerName: String?,
        /** 「予測に使わない」にした人と日。予測に使っていれば null */
        val excludedByName: String?,
        val excludedDate: LocalDate?,
        /** 自分がチェックした購入。チェックした人を「あなた」と表示し、項目ごと削除できる */
        val purchasedByMe: Boolean = false,
        /** 自分が「予測に使わない」にした。外した人を「あなた」と表示する */
        val excludedByMe: Boolean = false,
    ) {
        val deletable: Boolean get() = purchasedByMe
    }

    data class State(
        val productName: String = "",
        val rows: List<Row> = emptyList(),
        val loading: Boolean = true,
        /** 記録があった状態から0件になった（最後の購入を削除した）。商品名の管理まで戻って知らせる */
        val emptied: Boolean = false,
    )

    val state: StateFlow<State> = combine(
        productRepository.observeProducts(),
        purchaseRepository.observePurchases(since = clock.instant().minus(PURCHASE_WINDOW)),
        dictionaryProvider.observeDictionary(),
        memberDirectory.observeNames(),
    ) { products, purchases, dictionary, names ->
        val zone = clock.zone
        val me = currentUser.currentUserId()
        State(
            productName = products.find { it.id == productId }?.displayName.orEmpty(),
            rows = PurchaseHistory.of(productId, products, purchases, dictionary).map { purchase ->
                Row(
                    purchase = purchase,
                    date = purchase.checkedAt.atZone(zone).toLocalDate(),
                    checkerName = names.of(purchase.purchasedBy),
                    excludedByName = purchase.excludedBy?.takeIf { purchase.excludedFromPrediction }?.let(names::of),
                    excludedDate = purchase.excludedAt?.takeIf { purchase.excludedFromPrediction }?.atZone(zone)?.toLocalDate(),
                    purchasedByMe = purchase.purchasedBy == me,
                    excludedByMe = purchase.excludedFromPrediction && purchase.excludedBy == me,
                )
            },
            loading = false,
        )
    }.scan(State()) { previous, next ->
        next.copy(
            productName = next.productName.ifEmpty { previous.productName },
            emptied = previous.emptied || (previous.rows.isNotEmpty() && !next.loading && next.rows.isEmpty()),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), State())

    /** [id] の記録を予測に使わない（[excluded] が false なら使う）。家族で共有し、オフラインでも行える */
    fun setExcluded(id: ItemId, excluded: Boolean) {
        viewModelScope.launch { purchaseRepository.setExcludedFromPrediction(id, excluded) }
    }

    /** 自分がチェックした購入を、項目ごと削除する（確認は画面で行う）。家族の画面からも消え、元に戻せない */
    fun delete(id: ItemId) {
        viewModelScope.launch { itemRepository.deletePurchase(id) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        val PURCHASE_WINDOW: Duration = Duration.ofDays(365)
    }
}
