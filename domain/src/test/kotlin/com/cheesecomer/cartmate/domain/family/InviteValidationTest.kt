package com.cheesecomer.cartmate.domain.family

import com.cheesecomer.cartmate.domain.model.HouseholdId
import com.cheesecomer.cartmate.domain.model.Invite
import com.cheesecomer.cartmate.domain.model.UserId
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class InviteValidationTest {

    private val createdAt = Instant.parse("2026-10-26T00:00:00Z")
    private val invite = Invite(
        code = InviteCode.parse("7KQ2M9XPHA")!!,
        householdId = HouseholdId("h1"),
        createdBy = UserId("hanako"),
        inviterName = "山田 花子",
        createdAt = createdAt,
        expiresAt = createdAt + Duration.ofHours(24),
        maxUses = 1,
        usedCount = 0,
    )

    @Test
    fun `有効期限内で未使用なら使える`() {
        val now = createdAt + Duration.ofHours(23)
        assertThat(InviteValidation.check(invite, now)).isEqualTo(InviteCheck.Valid(invite))
    }

    @Test
    fun `見つからなければ NotFound`() {
        assertThat(InviteValidation.check(null, createdAt)).isEqualTo(InviteCheck.NotFound)
    }

    @Test
    fun `有効期限の時刻ちょうどからは期限切れ`() {
        assertThat(InviteValidation.check(invite, invite.expiresAt)).isEqualTo(InviteCheck.Expired)
        assertThat(InviteValidation.check(invite, invite.expiresAt.minusMillis(1))).isEqualTo(InviteCheck.Valid(invite))
    }

    @Test
    fun `使用回数に達していれば使用済み`() {
        val used = invite.copy(usedCount = 1)
        assertThat(InviteValidation.check(used, createdAt)).isEqualTo(InviteCheck.Used)
    }

    @Test
    fun `期限切れかつ使用済みなら使用済みとして伝える`() {
        val used = invite.copy(usedCount = 1)
        assertThat(InviteValidation.check(used, invite.expiresAt)).isEqualTo(InviteCheck.Used)
    }

    @Test
    fun `有効な招待だけを一覧に出す`() {
        val used = invite.copy(code = InviteCode.parse("AAAAAAAAAA")!!, usedCount = 1)
        val expired = invite.copy(code = InviteCode.parse("BBBBBBBBBB")!!, expiresAt = createdAt)
        assertThat(InviteValidation.active(listOf(invite, used, expired), createdAt + Duration.ofMinutes(1)))
            .containsExactly(invite)
    }

    @Test
    fun `残り時間は切り上げで数える`() {
        assertThat(InviteValidation.remainingHours(invite, createdAt)).isEqualTo(24)
        assertThat(InviteValidation.remainingHours(invite, createdAt + Duration.ofMinutes(61))).isEqualTo(23)
        assertThat(InviteValidation.remainingHours(invite, invite.expiresAt.minusSeconds(30 * 60))).isEqualTo(1)
    }

    @Test
    fun `期限を過ぎていれば残り時間は 0`() {
        assertThat(InviteValidation.remainingHours(invite, invite.expiresAt)).isEqualTo(0)
        assertThat(InviteValidation.remainingHours(invite, invite.expiresAt.plusSeconds(60))).isEqualTo(0)
    }
}
