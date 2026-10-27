package com.cheesecomer.cartmate.domain.product

import com.cheesecomer.cartmate.domain.model.Item
import com.cheesecomer.cartmate.domain.model.Product
import com.cheesecomer.cartmate.domain.model.ProductId
import com.cheesecomer.cartmate.domain.model.Purchase
import java.time.Instant

/** 自分に見える項目と購入の記録から分かる、商品の使われ方 */
data class ProductUsage(
    /** 自分に見える項目か購入の記録がある商品（他のメンバーがプライベートでだけ使った名前を出さない） */
    val visibleIds: Set<ProductId>,
    /** 商品ごとの、最後に項目を追加した時刻か購入した時刻 */
    val lastUsedAt: Map<ProductId, Instant>,
) {
    companion object {
        val EMPTY = ProductUsage(emptySet(), emptyMap())

        fun of(items: Collection<Item>, purchases: Collection<Purchase>): ProductUsage {
            val uses = items.mapNotNull { item -> item.productId?.let { it to item.createdAt } } +
                purchases.mapNotNull { purchase -> purchase.productId?.let { it to purchase.purchasedAt } }
            return ProductUsage(
                visibleIds = uses.mapTo(HashSet()) { it.first },
                lastUsedAt = uses.groupBy({ it.first }, { it.second }).mapValues { (_, times) -> times.max() },
            )
        }
    }
}

/**
 * 商品の非表示（docs/requirements.md §3.5「商品名の管理」）。家族で共有し、記録は消さない。
 * 非表示にできるのは統合先の商品だけで、そこにまとめている商品も一緒に非表示になる。
 * 非表示にしたあとに、その商品（まとめている商品を含む）の項目の追加か購入があれば、書き込みなしで、また表示する
 * （同じ名前をもう一度追加した場合。オフラインで追加しても、届く順番によらず同じ結果になる）
 */
object ProductHiding {

    /** 非表示になっている統合先の商品 */
    fun hiddenRoots(products: Collection<Product>, usage: ProductUsage): Set<ProductId> {
        val graph = ProductGraph(products)
        val lastUsed = HashMap<ProductId, Instant>()
        for ((id, at) in usage.lastUsedAt) {
            lastUsed.merge(graph.resolve(id), at) { a, b -> maxOf(a, b) }
        }
        return products
            .filter { product ->
                val hiddenAt = product.hiddenAt ?: return@filter false
                product.mergedInto == null && lastUsed[product.id]?.isAfter(hiddenAt) != true
            }
            .mapTo(HashSet()) { it.id }
    }

    /** [id] の商品が非表示か（統合先が非表示なら、まとめている商品も非表示） */
    fun isHidden(id: ProductId, products: Collection<Product>, hiddenRoots: Set<ProductId>): Boolean =
        hiddenRoots.isNotEmpty() && ProductGraph(products).resolve(id) in hiddenRoots
}
