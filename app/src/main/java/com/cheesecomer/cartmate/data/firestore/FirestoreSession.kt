package com.cheesecomer.cartmate.data.firestore

import com.cheesecomer.cartmate.domain.model.HouseholdId
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** ログイン中のユーザーと、所属する家族（users/{uid}.householdId） */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class FirestoreSession @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
) {
    val uid: Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    val householdId: Flow<HouseholdId?> = uid.flatMapLatest { uid ->
        if (uid == null) flowOf(null)
        else Paths.user(db, uid).snapshotFlow().map { it.getString(F.HOUSEHOLD_ID)?.let(::HouseholdId) }
    }.distinctUntilChanged()

    /** ログイン中のユーザーと所属する家族。どちらかがなければ流さない */
    val scope: Flow<Scope?> = uid.flatMapLatest { uid ->
        if (uid == null) flowOf(null)
        else householdId.map { householdId -> householdId?.let { Scope(uid, it) } }
    }.distinctUntilChanged()

    fun requireUid(): String = checkNotNull(auth.currentUser?.uid) { "ログインしていません" }

    suspend fun requireScope(): Scope = checkNotNull(scope.first()) { "家族に参加していません" }

    data class Scope(val uid: String, val householdId: HouseholdId)
}
