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
        assertThat(EarlyPurchaseDetector.detect(steady, last, last.plusDays(49)))
            .isEqualTo(EarlyPurchaseNotice(last, typicalIntervalDays = 100))
    }

    @Test
    fun `経過率が0点50なら知らせない`() {
        assertThat(EarlyPurchaseDetector.detect(steady, last, last.plusDays(50))).isNull()
    }

    @Test
    fun `周期が不安定なら、4日たっていれば知らせない`() {
        val unstable = CycleEstimator.estimate(listOf(start, start.plusDays(2), start.plusDays(12), start.plusDays(42)))!!
        assertThat(unstable.dispersion).isGreaterThan(0.5)
        assertThat(EarlyPurchaseDetector.detect(unstable, start.plusDays(42), start.plusDays(46))).isNull()
    }

    @Test
    fun `周期が不安定でも、3日以内に買っていれば周期なしで知らせる`() {
        val unstable = CycleEstimator.estimate(listOf(start, start.plusDays(2), start.plusDays(12), start.plusDays(42)))!!
        assertThat(EarlyPurchaseDetector.detect(unstable, start.plusDays(42), start.plusDays(45)))
            .isEqualTo(EarlyPurchaseNotice(start.plusDays(42), typicalIntervalDays = null))
    }

    @Test
    fun `購入日が2日なら、4日たっていれば知らせない`() {
        val estimate = CycleEstimator.estimate(listOf(start, start.plusDays(10)))
        assertThat(EarlyPurchaseDetector.detect(estimate, start.plusDays(10), start.plusDays(14))).isNull()
    }

    @Test
    fun `周期が分からなくても、今日買っていれば周期なしで知らせる`() {
        assertThat(EarlyPurchaseDetector.detect(null, start, start))
            .isEqualTo(EarlyPurchaseNotice(start, typicalIntervalDays = null))
    }

    @Test
    fun `周期が分かれば、3日以内に買っていても経過率で判定する`() {
        val daily = CycleEstimator.estimate(listOf(start, start.plusDays(2), start.plusDays(4)))!!
        assertThat(EarlyPurchaseDetector.detect(daily, start.plusDays(4), start.plusDays(5))).isNull()
    }

    @Test
    fun `購入の記録がなければ知らせない`() {
        assertThat(EarlyPurchaseDetector.detect(null, null, start)).isNull()
    }
}
