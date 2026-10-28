package com.cheesecomer.cartmate.ui.products

import com.cheesecomer.cartmate.domain.model.Product
import com.cheesecomer.cartmate.domain.model.ProductId
import com.cheesecomer.cartmate.domain.model.Purchase
import com.cheesecomer.cartmate.domain.product.NormalizationDictionary
import com.cheesecomer.cartmate.domain.product.ProductHiding
import com.cheesecomer.cartmate.domain.product.ProductUsage
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
        /** 非表示になっている統合先（[ProductHiding.hiddenRoots]） */
        val hidden: Set<ProductId> = emptySet(),
        /** 自分に見える直近365日の購入の記録（Undo したものを含む） */
        val purchases: List<Purchase> = emptyList(),
    )

    fun observe(): Flow<Snapshot> = combine(
        productRepository.observeProducts(),
        itemRepository.observeOpenItems(),
        purchaseRepository.observePurchases(since = clock.instant().minus(PURCHASE_WINDOW)),
        dictionaryProvider.observeDictionary(),
    ) { products, items, purchases, dictionary ->
        val usage = ProductUsage.of(items, purchases)
        Snapshot(products, usage.visibleIds, dictionary, ProductHiding.hiddenRoots(products, usage), purchases)
    }

    private companion object {
        val PURCHASE_WINDOW: Duration = Duration.ofDays(365)
    }
}
