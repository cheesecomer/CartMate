package com.cheesecomer.cartmate.domain.family

import com.cheesecomer.cartmate.domain.model.Member
import com.cheesecomer.cartmate.domain.model.MemberRole
import com.cheesecomer.cartmate.domain.model.UserId

/**
 * 家族から抜けるときのオーナーの引き継ぎ（docs/requirements.md §3.5「家族からの離脱」）。
 * オーナーが抜けると、残りのメンバーのうち参加がいちばん早い人がオーナーになる
 */
object OwnerSuccession {

    /**
     * [leaving] が抜けるときに、オーナーにする人。[leaving] がオーナーでない場合と、ほかにメンバーがいない場合は null。
     * 参加日時が同じなら、ユーザーIDの順で決める（どの端末で計算しても同じ人になるように）
     */
    fun successor(members: List<Member>, leaving: UserId): UserId? {
        val leavingMember = members.find { it.userId == leaving } ?: return null
        if (leavingMember.role != MemberRole.OWNER) return null
        return members
            .filter { it.userId != leaving }
            .minWithOrNull(compareBy<Member> { it.joinedAt }.thenBy { it.userId.value })
            ?.userId
    }
}
