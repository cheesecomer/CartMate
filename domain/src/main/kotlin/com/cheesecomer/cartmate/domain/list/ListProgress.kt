package com.cheesecomer.cartmate.domain.list

/**
 * リストの件数と進捗（docs/requirements.md §3.1）。件数は、閲覧している本人に見える項目だけで数える
 */
data class ListProgress(
    val openCount: Int,
    /** 直近24時間に完了した件数 */
    val completedCount: Int,
    /** 直近365日に、このリストで有効な購入の記録があるか */
    val hasPurchases: Boolean,
) {
    val status: Status
        get() = when {
            openCount > 0 -> Status.REMAINING
            hasPurchases -> Status.DONE
            else -> Status.NOT_REGISTERED
        }

    /** 進捗バーの割合（0〜1） */
    val fraction: Float
        get() = when (status) {
            Status.REMAINING -> completedCount.toFloat() / (openCount + completedCount)
            Status.NOT_REGISTERED -> 0f
            Status.DONE -> 1f
        }

    enum class Status {
        /** 未購入の商品がある（「N 件 のこり」） */
        REMAINING,

        /** 未購入が0件で、購入の記録もない（「0 件 登録なし」） */
        NOT_REGISTERED,

        /** 未購入が0件で、購入の記録がある（「0 件 買い終わった！」） */
        DONE,
    }
}
