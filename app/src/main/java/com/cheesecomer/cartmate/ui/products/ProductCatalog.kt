package com.cheesecomer.cartmate.ui.products

import com.cheesecomer.cartmate.domain.model.Product
import com.cheesecomer.cartmate.domain.model.ProductId
import com.cheesecomer.cartmate.domain.product.NormalizationDictionary
import com.cheesecomer.cartmate.domain.repository.DictionaryProvider
import com.cheesecomer.cartmate.domain.repository.ItemRepository
import com.cheesecomer.cartmate.domain.repository.ProductRepository
import com.cheesecomer.cartmate.domain.repository.PurchaseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.Duration
import javax.inject.Inject

/**
 * 「商品名の管理」の画面が使う、家族の商品と、自分に見える商品（自分に見える項目か購入の記録があるもの）と、名寄せ辞書。
 * ほかのメンバーがプライベートでだけ使った名前は出さない
 */
class ProductCatalog @Inject constructor(
    private val productRepository: ProductRepository,
    private val itemRepository: ItemRepository,
    private val purchaseRepository: PurchaseRepository,
    private val clock: Clock,
    private val dictionaryProvider: DictionaryProvider,
) {
    data class Snapshot(
        val products: List<Product>,
        val visibleIds: Set<ProductId>,
        val dictionary: NormalizationDictionary,
    )

    fun observe(): Flow<Snapshot> = combine(
        productRepository.observeProducts(),
        itemRepository.observeOpenItems(),
        purchaseRepository.observePurchases(since = clock.instant().minus(PURCHASE_WINDOW)),
        dictionaryProvider.observeDictionary(),
    ) { products, items, purchases, dictionary ->
        val visible = (items.mapNotNull { it.productId } + purchases.mapNotNull { it.productId }).toSet()
        Snapshot(products, visible, dictionary)
    }

    private companion object {
        val PURCHASE_WINDOW: Duration = Duration.ofDays(365)
    }
}
