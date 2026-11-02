package com.cheesecomer.cartmate.ui.suggestions

import com.cheesecomer.cartmate.domain.model.UserId
import com.cheesecomer.cartmate.domain.repository.CurrentUserProvider
import com.cheesecomer.cartmate.domain.repository.DictionaryProvider
import com.cheesecomer.cartmate.domain.repository.ItemRepository
import com.cheesecomer.cartmate.domain.repository.ProductRepository
import com.cheesecomer.cartmate.domain.repository.PurchaseRepository
import com.cheesecomer.cartmate.domain.suggest.SuggestionEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 予測とサジェストの入力（docs/design/suggestions.md「計算の実行場所」）。商品・直近365日の購入の記録・家族の未購入の項目・
 * 名寄せ辞書をまとめ、どれかが変わったら [SuggestionEngine] を作り直す。どれも自分に見えるものだけを購読している。
 *
 * アプリでは1つだけ作り、計算したエンジンを [cacheScope] で持っておく。リスト詳細や商品詳細入力を開くたびに作り直さず、
 * データが変わったときだけ裏で作り直す（フィードバック #016）。計算は、リスト一覧を表示したときに始める（[keepWarm]）。
 * [cacheScope] が null（テスト）なら持っておかず、購読のたびに計算する
 */
@Singleton
class SuggestionSource(
    private val productRepository: ProductRepository,
    private val itemRepository: ItemRepository,
    private val purchaseRepository: PurchaseRepository,
    private val clock: Clock,
    private val dictionaryProvider: DictionaryProvider,
    /** 計算を行うディスパッチャー。テストではテストのディスパッチャーを渡す（テストの終了後に計算が残らないように） */
    val dispatcher: CoroutineDispatcher,
    private val cacheScope: CoroutineScope? = null,
    /** 計算したユーザー。ログインやアカウントの切り替えの直後に、前のユーザーのエンジンを使わないため */
    private val currentUser: CurrentUserProvider? = null,
) {
    @Inject
    constructor(
        productRepository: ProductRepository,
        itemRepository: ItemRepository,
        purchaseRepository: PurchaseRepository,
        clock: Clock,
        dictionaryProvider: DictionaryProvider,
        currentUser: CurrentUserProvider,
    ) : this(
        productRepository, itemRepository, purchaseRepository, clock, dictionaryProvider, Dispatchers.Default,
        cacheScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        currentUser = currentUser,
    )

    private class Computed(val userId: UserId?, val engine: SuggestionEngine)

    /** 計算を始めるたびに作る。購入の記録の範囲（直近365日）と今日の日付は、そのときの時計で決める */
    private fun compute(): Flow<Computed> = combine(
        productRepository.observeProducts(),
        itemRepository.observeOpenItems(),
        purchaseRepository.observePurchases(since = clock.instant().minus(PURCHASE_WINDOW)),
        dictionaryProvider.observeDictionary(),
        // 持っておく間に日付が変わったら、経過日数を数え直す
        if (cacheScope != null) dateChanges() else flowOf(Unit),
    ) { products, openItems, purchases, dictionary, _ ->
        val engine = SuggestionEngine(products, purchases, openItems, dictionary, LocalDate.now(clock), clock.zone)
        Computed(currentUser?.currentUserId(), engine)
    }.flowOn(dispatcher)

    private val shared: Flow<Computed>? = cacheScope?.let {
        flow { emitAll(compute()) }.shareIn(it, SharingStarted.WhileSubscribed(KEEP_MILLIS), replay = 1)
    }

    fun observe(): Flow<SuggestionEngine> =
        (shared ?: compute())
            .filter { it.userId == currentUser?.currentUserId() }
            .map { it.engine }

    /** [scope] が続く間、エンジンを計算して持っておく。リストを開くころには計算が終わっているように、リスト一覧から呼ぶ */
    fun keepWarm(scope: CoroutineScope) {
        val shared = shared ?: return
        scope.launch { shared.collect {} }
    }

    /** 始めたときと、日付が変わるたびに流す */
    private fun dateChanges(): Flow<LocalDate> = flow {
        while (true) {
            val now = clock.instant()
            val today = LocalDate.ofInstant(now, clock.zone)
            emit(today)
            val nextMidnight = today.plusDays(1).atStartOfDay(clock.zone).toInstant()
            delay(Duration.between(now, nextMidnight).toMillis().coerceAtLeast(1))
        }
    }.distinctUntilChanged()

    private companion object {
        /** 購入の記録は直近365日分を使う（docs/design/data-model.md「購入履歴の取得範囲」） */
        val PURCHASE_WINDOW: Duration = Duration.ofDays(365)

        /** 購読がなくなっても、この間はエンジンと購読を持っておく（画面の行き来のたびに計算し直さない） */
        const val KEEP_MILLIS = 5 * 60 * 1000L
    }
}
