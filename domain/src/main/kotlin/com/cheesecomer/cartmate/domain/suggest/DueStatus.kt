package com.cheesecomer.cartmate.domain.suggest

/**
 * 時期が近い商品の、買い時の状態（「あと1日」「今日が目安」「3日過ぎ」。docs/requirements.md §4.3）。
 * 補足の「約N日ごと・最後はM日前」と食い違わないよう、画面に出す周期の日数（[CycleEstimate.typicalIntervalDays]）と
 * 最後に買ってからの日数の差で決める
 */
sealed interface DueStatus {
    /** 目安の日まで、あと [days] 日 */
    data class Remaining(val days: Long) : DueStatus

    /** 今日が目安の日 */
    data object Today : DueStatus

    /** 目安の日を [days] 日過ぎた */
    data class Overdue(val days: Long) : DueStatus

    companion object {
        fun of(intervalDays: Int, daysSinceLast: Long): DueStatus {
            val diff = intervalDays - daysSinceLast
            return when {
                diff > 0 -> Remaining(diff)
                diff == 0L -> Today
                else -> Overdue(-diff)
            }
        }
    }
}
