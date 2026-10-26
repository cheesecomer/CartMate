package com.cheesecomer.cartmate.domain.family

import com.cheesecomer.cartmate.domain.model.HouseholdId
import com.cheesecomer.cartmate.domain.model.Member
import com.cheesecomer.cartmate.domain.model.MemberRole
import com.cheesecomer.cartmate.domain.model.UserId
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class JoinPolicyTest {

    private val me = UserId("taro")
    private val mine = HouseholdId("mine")
    private val target = HouseholdId("hanako-family")

    private fun member(id: UserId, role: MemberRole = MemberRole.MEMBER) =
        Member(id, role, displayName = id.value, photoUrl = null, joinedAt = Instant.EPOCH)

    @Test
    fun `家族に参加していなければ参加する`() {
        assertThat(JoinPolicy.decide(me, current = null, currentMembers = emptyList(), target = target))
            .isEqualTo(JoinMode.Join)
    }

    @Test
    fun `自分しかいない家族からは合流する`() {
        assertThat(JoinPolicy.decide(me, mine, listOf(member(me, MemberRole.OWNER)), target))
            .isEqualTo(JoinMode.Merge(from = mine))
    }

    @Test
    fun `ほかのメンバーがいる家族からは参加できない`() {
        val members = listOf(member(me, MemberRole.OWNER), member(UserId("kenta")))
        assertThat(JoinPolicy.decide(me, mine, members, target)).isEqualTo(JoinMode.AlreadyInAnotherFamily)
    }

    @Test
    fun `招待先がすでに参加している家族なら何もしない`() {
        val members = listOf(member(me), member(UserId("hanako"), MemberRole.OWNER))
        assertThat(JoinPolicy.decide(me, target, members, target)).isEqualTo(JoinMode.AlreadyInThisFamily)
    }
}
