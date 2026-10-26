package com.cheesecomer.cartmate.data.firestore

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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val clock: Clock,
) : HouseholdRepository {

    private val random = SecureRandom()

    private val uid: Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    private fun requireUid(): String = checkNotNull(auth.currentUser?.uid) { "ログインしていません" }

    private val householdId: Flow<HouseholdId?> = uid.flatMapLatest { uid ->
        if (uid == null) flowOf(null)
        else Paths.user(db, uid).snapshotFlow().map { it.getString(F.HOUSEHOLD_ID)?.let(::HouseholdId) }
    }.distinctUntilChanged()

    override fun observeMembership(): Flow<Membership> = uid.flatMapLatest { uid ->
        if (uid == null) return@flatMapLatest emptyFlow()
        Paths.user(db, uid).snapshotFlow()
            .map { it.getString(F.HOUSEHOLD_ID)?.let(::HouseholdId) }
            .distinctUntilChanged()
            .flatMapLatest { householdId ->
                if (householdId == null) flowOf(Membership(null, null))
                else Paths.member(db, householdId, uid).snapshotFlow()
                    .map { Membership(householdId, it.exists()) }
                    // 外されると自分の members を読めなくなる（ルールの isMember）
                    .catch { if (it.isPermissionDenied) emit(Membership(householdId, false)) else throw it }
            }
    }

    override fun observeMembers(): Flow<List<Member>> = householdId.flatMapLatest { householdId ->
        if (householdId == null) flowOf(emptyList())
        else Paths.members(db, householdId).snapshotFlow()
            .map { snapshot -> snapshot.documents.mapNotNull { it.toMember() }.sortedBy { it.joinedAt } }
            .catch { if (it.isPermissionDenied) emit(emptyList()) else throw it }
    }

    override fun observeInvites(): Flow<List<Invite>> = householdId.flatMapLatest { householdId ->
        if (householdId == null) flowOf(emptyList())
        else Paths.invites(db).whereEqualTo(F.HOUSEHOLD_ID, householdId.value).snapshotFlow()
            .map { snapshot -> snapshot.documents.mapNotNull { it.toInvite() }.sortedBy { it.createdAt } }
            .catch { if (it.isPermissionDenied) emit(emptyList()) else throw it }
    }

    override suspend fun createHousehold(account: Account): HouseholdId {
        val uid = account.userId.value
        val household = Paths.households(db).document()
        val householdId = HouseholdId(household.id)
        db.batch()
            .set(household, mapOf(
                F.NAME to "${account.displayName}さんの家族",
                F.CREATED_BY to uid,
                F.CREATED_AT to FieldValue.serverTimestamp(),
            ))
            .set(Paths.member(db, householdId, uid), memberData(account, MemberRole.OWNER, joinedVia = null))
            .set(Paths.user(db, uid), userData(householdId))
            .commit().await()
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
        db.batch()
            .set(Paths.member(db, invite.householdId, uid), memberData(account, MemberRole.MEMBER, joinedVia = invite.code))
            .update(Paths.invite(db, invite.code), F.USED_COUNT, FieldValue.increment(1))
            .set(Paths.user(db, uid), userData(invite.householdId))
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
    ): JoinResult = throw UnsupportedOperationException("合流は、リストと項目の Firestore 実装のあとで実装する")

    override suspend fun leave() {
        val uid = requireUid()
        val householdId = householdId.first() ?: return
        val members = Paths.members(db, householdId).get().await().documents.mapNotNull { it.toMember() }
        val successor = OwnerSuccession.successor(members, leaving = UserId(uid))
        db.batch().apply {
            // ルールは、オーナーが自分の members を消すのと同じバッチでだけ、引き継ぎを認める
            successor?.let { update(Paths.member(db, householdId, it.value), F.ROLE, ROLE_OWNER) }
            delete(Paths.member(db, householdId, uid))
            set(Paths.user(db, uid), userData(null))
        }.commit().await()
    }

    override suspend fun removeMember(userId: UserId) {
        val householdId = checkNotNull(householdId.first()) { "家族に参加していません" }
        Paths.member(db, householdId, userId.value).delete().await()
    }

    override suspend fun clearRemovedMembership() {
        Paths.user(db, requireUid()).set(userData(null)).await()
    }

    private fun memberData(account: Account, role: MemberRole, joinedVia: InviteCode?) = mapOf(
        F.ROLE to if (role == MemberRole.OWNER) ROLE_OWNER else ROLE_MEMBER,
        F.DISPLAY_NAME to account.displayName,
        F.PHOTO_URL to account.photoUrl,
        F.JOINED_AT to FieldValue.serverTimestamp(),
        F.JOINED_VIA to joinedVia?.value,
    )

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
    }
}
