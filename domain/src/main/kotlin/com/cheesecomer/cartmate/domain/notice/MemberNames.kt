package com.cheesecomer.cartmate.domain.notice

import com.cheesecomer.cartmate.domain.family.DisplayName
import com.cheesecomer.cartmate.domain.model.Member
import com.cheesecomer.cartmate.domain.model.UserId

/**
 * チェックした人などの表示名（[DisplayName] の優先順位）。[aliases] は自分がほかのメンバーに付けた呼び名。
 * 家族のメンバーにいない人（抜けた・外された・アカウントを削除した）は、呼び名がなければ null を返し、
 * 画面では「退会したメンバー」と表示する（docs/requirements.md §3.5）
 */
class MemberNames(members: List<Member>, private val aliases: Map<UserId, String> = emptyMap()) {
    private val members = members.associateBy { it.userId }

    fun of(userId: UserId): String? = DisplayName.of(members[userId], aliases[userId])
}
