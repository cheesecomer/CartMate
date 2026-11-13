package com.cheesecomer.cartmate.di

import com.cheesecomer.cartmate.ui.suggestions.AiCandidateSource
import com.cheesecomer.cartmate.ui.suggestions.JudgedAiCandidateSource
import com.cheesecomer.cartmate.ui.suggestions.JudgementRequester
import com.cheesecomer.cartmate.ui.suggestions.NameJudgementRequester
import com.cheesecomer.cartmate.data.firestore.FirestoreProductRepository
import com.cheesecomer.cartmate.data.firestore.FirestoreShoppingStore
import com.cheesecomer.cartmate.domain.model.UserId
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
import com.cheesecomer.cartmate.FirebaseEmulator
import com.cheesecomer.cartmate.data.dictionary.AssetDictionaryProvider
import com.cheesecomer.cartmate.domain.repository.DictionaryProvider
import com.cheesecomer.cartmate.ui.hints.HintStore
import com.cheesecomer.cartmate.ui.hints.SharedPreferencesHintStore

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    // インメモリ実装（data/memory/InMemoryStore）は、テストで使う
    @Binds
    abstract fun bindHintStore(store: SharedPreferencesHintStore): HintStore

    @Binds
    abstract fun bindShoppingListRepository(store: FirestoreShoppingStore): ShoppingListRepository

    @Binds
    abstract fun bindItemRepository(store: FirestoreShoppingStore): ItemRepository

    @Binds
    abstract fun bindPurchaseRepository(store: FirestoreShoppingStore): PurchaseRepository

    @Binds
    abstract fun bindProductRepository(repository: FirestoreProductRepository): ProductRepository

    // 同梱の名寄せ辞書（docs/design/suggestions.md「名寄せ辞書」）
    @Binds
    abstract fun bindDictionaryProvider(provider: AssetDictionaryProvider): DictionaryProvider

    // AI の名寄せ（docs/design/suggestions.md「AI による名寄せの候補」）
    @Binds
    abstract fun bindJudgementRequester(requester: NameJudgementRequester): JudgementRequester

    @Binds
    abstract fun bindAiCandidateSource(source: JudgedAiCandidateSource): AiCandidateSource

    companion object {
        // 端末の時計。デバッグ版のスクリーンショットでは固定する（FirebaseEmulator.fixedNow）
        @Provides
        fun provideClock(): Clock = FirebaseEmulator.clock

        @Provides
        fun provideCurrentUserProvider(auth: FirebaseAuth): CurrentUserProvider =
            CurrentUserProvider { UserId(checkNotNull(auth.currentUser?.uid) { "ログインしていません" }) }
    }
}
