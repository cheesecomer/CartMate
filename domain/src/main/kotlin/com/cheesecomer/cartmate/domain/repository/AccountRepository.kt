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

    /**
     * アカウントを削除する（docs/requirements.md §3.5「アカウントの削除」）。[idToken] は直前に再ログインして取得した
     * Google の ID トークン。自分のプライベートなリスト・項目・購入の記録を消し、家族から抜けてから、アカウントを消す
     */
    suspend fun deleteAccount(idToken: String)
}
