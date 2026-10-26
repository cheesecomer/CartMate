package com.cheesecomer.cartmate.domain.notice

import com.cheesecomer.cartmate.domain.model.Member
import com.cheesecomer.cartmate.domain.model.UserId

/**
 * チェックした人などの表示名。家族のメンバーにいない人（抜けた・外された・アカウントを削除した）は null を返し、
 * 画面では「退会したメンバー」と表示する（docs/requirements.md §3.5）
 */
class MemberNames(members: List<Member>) {
    private val names = members.associate { it.userId to it.displayName }

    fun of(userId: UserId): String? = names[userId]?.takeIf { it.isNotBlank() }
}
