package com.cheesecomer.cartmate.data.firestore

import com.cheesecomer.cartmate.domain.repository.HouseholdRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * アカウントの削除（docs/design/security.md「アカウントの削除」）。Cloud Functions は使わず、アプリから削除する。
 * どの手順も、すでに消えていれば何もしないので、途中で失敗してももう一度実行すれば続きから削除できる
 */
@Singleton
class AccountDeletion @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val householdRepository: HouseholdRepository,
) {
    suspend fun delete(idToken: String) {
        val user = checkNotNull(auth.currentUser) { "ログインしていません" }
        // Firebase Auth の削除には直近のログインが必要
        user.reauthenticate(GoogleAuthProvider.getCredential(idToken, null)).await()
        val uid = user.uid

        val membership = householdRepository.observeMembership().first()
        val householdId = membership.householdId
        // 家族から外されていれば、元の家族のデータには触れられない
        if (householdId != null && membership.isMember == true) {
            deleteAll(Paths.purchases(db, householdId).whereEqualTo(F.VISIBILITY, V.PRIVATE).whereEqualTo(F.PURCHASED_BY, uid))
            deleteAll(Paths.items(db, householdId).whereEqualTo(F.VISIBILITY, V.PRIVATE).whereEqualTo(F.CREATED_BY, uid))
            deleteAll(Paths.lists(db, householdId).whereEqualTo(F.VISIBILITY, V.PRIVATE).whereEqualTo(F.CREATED_BY, uid))
            householdRepository.leave()
        }
        Paths.user(db, uid).delete().await()
        user.delete().await()
    }

    private suspend fun deleteAll(query: Query) {
        for (chunk in query.get(Source.SERVER).await().documents.chunked(BATCH_SIZE)) {
            db.batch().apply { chunk.forEach { delete(it.reference) } }.commit().await()
        }
    }

    private companion object {
        /** 1回の書き込みの上限（500件）とルールの get の上限に余裕を持たせる */
        const val BATCH_SIZE = 15
    }
}
