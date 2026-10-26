package com.cheesecomer.cartmate.di

import com.cheesecomer.cartmate.data.auth.FirebaseAccountRepository
import com.cheesecomer.cartmate.data.connectivity.ConnectivityObserver
import com.cheesecomer.cartmate.data.firestore.FirestoreHouseholdRepository
import com.cheesecomer.cartmate.domain.repository.AccountRepository
import com.cheesecomer.cartmate.domain.repository.HouseholdRepository
import com.cheesecomer.cartmate.domain.repository.MemberDirectory
import com.cheesecomer.cartmate.domain.repository.OnlineStatus
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class FirebaseModule {

    @Binds
    abstract fun bindAccountRepository(repository: FirebaseAccountRepository): AccountRepository

    @Binds
    abstract fun bindOnlineStatus(observer: ConnectivityObserver): OnlineStatus

    @Binds
    abstract fun bindHouseholdRepository(repository: FirestoreHouseholdRepository): HouseholdRepository

    @Binds
    abstract fun bindMemberDirectory(repository: FirestoreHouseholdRepository): MemberDirectory

    companion object {
        @Provides
        fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

        // 永続キャッシュは既定で有効（docs/design/architecture.md）
        @Provides
        fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()
    }
}
