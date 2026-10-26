package com.cheesecomer.cartmate.domain.model

import java.time.Instant

data class ShoppingList(
    val id: ListId,
    val name: String,
    /** 備考。一覧のカードに表示する */
    val memo: String?,
    val visibility: Visibility,
    /** 並び順。小さいほど上（docs/design/data-model.md） */
    val sortKey: Double,
    val createdAt: Instant,
    val createdBy: UserId,
    val status: ListStatus,
    /**
     * 前回の買い物のチェック済みを片付けた時刻（docs/requirements.md §3.2）。これより前にチェックした項目は、
     * リスト詳細にも進捗にも出さない。まだ片付けていなければ null
     */
    val clearedAt: Instant? = null,
) {
    companion object {
        const val MAX_NAME_LENGTH = 40
        const val MAX_MEMO_LENGTH = 60
    }
}

enum class ListStatus { ACTIVE, DELETED }

data class NewList(
    val name: String,
    val memo: String?,
    val visibility: Visibility,
)
