package com.cheesecomer.cartmate.domain.repository

import com.cheesecomer.cartmate.domain.family.InviteCode
import com.cheesecomer.cartmate.domain.model.Account
import com.cheesecomer.cartmate.domain.model.HouseholdId
import com.cheesecomer.cartmate.domain.model.Invite
import com.cheesecomer.cartmate.domain.model.Member
import com.cheesecomer.cartmate.domain.model.UserId
import kotlinx.coroutines.flow.Flow

/** 家族の作成・参加・離脱と招待（docs/requirements.md §3.5、docs/design/security.md） */
interface HouseholdRepository : MemberDirectory {
    /** 自分の所属。ログインしていなければ流さない */
    fun observeMembership(): Flow<Membership>

    /** 今の家族のメンバー。家族に参加していなければ空 */
    override fun observeMembers(): Flow<List<Member>>

    override fun observeAliases(): Flow<Map<UserId, String>>

    /** 自分の表示名を設定する。null で解除。オフラインでも行える（docs/requirements.md §3.5「表示名と呼び名」） */
    suspend fun setNickname(nickname: String?)

    /** [userId] に自分だけの呼び名を付ける。null で解除。オフラインでも行える */
    suspend fun setAlias(userId: UserId, alias: String?)

    /** 今の家族の招待（期限切れ・使用済みも含む。表示するかどうかは呼び出し側で判定する） */
    fun observeInvites(): Flow<List<Invite>>

    /** 家族を作成し、[account] をオーナーにする */
    suspend fun createHousehold(account: Account): HouseholdId

    /** 今の家族への招待を発行する。[inviterName] は確認画面に出す発行した人の名前 */
    suspend fun createInvite(inviterName: String): Invite

    suspend fun revokeInvite(code: InviteCode)

    /** 招待コードで招待を引く。見つからなければ null（存在しない・取り消された） */
    suspend fun findInvite(code: InviteCode): Invite?

    /** [invite] の家族に参加する。家族に参加していない人だけが使う */
    suspend fun join(invite: Invite, account: Account): JoinResult

    /**
     * 自分しかいない家族 [from] から、[invite] の家族に合流する。合流先に参加し、リスト・項目・商品を写してから、
     * 所属を切り替えて [from] を抜ける。[onProgress] には写した件数と全体の件数を渡す
     */
    suspend fun merge(invite: Invite, account: Account, from: HouseholdId, onProgress: (done: Int, total: Int) -> Unit): JoinResult

    /** 途中で止まった合流があれば、続きから写して終える。起動時に呼ぶ */
    suspend fun resumePendingMerge()

    /** 今の家族から抜ける。オーナーなら、参加がいちばん早いメンバーに引き継ぐ（domain の OwnerSuccession） */
    suspend fun leave()

    /** オーナーが [userId] を家族から外す */
    suspend fun removeMember(userId: UserId)

    /** 家族から外されたあとに、自分の所属の記録（users/{uid}.householdId）を消す */
    suspend fun clearRemovedMembership()
}

/**
 * 自分の所属。[householdId] は users/{uid}.householdId、[isMember] はその家族に自分の members があるか
 * （家族に参加していないか、まだ読めていなければ null）
 */
data class Membership(val householdId: HouseholdId?, val isMember: Boolean?)

sealed interface JoinResult {
    data object Joined : JoinResult
    /** 参加の直前に、期限切れ・使用済みになった（2人が同時に同じコードで参加した場合など） */
    data object InviteUnavailable : JoinResult
    /** 通信エラーなど */
    data class Failed(val cause: Throwable) : JoinResult
}
