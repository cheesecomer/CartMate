package com.cheesecomer.cartmate.di

import com.cheesecomer.cartmate.data.memory.InMemoryStore
import com.cheesecomer.cartmate.domain.model.UserId
import com.cheesecomer.cartmate.domain.repository.CurrentUserProvider
import com.cheesecomer.cartmate.domain.repository.ItemRepository
import com.cheesecomer.cartmate.domain.repository.PurchaseRepository
import com.cheesecomer.cartmate.domain.repository.ShoppingListRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    // フェーズ2で Firestore の実装に差し替える
    @Binds
    abstract fun bindShoppingListRepository(store: InMemoryStore): ShoppingListRepository

    @Binds
    abstract fun bindItemRepository(store: InMemoryStore): ItemRepository

    @Binds
    abstract fun bindPurchaseRepository(store: InMemoryStore): PurchaseRepository

    companion object {
        @Provides
        fun provideClock(): Clock = Clock.systemDefaultZone()

        // フェーズ2で Firebase Auth のユーザーに差し替える
        @Provides
        fun provideCurrentUserProvider(): CurrentUserProvider = CurrentUserProvider { LOCAL_USER }

        private val LOCAL_USER = UserId("local")
    }
}
