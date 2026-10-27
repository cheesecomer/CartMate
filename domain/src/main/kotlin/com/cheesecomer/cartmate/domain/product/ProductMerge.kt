package com.cheesecomer.cartmate.domain.product

import com.cheesecomer.cartmate.domain.model.Product
import com.cheesecomer.cartmate.domain.model.ProductId

/**
 * 商品の統合画面（docs/requirements.md §3.5「商品の統合」）。行の中身と、統合してよい組み合わせ
 */
object ProductMerge {

    data class Row(
        val product: Product,
        /** 名寄せの理由 */
        val reason: ProductResolver.Reason,
        /** 手動で統合した先の名前。統合していなければ null */
        val mergedIntoName: String?,
        /** 辞書でまとめている品目の名前。辞書でまとめていなければ null */
        val dictionaryName: String?,
    ) {
        /** 「統合を解除」を出すか */
        val canUnmerge: Boolean get() = product.mergedInto != null

        /** 「別の商品として扱う」を出すか（辞書でまとめている） */
        val canOptOut: Boolean get() = reason is ProductResolver.Reason.Dictionary

        /** 「辞書でまとめる」を出すか（手動で外している） */
        val canOptIn: Boolean get() = product.dictionaryOptOut
    }

    /** 画面に出す行。自分に見える商品だけを、名前の順に並べる */
    fun rows(products: Collection<Product>, visibleIds: Set<ProductId>, dictionary: NormalizationDictionary): List<Row> {
        val resolver = ProductResolver(products, dictionary)
        val graph = ProductGraph(products)
        val byId = products.associateBy { it.id }
        return products
            .filter { it.id in visibleIds }
            .map { product ->
                val resolution = resolver.resolve(product.id)
                val dictionaryReason = resolution.reason as? ProductResolver.Reason.Dictionary
                Row(
                    product = product,
                    reason = resolution.reason,
                    mergedIntoName = product.mergedInto?.let { byId[graph.resolve(product.id)]?.displayName },
                    dictionaryName = dictionaryReason?.let { dictionary.canonicalNames[it.canonicalId] },
                )
            }
            .sortedBy { it.product.searchKey }
    }

    /**
     * [from] を [target] に統合するときに書き込む統合先。[target] をたどった先（未統合の商品）にする。
     * 自分自身や、自分に統合されている商品（循環になる）には統合できないので null
     */
    fun mergeTarget(from: ProductId, target: ProductId, products: Collection<Product>): ProductId? {
        val into = ProductGraph(products).resolve(target)
        return into.takeIf { it != from }
    }
}
