package com.cheesecomer.cartmate.domain.family

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.SplittableRandom

class InviteCodeTest {

    @Test
    fun `生成したコードは読み間違えやすい文字を含まない10文字`() {
        val random = SplittableRandom(1)
        repeat(1000) {
            val code = InviteCode.generate(random).value
            assertThat(code).hasLength(10)
            assertThat(code).matches("[0-9A-HJKMNP-TV-Z]{10}")
        }
    }

    @Test
    fun `表示は4・4・2文字で区切る`() {
        assertThat(InviteCode.parse("7KQ2M9XPHA")!!.formatted()).isEqualTo("7KQ2-M9XP-HA")
    }

    @ParameterizedTest
    @ValueSource(strings = ["7KQ2M9XPHA", "7KQ2-M9XP-HA", "7kq2-m9xp-ha", " 7KQ2 M9XP HA "])
    fun `区切りと大文字・小文字を問わずに読む`(input: String) {
        assertThat(InviteCode.parse(input)!!.value).isEqualTo("7KQ2M9XPHA")
    }

    @Test
    fun `I と L は 1、O は 0 として読む`() {
        assertThat(InviteCode.parse("IL0O-ABCD-EF")!!.value).isEqualTo("1100ABCDEF")
        assertThat(InviteCode.parse("il0o-abcd-ef")!!.value).isEqualTo("1100ABCDEF")
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "7KQ2-M9XP-H", "7KQ2-M9XP-HAX", "7KQ2-M9XP-H!"])
    fun `10文字にならなければ読まない`(input: String) {
        assertThat(InviteCode.parse(input)).isNull()
    }

    @Test
    fun `入力途中の文字列は使える文字だけを大文字で最大10文字にする`() {
        assertThat(InviteCode.normalize("7kq2-m9")).isEqualTo("7KQ2M9")
        assertThat(InviteCode.normalize("7KQ2M9XPHA123")).isEqualTo("7KQ2M9XPHA")
        assertThat(InviteCode.normalize("U!?")).isEmpty()
    }

    @Test
    fun `入力途中の表示は4文字目と8文字目のあとで区切る`() {
        assertThat(InviteCode.formatPartial("")).isEmpty()
        assertThat(InviteCode.formatPartial("7KQ")).isEqualTo("7KQ")
        assertThat(InviteCode.formatPartial("7KQ2")).isEqualTo("7KQ2")
        assertThat(InviteCode.formatPartial("7KQ2M")).isEqualTo("7KQ2-M")
        assertThat(InviteCode.formatPartial("7KQ2M9XP")).isEqualTo("7KQ2-M9XP")
        assertThat(InviteCode.formatPartial("7KQ2M9XPH")).isEqualTo("7KQ2-M9XP-H")
        assertThat(InviteCode.formatPartial("7kq2-m9xp-ha")).isEqualTo("7KQ2-M9XP-HA")
    }
}
