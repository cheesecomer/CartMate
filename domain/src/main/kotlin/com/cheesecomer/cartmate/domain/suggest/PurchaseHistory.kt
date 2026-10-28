package com.cheesecomer.cartmate.domain.suggest

import com.cheesecomer.cartmate.domain.model.Product
import com.cheesecomer.cartmate.domain.model.ProductId
import com.cheesecomer.cartmate.domain.model.Purchase
import com.cheesecomer.cartmate.domain.product.NormalizationDictionary
import com.cheesecomer.cartmate.domain.product.ProductResolver
import java.time.LocalDate
import java.time.ZoneId

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

    /** 商品の画面の「前回: 10/18 購入（約14日ごと）」 */
    data class Summary(
        val lastPurchase: LocalDate,
        /** 周期が分からなければ（予測に使う購入が少なければ）null */
        val typicalIntervalDays: Int?,
    )

    /**
     * [history]（[of] の結果）の前回の購入日と周期。周期は「予測に使わない」にした記録を除いて求める（予測と同じ）。
     * 購入の記録がなければ null
     */
    fun summary(history: List<Purchase>, zone: ZoneId, params: SuggestionParams = SuggestionParams()): Summary? {
        fun Purchase.date(): LocalDate = checkedAt.atZone(zone).toLocalDate()
        val last = history.maxByOrNull { it.checkedAt } ?: return null
        val estimate = CycleEstimator.estimate(history.filter { !it.excludedFromPrediction }.map { it.date() }, params)
        return Summary(last.date(), estimate?.typicalIntervalDays)
    }
}
