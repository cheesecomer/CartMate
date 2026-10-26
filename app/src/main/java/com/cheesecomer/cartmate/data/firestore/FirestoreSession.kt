package com.cheesecomer.cartmate.data.firestore

import android.util.Log
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
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn

/**
 * ログイン中のユーザーと、所属する家族（users/{uid}.householdId）。
 * アプリの中で1つの購読を共有し、最新の値を持っておく。操作のたびに購読を作って最初の値を待たないようにするため
 * （書き込みの前に待たないことで、オフラインでも操作がすぐ画面に反映される。docs/design/sync.md「オフラインの書き込み」）
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class FirestoreSession @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
) {
    private val sharing = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val uid: Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged().shareIn(sharing, SharingStarted.Eagerly, replay = 1)

    val householdId: Flow<HouseholdId?> = uid.flatMapLatest { uid ->
        if (uid == null) flowOf(null)
        // 自分の users は拒否されないので、denied は流れない。通信の問題などでは購読し直す
        else recoverListen(
            listen = { Paths.user(db, uid).snapshotFlow().map { it.getString(F.HOUSEHOLD_ID)?.let(::HouseholdId) } },
            denied = null,
            awaitPendingWrites = { db.waitForPendingWrites().await() },
            onError = { Log.w("FirestoreSession", "listen failed", it) },
        )
    }.distinctUntilChanged().shareIn(sharing, SharingStarted.Eagerly, replay = 1)

    /** ログイン中のユーザーと所属する家族。どちらかがなければ流さない */
    val scope: Flow<Scope?> = uid.flatMapLatest { uid ->
        if (uid == null) flowOf(null)
        else householdId.map { householdId -> householdId?.let { Scope(uid, it) } }
    }.distinctUntilChanged().shareIn(sharing, SharingStarted.Eagerly, replay = 1)

    fun requireUid(): String = checkNotNull(auth.currentUser?.uid) { "ログインしていません" }

    suspend fun requireScope(): Scope = checkNotNull(scope.first()) { "家族に参加していません" }

    data class Scope(val uid: String, val householdId: HouseholdId)
}
