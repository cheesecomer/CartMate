package com.cheesecomer.cartmate.domain.family

import com.cheesecomer.cartmate.domain.model.Invite
import java.time.Instant

/** 招待コードが使えるか（docs/requirements.md §3.5「参加の確認」） */
object InviteValidation {

    /** [invite] は招待コードで引いた招待。見つからなければ null（存在しない・取り消された） */
    fun check(invite: Invite?, now: Instant): InviteCheck = when {
        invite == null -> InviteCheck.NotFound
        // 使用済みを先に判定する。期限が切れたあとで使用済みと分かっても、使われたことを伝えるほうが正確なため
        invite.usedCount >= invite.maxUses -> InviteCheck.Used
        !now.isBefore(invite.expiresAt) -> InviteCheck.Expired
        else -> InviteCheck.Valid(invite)
    }
}

sealed interface InviteCheck {
    data class Valid(val invite: Invite) : InviteCheck
    /** 「招待コードが見つかりません」 */
    data object NotFound : InviteCheck
    /** 「招待コードの有効期限が切れています」 */
    data object Expired : InviteCheck
    /** 「この招待コードはすでに使われています」 */
    data object Used : InviteCheck
}
