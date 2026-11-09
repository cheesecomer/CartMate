package com.cheesecomer.cartmate.domain.product

import com.cheesecomer.cartmate.domain.model.Product
import com.cheesecomer.cartmate.domain.model.ProductId
import com.cheesecomer.cartmate.domain.model.Purchase
import com.cheesecomer.cartmate.domain.model.Visibility
import com.cheesecomer.cartmate.domain.text.ChoiceName
import com.cheesecomer.cartmate.domain.text.NameNormalizer
import java.time.Instant

/**
 * AI に判定を頼む内容（docs/design/suggestions.md「問い合わせる条件」）。名前と属性だけで、
 * uid・家族・リスト名・日時・数量は含めない（docs/requirements.md §4.2）
 */
data class JudgementRequest(
    val productId: ProductId,
    val normalizedName: String,
    val qualifiers: Map<String, List<String>>,
) {
    val key: String get() = JudgementKey.of(normalizedName, qualifiers)

    companion object {
        /** 関数が受け付ける長さ（firebase/functions/src/validate.ts） */
        private const val NAME_MAX = 60
        private const val QUALIFIER_MAX = 20
        private const val QUALIFIER_VALUES_MAX = 3

        /**
         * 商品 [id] を AI に判定させる内容。問い合わせる対象でなければ null。
         * 共有の項目で購入された商品で、手動の統合・分離がなく、辞書で品目が決まらず、選択肢を含む名前でないもの。
         * 属性は、その商品を共有の項目で最初に購入した記録の名前と備考から読み取る
         */
        fun of(
            id: ProductId,
            products: Collection<Product>,
            purchases: Collection<Purchase>,
            dictionary: NormalizationDictionary,
        ): JudgementRequest? {
            val product = products.firstOrNull { it.id == id } ?: return null
            // 手動の統合・分離（統合している、まとめている、辞書から外している）があれば問い合わせない
            if (product.mergedInto != null || product.dictionaryOptOut || products.any { it.mergedInto == id }) return null
            if (ProductResolver(products, dictionary).resolve(id).reason != ProductResolver.Reason.Self) return null
            if (ChoiceName.hasChoice(product.displayName)) return null
            val first = firstSharedPurchase(id, purchases) ?: return null
            val qualifiers = JudgementKey.qualifiersOf(AttributeExtractor.extract(first.inputName, first.memo, dictionary).qualifiers)
            if (product.normalizedName.length > NAME_MAX) return null
            if (qualifiers.values.any { values -> values.size > QUALIFIER_VALUES_MAX || values.any { it.length > QUALIFIER_MAX } }) return null
            return JudgementRequest(id, product.normalizedName, qualifiers)
        }

        /** 属性を読み取る記録。共有の項目で有効な購入のうち最も古いもの（プライベートな購入の名前と備考は送らない） */
        fun firstSharedPurchase(id: ProductId, purchases: Collection<Purchase>): Purchase? = purchases
            .filter { it.active && it.visibility == Visibility.SHARED && productOf(it) == id }
            .minByOrNull { it.purchasedAt }

        /**
         * 今ある判定 [existing] があっても問い合わせ直すか（docs/design/data-model.md「問い合わせ直す条件」。関数の shouldAsk と同じ）。
         * 判定がない、バージョンが違う、pending のまま5分、failed から1日なら問い合わせ直す
         */
        fun shouldAsk(existing: NameJudgement?, now: Instant): Boolean {
            if (existing == null || !existing.isCurrent) return true
            return when (existing.status) {
                NameJudgement.Status.PENDING ->
                    existing.requestedAt == null || !now.isBefore(existing.requestedAt.plus(NameJudgement.PENDING_TIMEOUT))
                NameJudgement.Status.FAILED -> {
                    val since = existing.judgedAt ?: existing.requestedAt
                    since == null || !now.isBefore(since.plus(NameJudgement.RETRY_AFTER_FAILURE))
                }
                NameJudgement.Status.DONE -> false
            }
        }

        private fun productOf(purchase: Purchase): ProductId =
            purchase.productId ?: ProductId(NameNormalizer.productId(purchase.inputName))
    }
}
