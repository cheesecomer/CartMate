package com.cheesecomer.cartmate.domain.model

import java.time.Instant

@JvmInline
value class HouseholdId(val value: String)

/** 家族のメンバー（docs/requirements.md §3.5）。名前と写真は、参加したときの Google アカウントのもの */
data class Member(
    val userId: UserId,
    val role: MemberRole,
    val displayName: String,
    val photoUrl: String?,
    val joinedAt: Instant,
)

enum class MemberRole { OWNER, MEMBER }

/** ログイン中の Google アカウント */
data class Account(
    val userId: UserId,
    val displayName: String,
    val email: String,
    val photoUrl: String?,
)
