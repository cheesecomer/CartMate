package com.cheesecomer.cartmate.data.memory

import com.cheesecomer.cartmate.domain.model.Account
import com.cheesecomer.cartmate.domain.repository.AccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** テスト用。決まったアカウントでログインしている */
class FixedAccountRepository(account: Account?) : AccountRepository {
    private val account = MutableStateFlow(account)

    override fun observeAccount(): Flow<Account?> = account

    override suspend fun signInWithGoogle(idToken: String) = Unit

    override suspend fun signOut() {
        account.value = null
    }

    override suspend fun deleteAccount(idToken: String) {
        account.value = null
    }
}
