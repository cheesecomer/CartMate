package com.cheesecomer.cartmate.domain.model

import java.time.Instant

data class Item(
    val id: ItemId,
    val listId: ListId,
    val inputName: String,
    /** 並び順。小さいほど上（docs/design/data-model.md） */
    val sortKey: Double,
    val quantity: Double?,
    val unit: String?,
    val memo: String?,
    /** プライベートのリストの項目は、常に PRIVATE */
    val visibility: Visibility,
    val status: ItemStatus,
    val createdAt: Instant,
    val createdBy: UserId,
    val completedAt: Instant?,
    val completedBy: UserId?,
    /** 入力した名前に対応する商品（統合元）。統合先への置き換えは読むときに行う（docs/design/suggestions.md） */
    val productId: ProductId? = null,
) {
    companion object {
        const val MAX_NAME_LENGTH = 60
        const val MAX_UNIT_LENGTH = 10
        const val MAX_MEMO_LENGTH = 100
        const val MAX_QUANTITY = 9999.0
    }
}

enum class ItemStatus { OPEN, COMPLETED }

data class NewItem(
    val listId: ListId,
    val inputName: String,
    val quantity: Double?,
    val unit: String?,
    val memo: String?,
    val visibility: Visibility,
    /**
     * 入力した名前が新しい商品のとき、その商品を最初からこの商品に統合する（表記ゆれのチップを選んだ場合）。
     * 統合先までたどった商品を渡す
     */
    val mergeInto: ProductId? = null,
)
