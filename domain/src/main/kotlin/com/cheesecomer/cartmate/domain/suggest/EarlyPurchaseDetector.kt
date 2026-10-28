package com.cheesecomer.cartmate.domain.suggest

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 「前回 10/18 に購入・いつもは約14日ごと」。周期が分からないか不安定なら [typicalIntervalDays] は null（「前回 10/28 に購入」） */
data class EarlyPurchaseNotice(val lastPurchase: LocalDate, val typicalIntervalDays: Int?)

/** 早すぎる再購入の検知（docs/design/suggestions.md「早すぎる再購入の検知」）。表示するだけで、追加は妨げない */
object EarlyPurchaseDetector {

    /**
     * 周期が分かれば、経過率が [SuggestionParams.earlyRatio] 未満なら知らせる。
     * 周期が分からない（購入日が3日未満）か不安定なら、前回の購入 [lastPurchase] から
     * [SuggestionParams.recentPurchaseDays] 日以内のときだけ、周期なしで知らせる（家族がもう買ってきたのに気づかず足さないように）
     */
    fun detect(
        estimate: CycleEstimate?,
        lastPurchase: LocalDate?,
        today: LocalDate,
        params: SuggestionParams = SuggestionParams(),
    ): EarlyPurchaseNotice? {
        if (estimate != null && estimate.dispersion <= params.maxDispersion) {
            if (CycleEstimator.elapsedRatio(estimate, today) >= params.earlyRatio) return null
            return EarlyPurchaseNotice(estimate.lastPurchase, estimate.typicalIntervalDays)
        }
        if (lastPurchase == null || ChronoUnit.DAYS.between(lastPurchase, today) > params.recentPurchaseDays) return null
        return EarlyPurchaseNotice(lastPurchase, typicalIntervalDays = null)
    }
}
