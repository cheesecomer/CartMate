package com.cheesecomer.cartmate.ui.suggestions

import app.cash.turbine.test
import com.cheesecomer.cartmate.data.memory.InMemoryStore
import com.cheesecomer.cartmate.domain.model.Item
import com.cheesecomer.cartmate.domain.model.ItemId
import com.cheesecomer.cartmate.domain.model.ListId
import com.cheesecomer.cartmate.domain.model.UserId
import com.cheesecomer.cartmate.domain.model.Visibility
import com.cheesecomer.cartmate.domain.product.JudgementRequest
import com.cheesecomer.cartmate.domain.product.NormalizationDictionary
import com.cheesecomer.cartmate.domain.product.NormalizationDictionary.Alias
import com.cheesecomer.cartmate.domain.product.NormalizationDictionary.Match
import com.cheesecomer.cartmate.domain.product.CanonicalId
import com.cheesecomer.cartmate.testing.FakeAiDismissalRepository
import com.cheesecomer.cartmate.testing.FakeNameJudgementRepository
import com.cheesecomer.cartmate.testing.MutableClock
import com.cheesecomer.cartmate.testing.awaitUntil
import com.cheesecomer.cartmate.testing.createList
import com.cheesecomer.cartmate.testing.newItem
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant

/** 購入のあとに判定を頼むこと（docs/design/suggestions.md「問い合わせる条件」）と、AI の候補 */
class AiJudgementsTest {

    private val clock = MutableClock()
    private val store = InMemoryStore(clock) { UserId("taro") }
    private val listId: ListId = runBlocking { store.createList("スーパー") }
    private val judgements = FakeNameJudgementRepository()
    private val online = MutableStateFlow(true)
    private val sesame = CanonicalId("sesame_dressing")
    private val dictionary = NormalizationDictionary(
        version = "2026.10.1",
        canonicalNames = mapOf(sesame to "ごまドレッシング"),
        aliases = mapOf("ごまドレッシング" to Alias(sesame, Match.EXACT)),
    )

    private fun TestScope.requester() = NameJudgementRequester(
        store, store, { flowOf(dictionary) }, judgements, { online }, clock, backgroundScope,
    )

    /** [name] を追加してチェックし、チェックした項目を返す */
    private suspend fun purchase(name: String, visibility: Visibility = Visibility.SHARED): Item {
        val id: ItemId = store.addItem(newItem(listId, name, visibility = visibility)).id
        store.completeItem(id, clock.instant())
        return store.observeListItems(listId, Instant.EPOCH).first().first { it.id == id }
    }

    @Test
    fun `共有の項目をチェックしたら、辞書で決まらない名前の判定を頼む`() = runTest {
        val requester = requester()
        requester.onPurchased(purchase("胡麻ドレ"))
        runCurrent()
        assertThat(judgements.requests.value.map { it.normalizedName }).containsExactly("胡麻ドレ")
    }

    @Test
    fun `プライベートな項目と、辞書で決まる名前は頼まない`() = runTest {
        val requester = requester()
        requester.onPurchased(purchase("胡麻ドレ", visibility = Visibility.PRIVATE))
        requester.onPurchased(purchase("ごまドレッシング"))
        runCurrent()
        assertThat(judgements.requests.value).isEmpty()
    }

    @Test
    fun `判定がすでにあれば頼まない`() = runTest {
        val item = purchase("胡麻ドレ")
        judgements.judge(store.observeProducts().first().single { it.displayName == "胡麻ドレ" }, "ごまドレッシング")
        requester().onPurchased(item)
        runCurrent()
        assertThat(judgements.requests.value).isEmpty()
    }

    @Test
    fun `通信できなければ、つながるまで待ってから頼む`() = runTest {
        online.value = false
        requester().onPurchased(purchase("胡麻ドレ"))
        runCurrent()
        assertThat(judgements.requests.value).isEmpty()
        online.value = true
        runCurrent()
        assertThat(judgements.requests.value.map { it.normalizedName }).containsExactly("胡麻ドレ")
    }

    @Test
    fun `待っている間に同じ商品をまたチェックしても、二重に頼まない`() = runTest {
        online.value = false
        val requester = requester()
        val first = purchase("胡麻ドレ")
        requester.onPurchased(first)
        requester.onPurchased(first)
        runCurrent()
        online.value = true
        runCurrent()
        assertThat(judgements.requests.value).hasSize(1)
    }

    private val dismissals = FakeAiDismissalRepository()
    private fun source() = JudgedAiCandidateSource(store, store, store, { flowOf(dictionary) }, judgements, dismissals, clock)

    @Test
    fun `判定が届くと候補になり、選ぶとまとまり、出さないにした組は消える`() = runTest {
        purchase("胡麻ドレ")
        purchase("ごまドレッシング")
        val goma = store.observeProducts().first().single { it.displayName == "胡麻ドレ" }
        val source = source()
        source.observe().test {
            assertThat(awaitItem()).isEmpty()
            judgements.judge(goma, "ごまドレッシング")
            val candidate = awaitUntil { it.isNotEmpty() }.single()
            assertThat(candidate.source.displayName).isEqualTo("胡麻ドレ")
            assertThat(candidate.target.displayName).isEqualTo("ごまドレッシング")

            source.dismiss(candidate.pair)
            assertThat(awaitUntil { it.isEmpty() }).isEmpty()
            dismissals.dismissed.value = emptySet()
            assertThat(source.merge(awaitUntil { it.isNotEmpty() }.single())).isTrue()
            // まとめた商品は候補に出ない
            assertThat(awaitUntil { it.isEmpty() }).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
        val merged = store.observeProducts().first().single { it.displayName == "胡麻ドレ" }
        assertThat(merged.mergedInto?.value).isEqualTo(store.observeProducts().first().single { it.displayName == "ごまドレッシング" }.id.value)
    }

    @Test
    fun `非表示の商品は候補にしない`() = runTest {
        purchase("胡麻ドレ")
        purchase("ごまドレッシング")
        val products = store.observeProducts().first()
        val goma = products.single { it.displayName == "胡麻ドレ" }
        store.setSuggestionExcluded(products.single { it.displayName == "ごまドレッシング" }.id, true)
        judgements.judge(goma, "ごまドレッシング")
        assertThat(source().observe().first()).isEmpty()
    }

    @Test
    fun `頼む内容は、domain の規則（JudgementRequest）と同じ`() = runTest {
        val item = purchase("胡麻ドレ")
        val products = store.observeProducts().first()
        val purchases = store.observePurchases(Instant.EPOCH).first()
        requester().onPurchased(item)
        runCurrent()
        assertThat(judgements.requests.value)
            .containsExactly(JudgementRequest.of(products.single { it.displayName == "胡麻ドレ" }.id, products, purchases, dictionary))
    }
}
