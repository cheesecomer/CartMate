package com.cheesecomer.cartmate.domain.suggest

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class EarlyPurchaseDetectorTest {

    private val start = LocalDate.of(2026, 1, 1)

    /** 100日ごとに3回買った（最後は [start] の200日後） */
    private val steady = CycleEstimator.estimate(listOf(start, start.plusDays(100), start.plusDays(200)))!!
    private val last = start.plusDays(200)

    @Test
    fun `経過率が0点49なら知らせる`() {
        assertThat(EarlyPurchaseDetector.detect(steady, last.plusDays(49)))
            .isEqualTo(EarlyPurchaseNotice(last, typicalIntervalDays = 100))
    }

    @Test
    fun `経過率が0点50なら知らせない`() {
        assertThat(EarlyPurchaseDetector.detect(steady, last.plusDays(50))).isNull()
    }

    @Test
    fun `周期が不安定なら知らせない`() {
        val unstable = CycleEstimator.estimate(listOf(start, start.plusDays(2), start.plusDays(12), start.plusDays(42)))!!
        assertThat(unstable.dispersion).isGreaterThan(0.5)
        assertThat(EarlyPurchaseDetector.detect(unstable, start.plusDays(43))).isNull()
    }

    @Test
    fun `購入日が2日なら知らせない`() {
        assertThat(EarlyPurchaseDetector.detect(CycleEstimator.estimate(listOf(start, start.plusDays(10))), start.plusDays(11))).isNull()
    }
}
