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
    /**
     * 常に非表示（docs/requirements.md §4.3）。時期が近い商品・補完・表記ゆれの候補・早すぎる再購入の注意に出さない。
     * 再購入しても解除されない。統合先の商品の設定が効く
     */
    val suggestionExcluded: Boolean = false,
    /** 常に非表示にした人 */
    val suggestionExcludedBy: UserId? = null,
    /** 非表示にした時刻。非表示にしていなければ null。非表示かどうかは [com.cheesecomer.cartmate.domain.product.ProductHiding] で決める */
    val hiddenAt: Instant? = null,
    /** 非表示（一時的）にした人 */
    val hiddenBy: UserId? = null,
)
