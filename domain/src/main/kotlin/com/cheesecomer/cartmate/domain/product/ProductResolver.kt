package com.cheesecomer.cartmate.domain.product

import com.cheesecomer.cartmate.domain.model.Product
import com.cheesecomer.cartmate.domain.model.ProductId

/**
 * 名寄せの解決（docs/design/suggestions.md「名寄せの解決」）。読み取るときにだけ使い、記録は書き換えない。
 * 優先順位は、手動の統合・分離 ＞ 辞書 ＞ 機械的な正規化（同じ productId）
 */
class ProductResolver(products: Collection<Product>, private val dictionary: NormalizationDictionary) {
    private val byId = products.associateBy { it.id }
    private val graph = ProductGraph(products)

    /** [id] の商品を、どの商品としてまとめるかと、その理由 */
    fun resolve(id: ProductId): Resolution {
        val root = graph.resolve(id)
        val manual = root != id
        val product = byId[root]
        if (product == null || product.dictionaryOptOut) {
            val reason = when {
                product?.dictionaryOptOut == true -> Reason.OptedOut(merged = manual)
                manual -> Reason.Manual
                else -> Reason.Self
            }
            return Resolution(ResolvedKey.OfProduct(root), reason)
        }
        val alias = dictionary.lookup(product.normalizedName)
        return when {
            alias != null -> Resolution(
                ResolvedKey.OfCanonical(alias.canonicalId),
                Reason.Dictionary(matchedName = product.normalizedName, canonicalId = alias.canonicalId, merged = manual),
            )
            manual -> Resolution(ResolvedKey.OfProduct(root), Reason.Manual)
            else -> Resolution(ResolvedKey.OfProduct(root), Reason.Self)
        }
    }

    /** 周期だけ合わせて数えるグループ。なければ [resolve] と同じまとめ先 */
    fun cycleKey(id: ProductId): CycleKey {
        val resolution = resolve(id)
        val canonical = (resolution.key as? ResolvedKey.OfCanonical)?.canonicalId
        val group = canonical?.let(dictionary.cycleGroups::get)
        return if (group != null) CycleKey.OfGroup(group) else CycleKey.Of(resolution.key)
    }

    data class Resolution(val key: ResolvedKey, val reason: Reason)

    /** まとめ先。同じ値になる商品どうしを、同じ商品として扱う */
    sealed interface ResolvedKey {
        data class OfProduct(val productId: ProductId) : ResolvedKey
        data class OfCanonical(val canonicalId: CanonicalId) : ResolvedKey
    }

    sealed interface CycleKey {
        data class Of(val key: ResolvedKey) : CycleKey
        data class OfGroup(val groupId: CycleGroupId) : CycleKey
    }

    /** まとめた理由。統合の画面に表示する */
    sealed interface Reason {
        /** どれにも当たらない（同じ productId の商品だけ） */
        data object Self : Reason

        /** 手動で統合した */
        data object Manual : Reason

        /** 手動で辞書の名寄せを外した。[merged] は手動の統合もしているか */
        data class OptedOut(val merged: Boolean) : Reason

        /** 辞書の別名に一致した。[merged] は手動の統合もしているか */
        data class Dictionary(val matchedName: String, val canonicalId: CanonicalId, val merged: Boolean) : Reason
    }
}
