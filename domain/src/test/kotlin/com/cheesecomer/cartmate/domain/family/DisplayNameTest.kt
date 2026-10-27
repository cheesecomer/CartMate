package com.cheesecomer.cartmate.domain.family

import com.cheesecomer.cartmate.domain.model.Member
import com.cheesecomer.cartmate.domain.model.MemberRole
import com.cheesecomer.cartmate.domain.model.UserId
import com.cheesecomer.cartmate.domain.notice.MemberNames
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class DisplayNameTest {

    private val hanako = UserId("hanako")

    private fun member(nickname: String? = null) =
        Member(hanako, MemberRole.MEMBER, "山田 花子", photoUrl = null, Instant.EPOCH, nickname = nickname)

    @Test
    fun `呼び名があれば呼び名`() {
        assertThat(DisplayName.of(member(nickname = "花子"), alias = "ママ")).isEqualTo("ママ")
    }

    @Test
    fun `呼び名がなければ本人の表示名`() {
        assertThat(DisplayName.of(member(nickname = "花子"), alias = null)).isEqualTo("花子")
    }

    @Test
    fun `どちらもなければ Google アカウントの名前`() {
        assertThat(DisplayName.of(member(), alias = null)).isEqualTo("山田 花子")
    }

    @Test
    fun `空白だけの値は設定していないものとして扱う`() {
        assertThat(DisplayName.of(member(nickname = " "), alias = "")).isEqualTo("山田 花子")
    }

    @Test
    fun `抜けたメンバーは呼び名があれば呼び名で、なければ null`() {
        assertThat(DisplayName.of(null, alias = "ママ")).isEqualTo("ママ")
        assertThat(DisplayName.of(null, alias = null)).isNull()
    }

    @Test
    fun `呼び名は付けた人の MemberNames にだけ反映される`() {
        val members = listOf(member(nickname = "花子"))
        assertThat(MemberNames(members, aliases = mapOf(hanako to "ママ")).of(hanako)).isEqualTo("ママ")
        assertThat(MemberNames(members, aliases = mapOf(hanako to "お母さん")).of(hanako)).isEqualTo("お母さん")
        assertThat(MemberNames(members).of(hanako)).isEqualTo("花子")
    }

    @Test
    fun `入力は前後の空白を除き、空なら解除`() {
        assertThat(DisplayName.normalize("  ママ ")).isEqualTo("ママ")
        assertThat(DisplayName.normalize("   ")).isNull()
    }

    @Test
    fun `20文字までなら保存できる`() {
        assertThat(DisplayName.isValid("あ".repeat(20))).isTrue()
        assertThat(DisplayName.isValid(" " + "あ".repeat(20) + " ")).isTrue()
        assertThat(DisplayName.isValid("あ".repeat(21))).isFalse()
    }
}
