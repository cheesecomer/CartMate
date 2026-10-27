package com.cheesecomer.cartmate.domain.suggest

import java.time.LocalDate

/** 「前回 10/18 に購入・いつもは約14日ごと」 */
data class EarlyPurchaseNotice(val lastPurchase: LocalDate, val typicalIntervalDays: Int)

/** 早すぎる再購入の検知（docs/design/suggestions.md「早すぎる再購入の検知」）。表示するだけで、追加は妨げない */
object EarlyPurchaseDetector {

    /** 周期が分からない（購入日が3日未満）か不安定なら null。経過率が [SuggestionParams.earlyRatio] 未満なら知らせる */
    fun detect(estimate: CycleEstimate?, today: LocalDate, params: SuggestionParams = SuggestionParams()): EarlyPurchaseNotice? {
        if (estimate == null || estimate.dispersion > params.maxDispersion) return null
        if (CycleEstimator.elapsedRatio(estimate, today) >= params.earlyRatio) return null
        return EarlyPurchaseNotice(estimate.lastPurchase, estimate.typicalIntervalDays)
    }
}
