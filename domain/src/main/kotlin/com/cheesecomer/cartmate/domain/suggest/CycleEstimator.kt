package com.cheesecomer.cartmate.domain.suggest

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 購入の周期の推定（docs/design/suggestions.md「周期推定」） */
data class CycleEstimate(
    /** 日付単位でまとめた購入日（昇順） */
    val eventDates: List<LocalDate>,
    val medianIntervalDays: Double,
    /** 中央絶対偏差 / 中央値。大きいほど周期が不安定 */
    val dispersion: Double,
) {
    val lastPurchase: LocalDate get() = eventDates.last()

    /** 画面に出す「約14日ごと」の日数 */
    val typicalIntervalDays: Int get() = Math.round(medianIntervalDays).toInt().coerceAtLeast(1)
}

object CycleEstimator {

    /**
     * [purchaseDates] から周期を推定する。同じ日の購入は1回として数え（間隔0で中央値が歪まないように）、
     * 購入した日が [SuggestionParams.minEvents] 日に満たなければ null。直近 [SuggestionParams.maxIntervals] 個の間隔の中央値を使う
     */
    fun estimate(purchaseDates: Collection<LocalDate>, params: SuggestionParams = SuggestionParams()): CycleEstimate? {
        val dates = purchaseDates.toSortedSet().toList()
        if (dates.size < params.minEvents) return null
        val intervals = dates.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b).toDouble() }.takeLast(params.maxIntervals)
        val median = median(intervals)
        val deviation = median(intervals.map { kotlin.math.abs(it - median) })
        return CycleEstimate(dates, median, if (median > 0) deviation / median else 0.0)
    }

    /** 最後に買ってから、周期の何倍の日数がたったか */
    fun elapsedRatio(estimate: CycleEstimate, today: LocalDate): Double =
        ChronoUnit.DAYS.between(estimate.lastPurchase, today) / estimate.medianIntervalDays

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2 else sorted[middle]
    }
}
