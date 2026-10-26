package com.cheesecomer.cartmate.domain.list

import com.cheesecomer.cartmate.domain.list.ListProgress.Status
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ListProgressTest {

    @Test
    fun `未購入があれば「のこり」で、割合は完了の件数から求める`() {
        val progress = ListProgress(openCount = 3, completedCount = 1, hasPurchases = true)
        assertThat(progress.status).isEqualTo(Status.REMAINING)
        assertThat(progress.fraction).isEqualTo(0.25f)
    }

    @Test
    fun `未購入があり完了がなければ割合は0`() {
        assertThat(ListProgress(openCount = 2, completedCount = 0, hasPurchases = false).fraction).isEqualTo(0f)
    }

    @Test
    fun `未購入が0件で購入の記録もなければ「登録なし」で、バーは空`() {
        val progress = ListProgress(openCount = 0, completedCount = 0, hasPurchases = false)
        assertThat(progress.status).isEqualTo(Status.NOT_REGISTERED)
        assertThat(progress.fraction).isEqualTo(0f)
    }

    @Test
    fun `未購入が0件で購入の記録があれば「買い終わった」で、バーはいっぱい`() {
        // 完了から24時間が過ぎて完了の件数が0でも、買い終わった状態のまま
        val progress = ListProgress(openCount = 0, completedCount = 0, hasPurchases = true)
        assertThat(progress.status).isEqualTo(Status.DONE)
        assertThat(progress.fraction).isEqualTo(1f)
    }
}
