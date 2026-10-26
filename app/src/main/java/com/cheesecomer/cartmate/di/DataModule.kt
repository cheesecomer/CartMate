package com.cheesecomer.cartmate.di

import com.cheesecomer.cartmate.data.firestore.FirestoreShoppingStore
import com.cheesecomer.cartmate.domain.model.UserId
import com.cheesecomer.cartmate.domain.repository.CurrentUserProvider
import com.cheesecomer.cartmate.domain.repository.ItemRepository
import com.cheesecomer.cartmate.domain.repository.PurchaseRepository
import com.cheesecomer.cartmate.domain.repository.ShoppingListRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    // インメモリ実装（data/memory/InMemoryStore）は、テストで使う
    @Binds
    abstract fun bindShoppingListRepository(store: FirestoreShoppingStore): ShoppingListRepository

    @Binds
    abstract fun bindItemRepository(store: FirestoreShoppingStore): ItemRepository

    @Binds
    abstract fun bindPurchaseRepository(store: FirestoreShoppingStore): PurchaseRepository

    companion object {
        @Provides
        fun provideClock(): Clock = Clock.systemDefaultZone()

        @Provides
        fun provideCurrentUserProvider(auth: FirebaseAuth): CurrentUserProvider =
            CurrentUserProvider { UserId(checkNotNull(auth.currentUser?.uid) { "ログインしていません" }) }
    }
}
