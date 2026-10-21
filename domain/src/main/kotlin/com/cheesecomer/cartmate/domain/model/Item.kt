package com.cheesecomer.cartmate.domain.model

import java.time.Instant

// productId はフェーズ3（商品の同一性）で追加する
data class Item(
    val id: ItemId,
    val listId: ListId,
    val inputName: String,
    val quantity: Double?,
    val unit: String?,
    val status: ItemStatus,
    val createdAt: Instant,
    val createdBy: UserId,
    val completedAt: Instant?,
    val completedBy: UserId?,
) {
    companion object {
        const val MAX_NAME_LENGTH = 60
        const val MAX_UNIT_LENGTH = 10
        const val MAX_QUANTITY = 9999.0
    }
}

enum class ItemStatus { OPEN, COMPLETED }

data class NewItem(
    val listId: ListId,
    val inputName: String,
    val quantity: Double?,
    val unit: String?,
)
