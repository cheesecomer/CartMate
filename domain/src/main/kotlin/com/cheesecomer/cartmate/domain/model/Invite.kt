package com.cheesecomer.cartmate.domain.model

import com.cheesecomer.cartmate.domain.family.InviteCode
import java.time.Instant

/**
 * 家族への招待（docs/design/security.md「招待」）。24時間有効で、1回だけ使える。
 * 取り消すとドキュメントごと消えるので、取り消された招待は見つからない招待と同じ扱いになる
 */
data class Invite(
    val code: InviteCode,
    val householdId: HouseholdId,
    val createdBy: UserId,
    /** 発行した人の表示名。参加前の人は members を読めないため、確認画面の「○○さんの家族」はここから表示する */
    val inviterName: String,
    val createdAt: Instant,
    val expiresAt: Instant,
    val maxUses: Int,
    val usedCount: Int,
)
