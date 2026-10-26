package com.cheesecomer.cartmate.di

import com.cheesecomer.cartmate.data.firestore.FirestoreProductRepository
import com.cheesecomer.cartmate.data.firestore.FirestoreShoppingStore
import com.cheesecomer.cartmate.domain.model.UserId
import com.cheesecomer.cartmate.domain.product.NormalizationDictionary
import com.cheesecomer.cartmate.domain.repository.CurrentUserProvider
import com.cheesecomer.cartmate.domain.repository.ItemRepository
import com.cheesecomer.cartmate.domain.repository.ProductRepository
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

    @Binds
    abstract fun bindProductRepository(repository: FirestoreProductRepository): ProductRepository

    companion object {
        @Provides
        fun provideClock(): Clock = Clock.systemDefaultZone()

        // 名寄せ辞書の中身は、同梱の辞書を読み込むまで空（docs/design/suggestions.md「名寄せ辞書」）
        @Provides
        fun provideNormalizationDictionary(): NormalizationDictionary = NormalizationDictionary.EMPTY

        @Provides
        fun provideCurrentUserProvider(auth: FirebaseAuth): CurrentUserProvider =
            CurrentUserProvider { UserId(checkNotNull(auth.currentUser?.uid) { "ログインしていません" }) }
    }
}
