package com.cheesecomer.cartmate.domain.repository

import com.cheesecomer.cartmate.domain.model.Account
import kotlinx.coroutines.flow.Flow

/** Google アカウントでのログイン（docs/requirements.md §3.5「ログイン」） */
interface AccountRepository {
    /** ログイン中のアカウント。ログインしていなければ null */
    fun observeAccount(): Flow<Account?>

    /** Credential Manager で取得した Google の ID トークンでログインする */
    suspend fun signInWithGoogle(idToken: String)

    suspend fun signOut()
}
