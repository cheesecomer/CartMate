package com.cheesecomer.cartmate.ui.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cheesecomer.cartmate.domain.model.ProductId
import com.cheesecomer.cartmate.domain.product.ProductGraph
import com.cheesecomer.cartmate.domain.product.ProductMerge
import com.cheesecomer.cartmate.domain.repository.ItemRepository
import com.cheesecomer.cartmate.domain.repository.ProductRepository
import com.cheesecomer.cartmate.domain.repository.PurchaseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import com.cheesecomer.cartmate.domain.repository.DictionaryProvider

/**
 * 商品の統合画面（docs/requirements.md §3.5「商品の統合」）。統合・統合の解除・辞書の名寄せを外す。
 * どれも家族で共有し、オフラインでも行える
 */
@HiltViewModel
class ProductMergeViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    itemRepository: ItemRepository,
    purchaseRepository: PurchaseRepository,
    clock: Clock,
    dictionaryProvider: DictionaryProvider,
) : ViewModel() {

    /** 自分に見える項目か購入の記録がある商品の行（他のメンバーがプライベートでだけ使った名前を出さない） */
    val rows: StateFlow<List<ProductMerge.Row>> = combine(
        productRepository.observeProducts(),
        itemRepository.observeOpenItems(),
        purchaseRepository.observePurchases(since = clock.instant().minus(PURCHASE_WINDOW)),
        dictionaryProvider.observeDictionary(),
    ) { products, items, purchases, dictionary ->
        val visible = (items.mapNotNull { it.productId } + purchases.mapNotNull { it.productId }).toSet()
        ProductMerge.rows(products, visible, dictionary)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    /** [from] の統合の相手の候補。自分と、自分に統合されている商品（循環になる）は出さない */
    fun mergeTargets(from: ProductId): List<ProductMerge.Row> {
        val rows = rows.value
        val products = rows.map { it.product }
        val graph = ProductGraph(products)
        return rows.filter { it.product.id != from && graph.resolve(it.product.id) != from }
    }

    fun merge(from: ProductId, target: ProductId) {
        val into = ProductMerge.mergeTarget(from, target, rows.value.map { it.product }) ?: return
        viewModelScope.launch { productRepository.merge(from, into) }
    }

    fun unmerge(id: ProductId) {
        viewModelScope.launch { productRepository.unmerge(id) }
    }

    fun setDictionaryOptOut(id: ProductId, optOut: Boolean) {
        viewModelScope.launch { productRepository.setDictionaryOptOut(id, optOut) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        val PURCHASE_WINDOW: Duration = Duration.ofDays(365)
    }
}
