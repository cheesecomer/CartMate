package com.cheesecomer.cartmate.domain.suggest

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class DueStatusTest {

    @Test
    fun `周期より前ならあと何日か`() {
        assertThat(DueStatus.of(intervalDays = 7, daysSinceLast = 6)).isEqualTo(DueStatus.Remaining(1))
    }

    @Test
    fun `周期と同じ日数なら今日が目安`() {
        assertThat(DueStatus.of(intervalDays = 4, daysSinceLast = 4)).isEqualTo(DueStatus.Today)
    }

    @Test
    fun `周期を超えたら何日過ぎたか`() {
        assertThat(DueStatus.of(intervalDays = 2, daysSinceLast = 5)).isEqualTo(DueStatus.Overdue(3))
    }

    @Test
    fun `四捨五入した周期の日数で決め、補足の数字と食い違わない`() {
        // 中央値 2.5 日は「約3日ごと」と出すので、2日前なら「あと1日」
        val estimate = CycleEstimate(emptyList(), medianIntervalDays = 2.5, dispersion = 0.0)
        assertThat(DueStatus.of(estimate.typicalIntervalDays, daysSinceLast = 2)).isEqualTo(DueStatus.Remaining(1))
    }
}
