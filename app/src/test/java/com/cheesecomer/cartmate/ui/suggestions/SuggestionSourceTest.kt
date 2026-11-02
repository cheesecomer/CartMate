package com.cheesecomer.cartmate.ui.suggestions

import com.cheesecomer.cartmate.data.memory.InMemoryStore
import com.cheesecomer.cartmate.domain.model.NewItem
import com.cheesecomer.cartmate.domain.model.NewList
import com.cheesecomer.cartmate.domain.model.Product
import com.cheesecomer.cartmate.domain.model.UserId
import com.cheesecomer.cartmate.domain.model.Visibility
import com.cheesecomer.cartmate.domain.product.NormalizationDictionary
import com.cheesecomer.cartmate.domain.repository.ProductRepository
import com.cheesecomer.cartmate.domain.suggest.SuggestionEngine
import com.cheesecomer.cartmate.testing.MutableClock
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Duration

/** サジェストのエンジンをアプリで持っておく（フィードバック #016） */
class SuggestionSourceTest {

    private val clock = MutableClock()
    private var user = UserId("taro")
    private val store = InMemoryStore(clock) { user }

    /** 商品の購読を始めた回数。エンジンを作るための購読を、画面ごとにやり直していないかを見る */
    private var productSubscriptions = 0
    private val products = object : ProductRepository by store {
        override fun observeProducts(): Flow<List<Product>> =
            store.observeProducts().onStart { productSubscriptions++ }
    }

    private fun TestScope.cachedSource() = SuggestionSource(
        products, store, store, clock, { flowOf(NormalizationDictionary.EMPTY) },
        StandardTestDispatcher(testScheduler),
        cacheScope = backgroundScope,
        currentUser = { user },
    )

    /** スーパーで牛乳を10日ごとに3回買い、最後から8日たった（時期が近い商品に出る） */
    private suspend fun buyMilkEveryTenDays() {
        val listId = store.createList(NewList("スーパー", null, Visibility.SHARED))
        repeat(3) { index ->
            if (index > 0) clock.advance(Duration.ofDays(10))
            val id = store.addItem(NewItem(listId, "牛乳", 1.0, "本", null, Visibility.SHARED))
            store.completeItem(id, clock.now)
        }
        clock.advance(Duration.ofDays(8))
    }

    private fun SuggestionEngine.milkDaysSinceLast(): Long =
        (due("スーパー") as SuggestionEngine.DueResult.Suggestions).items.single().daysSinceLast

    @Test
    fun `計算したエンジンを持っておき、あとから開いた画面は計算し直さずに受け取る`() = runTest {
        buyMilkEveryTenDays()
        val source = cachedSource()
        source.keepWarm(backgroundScope)
        runCurrent()

        // リスト詳細を開き、続けて商品詳細入力を開く
        source.observe().first()
        source.observe().first()

        assertThat(productSubscriptions).isEqualTo(1)
    }

    @Test
    fun `ユーザーが変わったら、前のユーザーで計算したエンジンは使わない`() = runTest {
        buyMilkEveryTenDays()
        val source = cachedSource()
        source.keepWarm(backgroundScope)
        runCurrent()
        assertThat(source.observe().first().milkDaysSinceLast()).isEqualTo(8)

        user = UserId("hanako")
        // 新しいユーザーのデータが届くまでは、何も受け取らない
        val received = mutableListOf<SuggestionEngine>()
        backgroundScope.launch(Dispatchers.Unconfined) { source.observe().collect { received += it } }
        runCurrent()
        assertThat(received).isEmpty()
    }

    @Test
    fun `持っている間に日付が変わったら、経過日数を数え直す`() = runTest {
        buyMilkEveryTenDays()
        val source = cachedSource()
        source.keepWarm(backgroundScope)
        runCurrent()
        assertThat(source.observe().first().milkDaysSinceLast()).isEqualTo(8)

        // 端末の時計と、待っている時間の両方を翌日まで進める
        clock.advance(Duration.ofDays(1))
        advanceTimeBy(Duration.ofDays(1).toMillis())
        runCurrent()

        assertThat(source.observe().first().milkDaysSinceLast()).isEqualTo(9)
    }
}
