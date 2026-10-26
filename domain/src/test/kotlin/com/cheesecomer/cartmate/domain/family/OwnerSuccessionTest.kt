package com.cheesecomer.cartmate.domain.family

import com.cheesecomer.cartmate.domain.model.Member
import com.cheesecomer.cartmate.domain.model.MemberRole
import com.cheesecomer.cartmate.domain.model.UserId
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class OwnerSuccessionTest {

    private fun member(id: String, role: MemberRole, joinedAt: String) =
        Member(UserId(id), role, displayName = id, photoUrl = null, joinedAt = Instant.parse(joinedAt))

    private val taro = member("taro", MemberRole.OWNER, "2026-10-01T00:00:00Z")
    private val hanako = member("hanako", MemberRole.MEMBER, "2026-10-02T00:00:00Z")
    private val kenta = member("kenta", MemberRole.MEMBER, "2026-10-03T00:00:00Z")

    @Test
    fun `オーナーが抜けると参加がいちばん早いメンバーが引き継ぐ`() {
        assertThat(OwnerSuccession.successor(listOf(taro, kenta, hanako), leaving = taro.userId))
            .isEqualTo(hanako.userId)
    }

    @Test
    fun `参加日時が同じならユーザーIDの順で決める`() {
        val a = member("a", MemberRole.MEMBER, "2026-10-02T00:00:00Z")
        val b = member("b", MemberRole.MEMBER, "2026-10-02T00:00:00Z")
        assertThat(OwnerSuccession.successor(listOf(taro, b, a), leaving = taro.userId)).isEqualTo(a.userId)
    }

    @Test
    fun `オーナー以外が抜けても引き継がない`() {
        assertThat(OwnerSuccession.successor(listOf(taro, hanako, kenta), leaving = hanako.userId)).isNull()
    }

    @Test
    fun `ほかにメンバーがいなければ引き継がない`() {
        assertThat(OwnerSuccession.successor(listOf(taro), leaving = taro.userId)).isNull()
    }

    @Test
    fun `抜ける人がメンバーにいなければ引き継がない`() {
        assertThat(OwnerSuccession.successor(listOf(hanako, kenta), leaving = taro.userId)).isNull()
    }
}
