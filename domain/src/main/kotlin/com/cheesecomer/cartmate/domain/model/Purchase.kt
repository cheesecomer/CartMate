package com.cheesecomer.cartmate.domain.model

import java.time.Instant

/**
 * チェックによって記録される購入。ID は元の項目と同じ（[itemId]）で、1項目につき最大1件。
 * Undo すると [active] が false になり、予測に使われなくなる。
 */
data class Purchase(
    val itemId: ItemId,
    val listId: ListId,
    val listNameSnapshot: String,
    val inputName: String,
    val quantity: Double?,
    val unit: String?,
    val memo: String?,
    /** 元の項目と同じ。PRIVATE なら [purchasedBy]（＝項目を作った本人）にだけ見える */
    val visibility: Visibility,
    val checkedAt: Instant,
    val purchasedAt: Instant,
    val purchasedBy: UserId,
    val active: Boolean,
    val revokedAt: Instant?,
    val revokedBy: UserId?,
)
