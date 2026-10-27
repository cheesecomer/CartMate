package com.cheesecomer.cartmate.domain.model

import java.time.Instant

@JvmInline
value class ProductId(val value: String)

/**
 * 商品（docs/design/data-model.md）。ID は正規化した商品名から決まる（NameNormalizer.productId）。
 * 統合と辞書の分離は、家族で共有する訂正（docs/design/suggestions.md「サジェストの補正」）
 */
data class Product(
    val id: ProductId,
    val displayName: String,
    val normalizedName: String,
    val searchKey: String,
    val createdAt: Instant,
    /** 手動で統合した先。統合していなければ null */
    val mergedInto: ProductId? = null,
    /** 名寄せ辞書でまとめない（手動の分離） */
    val dictionaryOptOut: Boolean = false,
    /** 非表示にした時刻。非表示にしていなければ null。非表示かどうかは [com.cheesecomer.cartmate.domain.product.ProductHiding] で決める */
    val hiddenAt: Instant? = null,
)
