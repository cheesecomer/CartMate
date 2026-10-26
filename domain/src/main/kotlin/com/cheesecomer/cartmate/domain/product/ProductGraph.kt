package com.cheesecomer.cartmate.domain.product

import com.cheesecomer.cartmate.domain.model.Product
import com.cheesecomer.cartmate.domain.model.ProductId

/**
 * 手動の統合（mergedInto）をたどって、統合先を求める（docs/design/suggestions.md「ProductGraph」）
 */
class ProductGraph(products: Collection<Product>) {
    private val mergedInto: Map<ProductId, ProductId?> = products.associate { it.id to it.mergedInto }

    /**
     * [id] の統合先。統合していなければ自分自身。たどる深さは [MAX_DEPTH] までで、
     * 循環を見つけた場合や深すぎる場合は自分自身を返す（同時に統合操作が行われた場合の保険）
     */
    fun resolve(id: ProductId): ProductId {
        var current = id
        val seen = mutableSetOf(id)
        repeat(MAX_DEPTH) {
            val next = mergedInto[current] ?: return current
            if (!seen.add(next)) return id
            current = next
        }
        return if (mergedInto[current] == null) current else id
    }

    companion object {
        const val MAX_DEPTH = 10
    }
}
