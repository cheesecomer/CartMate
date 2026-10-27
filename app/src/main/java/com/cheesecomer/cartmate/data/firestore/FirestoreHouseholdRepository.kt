package com.cheesecomer.cartmate.data.firestore

import android.util.Log
import com.cheesecomer.cartmate.domain.family.InviteCode
import com.cheesecomer.cartmate.domain.family.OwnerSuccession
import com.cheesecomer.cartmate.domain.model.Account
import com.cheesecomer.cartmate.domain.model.HouseholdId
import com.cheesecomer.cartmate.domain.model.Invite
import com.cheesecomer.cartmate.domain.model.Member
import com.cheesecomer.cartmate.domain.model.MemberRole
import com.cheesecomer.cartmate.domain.model.UserId
import com.cheesecomer.cartmate.domain.repository.HouseholdRepository
import com.cheesecomer.cartmate.domain.repository.JoinResult
import com.cheesecomer.cartmate.domain.repository.Membership
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class FirestoreHouseholdRepository @Inject constructor(
    private val session: FirestoreSession,
    private val db: FirebaseFirestore,
    private val merger: FamilyMerger,
    private val clock: Clock,
) : HouseholdRepository {

    private val random = SecureRandom()

    private val householdId: Flow<HouseholdId?> get() = session.householdId

    private fun requireUid(): String = session.requireUid()

    private suspend fun awaitPendingWrites() {
        db.waitForPendingWrites().await()
    }

    private fun logListenError(error: Throwable) {
        Log.w(TAG, "listen failed", error)
    }

    override fun observeMembership(): Flow<Membership> = session.uid.flatMapLatest { uid ->
        if (uid == null) return@flatMapLatest emptyFlow()
        session.householdId
            .flatMapLatest { householdId ->
                if (householdId == null) flowOf(Membership(null, null))
                else memberPresence(
                    listen = {
                        Paths.member(db, householdId, uid).snapshotFlow()
                            // まだ分からない（キャッシュにないだけ）間は流さない
                            .mapNotNull { documentPresence(it.exists(), it.metadata.isFromCache) }
                    },
                    awaitPendingWrites = ::awaitPendingWrites,
                    onError = ::logListenError,
                ).map { Membership(householdId, it) }
            }
    }

    override fun observeMembers(): Flow<List<Member>> = householdId.flatMapLatest { householdId ->
        if (householdId == null) flowOf(emptyList())
        else recoverListen(
            listen = {
                Paths.members(db, householdId).snapshotFlow()
                    .map { snapshot -> snapshot.documents.mapNotNull { it.toMember() }.sortedBy { it.joinedAt } }
            },
            denied = emptyList(),
            awaitPendingWrites = ::awaitPendingWrites,
            onError = ::logListenError,
        )
    }

    override fun observeAliases(): Flow<Map<UserId, String>> = session.uid.flatMapLatest { uid ->
        if (uid == null) flowOf(emptyMap())
        else recoverListen(
            listen = {
                Paths.user(db, uid).snapshotFlow().map { snapshot ->
                    (snapshot.get(F.MEMBER_ALIASES) as? Map<*, *>).orEmpty()
                        .mapNotNull { (key, value) -> if (key is String && value is String) UserId(key) to value else null }
                        .toMap()
                }
            },
            denied = emptyMap(),
            awaitPendingWrites = ::awaitPendingWrites,
            onError = ::logListenError,
        )
    }

    /** 本人だけが読める users と、家族全員が読める members の両方に書く。オフラインでも行えるよう、完了を待たない */
    override suspend fun setNickname(nickname: String?) {
        val uid = requireUid()
        val householdId = checkNotNull(householdId.first()) { "家族に参加していません" }
        val value: Any = nickname ?: FieldValue.delete()
        db.batch()
            .set(Paths.user(db, uid), mapOf(F.NICKNAME to value), SetOptions.merge())
            .update(Paths.member(db, householdId, uid), F.NICKNAME, value)
            .commit().addOnFailureListener { Log.w(TAG, "setNickname failed", it) }
    }

    override suspend fun setAlias(userId: UserId, alias: String?) {
        val uid = requireUid()
        Paths.user(db, uid)
            .set(mapOf(F.MEMBER_ALIASES to mapOf(userId.value to (alias ?: FieldValue.delete()))), SetOptions.merge())
            .addOnFailureListener { Log.w(TAG, "setAlias failed", it) }
    }

    override fun observeInvites(): Flow<List<Invite>> = householdId.flatMapLatest { householdId ->
        if (householdId == null) flowOf(emptyList())
        else recoverListen(
            listen = {
                Paths.invites(db).whereEqualTo(F.HOUSEHOLD_ID, householdId.value).snapshotFlow()
                    .map { snapshot -> snapshot.documents.mapNotNull { it.toInvite() }.sortedBy { it.createdAt } }
            },
            denied = emptyList(),
            awaitPendingWrites = ::awaitPendingWrites,
            onError = ::logListenError,
        )
    }

    override suspend fun createHousehold(account: Account): HouseholdId {
        val uid = account.userId.value
        val household = Paths.households(db).document()
        val householdId = HouseholdId(household.id)
        db.batch()
            .set(household, mapOf(
                // 画面には表示しない。ルールの長さの上限（40文字）に収まるよう、表示名を切り詰める
                F.NAME to "${account.displayName.take(MAX_NAME_IN_HOUSEHOLD)}さんの家族",
                F.CREATED_BY to uid,
                F.CREATED_AT to FieldValue.serverTimestamp(),
            ))
            .set(Paths.member(db, householdId, uid), memberData(account, MemberRole.OWNER, joinedVia = null, ownNickname(uid, Source.CACHE)))
            .set(Paths.user(db, uid), userData(householdId), SetOptions.merge())
            // オフラインでも作成できるよう、書き込みの完了を待たない（docs/requirements.md §3.3）
            .commit().addOnFailureListener { Log.w(TAG, "createHousehold failed", it) }
        return householdId
    }

    override suspend fun createInvite(inviterName: String): Invite {
        val uid = requireUid()
        val householdId = checkNotNull(householdId.first()) { "家族に参加していません" }
        val now = clock.instant()
        val invite = Invite(
            code = InviteCode.generate(random),
            householdId = householdId,
            createdBy = UserId(uid),
            inviterName = inviterName,
            createdAt = now,
            // ルールはサーバーの時刻から24時間以内を求めるので、端末の時計のずれを見込んで少し短くする
            expiresAt = now + INVITE_LIFETIME - CLOCK_SKEW_MARGIN,
            maxUses = 1,
            usedCount = 0,
        )
        Paths.invite(db, invite.code).set(mapOf(
            F.HOUSEHOLD_ID to householdId.value,
            F.CREATED_BY to uid,
            F.INVITER_NAME to inviterName,
            F.CREATED_AT to FieldValue.serverTimestamp(),
            F.EXPIRES_AT to Timestamp(Date.from(invite.expiresAt)),
            F.MAX_USES to invite.maxUses,
            F.USED_COUNT to invite.usedCount,
        )).await()
        return invite
    }

    override suspend fun revokeInvite(code: InviteCode) {
        Paths.invite(db, code).delete().await()
    }

    override suspend fun findInvite(code: InviteCode): Invite? =
        Paths.invite(db, code).get().await().takeIf { it.exists() }?.toInvite()

    override suspend fun join(invite: Invite, account: Account): JoinResult = runCatching {
        val uid = account.userId.value
        val nickname = ownNickname(uid, Source.DEFAULT)
        db.batch()
            .set(Paths.member(db, invite.householdId, uid), memberData(account, MemberRole.MEMBER, joinedVia = invite.code, nickname))
            .update(Paths.invite(db, invite.code), F.USED_COUNT, FieldValue.increment(1))
            .set(Paths.user(db, uid), userData(invite.householdId), SetOptions.merge())
            .commit().await()
    }.fold(
        onSuccess = { JoinResult.Joined },
        // ルールは、期限内で未使用の招待でだけ参加を認める
        onFailure = { if (it.isPermissionDenied) JoinResult.InviteUnavailable else JoinResult.Failed(it) },
    )

    override suspend fun merge(
        invite: Invite,
        account: Account,
        from: HouseholdId,
        onProgress: (done: Int, total: Int) -> Unit,
    ): JoinResult {
        val uid = account.userId.value
        // 合流先に参加する。所属（users）は写し終えてから切り替える（途中で画面が切り替わらないように）
        val joined = runCatching {
            val nickname = ownNickname(uid, Source.DEFAULT)
            db.batch()
                .set(Paths.member(db, invite.householdId, uid), memberData(account, MemberRole.MEMBER, joinedVia = invite.code, nickname))
                .update(Paths.invite(db, invite.code), F.USED_COUNT, FieldValue.increment(1))
                .commit().await()
        }
        joined.exceptionOrNull()?.let { return if (it.isPermissionDenied) JoinResult.InviteUnavailable else JoinResult.Failed(it) }
        val pending = FamilyMerger.Pending(uid, from, invite.householdId)
        merger.start(pending)
        return runCatching { merger.resume(pending, onProgress) }
            .fold(onSuccess = { JoinResult.Joined }, onFailure = { JoinResult.Failed(it) })
    }

    override suspend fun resumePendingMerge() {
        val pending = merger.pending(session.uid.first() ?: return) ?: return
        merger.resume(pending) { _, _ -> }
    }

    override suspend fun leave() {
        val uid = requireUid()
        val householdId = householdId.first() ?: return
        val members = Paths.members(db, householdId).get().await().documents.mapNotNull { it.toMember() }
        val successor = OwnerSuccession.successor(members, leaving = UserId(uid))
        db.batch().apply {
            // ルールは、オーナーが自分の members を消すのと同じバッチでだけ、引き継ぎを認める
            successor?.let { update(Paths.member(db, householdId, it.value), F.ROLE, ROLE_OWNER) }
            delete(Paths.member(db, householdId, uid))
            set(Paths.user(db, uid), userData(null), SetOptions.merge())
        }.commit().await()
    }

    override suspend fun removeMember(userId: UserId) {
        val householdId = checkNotNull(householdId.first()) { "家族に参加していません" }
        Paths.member(db, householdId, userId.value).delete().await()
    }

    override suspend fun clearRemovedMembership() {
        Paths.user(db, requireUid()).set(userData(null), SetOptions.merge()).await()
    }

    private fun memberData(account: Account, role: MemberRole, joinedVia: InviteCode?, nickname: String?) = mapOf(
        F.ROLE to if (role == MemberRole.OWNER) ROLE_OWNER else ROLE_MEMBER,
        F.DISPLAY_NAME to account.displayName,
        F.PHOTO_URL to account.photoUrl,
        F.JOINED_AT to FieldValue.serverTimestamp(),
        F.JOINED_VIA to joinedVia?.value,
    ) + listOfNotNull(nickname?.let { F.NICKNAME to it })

    /**
     * 自分の表示名。家族を移っても引き継げるよう、本人だけが読める users にも置いている（docs/design/data-model.md）。
     * 家族の作成はオフラインでもできるので、端末のキャッシュから読む
     */
    private suspend fun ownNickname(uid: String, source: Source): String? =
        runCatching { Paths.user(db, uid).get(source).await().getString(F.NICKNAME) }.getOrNull()

    /** users は丸ごと上書きせず、マージで書く（表示名と呼び名を消さないため） */
    private fun userData(householdId: HouseholdId?) = mapOf(
        F.HOUSEHOLD_ID to householdId?.value,
        F.UPDATED_AT to FieldValue.serverTimestamp(),
    )

    private fun DocumentSnapshot.toMember(): Member? {
        // サーバーの時刻が確定する前（自分の書き込みの直後）は、見積もりの時刻を使う
        val joinedAt = getTimestamp(F.JOINED_AT, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) ?: return null
        return Member(
            userId = UserId(id),
            role = if (getString(F.ROLE) == ROLE_OWNER) MemberRole.OWNER else MemberRole.MEMBER,
            displayName = getString(F.DISPLAY_NAME).orEmpty(),
            photoUrl = getString(F.PHOTO_URL),
            joinedAt = joinedAt.toDate().toInstant(),
            nickname = getString(F.NICKNAME),
        )
    }

    private fun DocumentSnapshot.toInvite(): Invite? {
        val code = InviteCode.parse(id) ?: return null
        return Invite(
            code = code,
            householdId = HouseholdId(getString(F.HOUSEHOLD_ID) ?: return null),
            createdBy = UserId(getString(F.CREATED_BY) ?: return null),
            inviterName = getString(F.INVITER_NAME).orEmpty(),
            createdAt = (getTimestamp(F.CREATED_AT, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) ?: return null)
                .toDate().toInstant(),
            expiresAt = (getTimestamp(F.EXPIRES_AT) ?: return null).toDate().toInstant(),
            maxUses = getLong(F.MAX_USES)?.toInt() ?: 1,
            usedCount = getLong(F.USED_COUNT)?.toInt() ?: 0,
        )
    }

    private companion object {
        const val ROLE_OWNER = "owner"
        const val ROLE_MEMBER = "member"
        val INVITE_LIFETIME: Duration = Duration.ofHours(24)
        val CLOCK_SKEW_MARGIN: Duration = Duration.ofMinutes(10)
        const val MAX_NAME_IN_HOUSEHOLD = 30
        const val TAG = "FirestoreHouseholdRepository"
    }
}
