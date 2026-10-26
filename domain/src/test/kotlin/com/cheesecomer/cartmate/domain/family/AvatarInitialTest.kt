package com.cheesecomer.cartmate.domain.family

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class AvatarInitialTest {

    @Test
    fun `名前の最初の1文字`() {
        assertThat(AvatarInitial.of("山田 太郎")).isEqualTo("山")
        assertThat(AvatarInitial.of("  健太")).isEqualTo("健")
    }

    @Test
    fun `英字は大文字にする`() {
        assertThat(AvatarInitial.of("taro")).isEqualTo("T")
    }

    @Test
    fun `サロゲートペアの文字も1文字として扱う`() {
        assertThat(AvatarInitial.of("𠮷田")).isEqualTo("𠮷")
    }

    @Test
    fun `名前が空なら null`() {
        assertThat(AvatarInitial.of("")).isNull()
        assertThat(AvatarInitial.of("  ")).isNull()
    }
}
