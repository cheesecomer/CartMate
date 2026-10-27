package com.cheesecomer.cartmate.domain.suggest

import com.cheesecomer.cartmate.domain.model.Product
import com.cheesecomer.cartmate.domain.model.ProductId
import com.cheesecomer.cartmate.domain.model.Purchase
import com.cheesecomer.cartmate.domain.product.NormalizationDictionary
import com.cheesecomer.cartmate.domain.product.ProductResolver

/**
 * 商品ごとの購入履歴（docs/requirements.md §4.1）。[productId] と同じ商品として数える購入の記録（手動の統合と辞書の名寄せで
 * まとまる商品のもの）を新しい順に。Undo した記録は購入ではないので出さない。「予測に使わない」にした記録は出す（戻せるように）
 */
object PurchaseHistory {

    fun of(
        productId: ProductId,
        products: Collection<Product>,
        purchases: Collection<Purchase>,
        dictionary: NormalizationDictionary,
    ): List<Purchase> {
        val resolver = ProductResolver(products, dictionary)
        val key = resolver.resolve(productId).key
        return purchases
            .filter { purchase -> purchase.active && purchase.productId?.let { resolver.resolve(it).key == key } == true }
            .sortedByDescending { it.checkedAt }
    }
}
