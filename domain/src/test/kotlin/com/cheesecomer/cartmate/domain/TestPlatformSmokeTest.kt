package com.cheesecomer.cartmate.domain

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

// テスト基盤（JUnit Platform + Truth）が動くことの確認用。domain に実際のテストができたら削除する
class TestPlatformSmokeTest {
    @Test
    fun `JUnit Platform で実行される`() {
        assertThat(listOf(1, 2, 3)).containsExactly(1, 2, 3).inOrder()
    }
}
