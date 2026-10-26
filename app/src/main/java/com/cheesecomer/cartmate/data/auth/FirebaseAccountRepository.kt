package com.cheesecomer.cartmate.data.auth

import com.cheesecomer.cartmate.data.firestore.AccountDeletion
import com.cheesecomer.cartmate.domain.model.Account
import com.cheesecomer.cartmate.domain.model.UserId
import com.cheesecomer.cartmate.domain.repository.AccountRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAccountRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val deletion: AccountDeletion,
) : AccountRepository {

    override fun observeAccount(): Flow<Account?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.toAccount()) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    override suspend fun signInWithGoogle(idToken: String) {
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
    }

    override suspend fun signOut() {
        auth.signOut()
    }

    override suspend fun deleteAccount(idToken: String) {
        deletion.delete(idToken)
    }
}

fun FirebaseUser.toAccount() = Account(
    userId = UserId(uid),
    displayName = displayName.orEmpty(),
    email = email.orEmpty(),
    photoUrl = photoUrl?.toString(),
)
