package com.cheesecomer.cartmate.domain.model

import java.time.Instant

data class ShoppingList(
    val id: ListId,
    val name: String,
    /** 並び順。小さいほど上（docs/design/data-model.md） */
    val sortKey: Double,
    val createdAt: Instant,
    val createdBy: UserId,
    val status: ListStatus,
) {
    companion object {
        const val MAX_NAME_LENGTH = 40
    }
}

enum class ListStatus { ACTIVE, DELETED }
