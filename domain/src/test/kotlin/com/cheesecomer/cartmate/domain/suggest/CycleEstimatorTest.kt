package com.cheesecomer.cartmate.domain.suggest

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class CycleEstimatorTest {

    private val start = LocalDate.of(2026, 1, 1)

    /** [start] から [intervals] の日数ずつ進んだ購入日 */
    private fun dates(vararg intervals: Int): List<LocalDate> =
        intervals.runningFold(start) { date, days -> date.plusDays(days.toLong()) }

    @Test
    fun `購入日が2日なら推定しない`() {
        assertThat(CycleEstimator.estimate(dates(7))).isNull()
    }

    @Test
    fun `購入日が3日あれば推定する`() {
        val estimate = CycleEstimator.estimate(dates(7, 7))!!
        assertThat(estimate.medianIntervalDays).isEqualTo(7.0)
        assertThat(estimate.lastPurchase).isEqualTo(start.plusDays(14))
    }

    @Test
    fun `同じ日の購入は1回として数える`() {
        val sameDay = dates(7, 7) + start.plusDays(7)
        assertThat(CycleEstimator.estimate(sameDay)!!.eventDates).hasSize(3)
        // 同じ日が重なって購入日が2日なら推定しない
        assertThat(CycleEstimator.estimate(listOf(start, start, start.plusDays(7)))).isNull()
    }

    @Test
    fun `間隔が10個を超えたら直近10個だけを使う`() {
        // 最初の3つの間隔は100日、そのあとの10個は7日
        val estimate = CycleEstimator.estimate(dates(100, 100, 100, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7))!!
        assertThat(estimate.medianIntervalDays).isEqualTo(7.0)
    }

    @Test
    fun `間隔の数が偶数なら中央の2つの平均`() {
        assertThat(CycleEstimator.estimate(dates(6, 8, 10, 12))!!.medianIntervalDays).isEqualTo(9.0)
    }

    @Test
    fun `外れ値が1つあっても中央値は動かない`() {
        assertThat(CycleEstimator.estimate(dates(7, 7, 60, 7, 7))!!.medianIntervalDays).isEqualTo(7.0)
    }

    @Test
    fun `経過率は最後の購入からの日数を周期で割ったもの`() {
        val estimate = CycleEstimator.estimate(dates(10, 10))!!
        assertThat(CycleEstimator.elapsedRatio(estimate, start.plusDays(28))).isEqualTo(0.8)
    }

    @Test
    fun `間隔がばらつくほど不安定とみなす`() {
        assertThat(CycleEstimator.estimate(dates(7, 7, 7))!!.dispersion).isEqualTo(0.0)
        assertThat(CycleEstimator.estimate(dates(2, 10, 30))!!.dispersion).isGreaterThan(0.5)
    }
}
