package com.cheesecomer.cartmate.domain.product

import com.cheesecomer.cartmate.domain.model.Visibility
import com.cheesecomer.cartmate.domain.product.AiJudgementTestData.dictionary
import com.cheesecomer.cartmate.domain.product.AiJudgementTestData.judgement
import com.cheesecomer.cartmate.domain.product.AiJudgementTestData.product
import com.cheesecomer.cartmate.domain.product.AiJudgementTestData.purchase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

/** AI に問い合わせる条件（docs/design/suggestions.md「問い合わせる条件」） */
class JudgementRequestTest {

    private val goma = product("胡麻ドレ")

    @Test
    fun `共有の項目で購入された、辞書で決まらない商品は、名前を判定に送る`() {
        val request = JudgementRequest.of(goma.id, listOf(goma), listOf(purchase(goma)), dictionary)
        assertThat(request).isEqualTo(JudgementRequest(goma.id, "胡麻ドレ", emptyMap()))
        assertThat(request?.key).isEqualTo(JudgementKey.of("胡麻ドレ", emptyMap()))
    }

    @Test
    fun `属性は、共有の項目で最初に購入した記録の名前と備考から読み取る`() {
        val tea = product("ウーロン茶")
        val purchases = listOf(
            purchase(tea, day = 5, memo = "ペットボトル"),
            purchase(tea, day = 1, memo = "詰め替え"),
            purchase(tea, day = 0, memo = "ペットボトル", visibility = Visibility.PRIVATE),
            purchase(tea, day = 0, memo = "ペットボトル", active = false, id = "undone"),
        )
        val request = JudgementRequest.of(tea.id, listOf(tea), purchases, dictionary)
        assertThat(request?.qualifiers).isEqualTo(mapOf("variant" to listOf("詰め替え")))
    }

    @Test
    fun `プライベートな項目だけで購入した商品は送らない`() {
        assertThat(JudgementRequest.of(goma.id, listOf(goma), listOf(purchase(goma, visibility = Visibility.PRIVATE)), dictionary)).isNull()
        assertThat(JudgementRequest.of(goma.id, listOf(goma), emptyList(), dictionary)).isNull()
    }

    @Test
    fun `辞書で品目が決まる商品は送らない`() {
        val dressing = product("ごまドレッシング")
        assertThat(JudgementRequest.of(dressing.id, listOf(dressing), listOf(purchase(dressing)), dictionary)).isNull()
    }

    @Test
    fun `既定の同義語（お茶）は辞書で決まらないので送る`() {
        val tea = product("お茶")
        assertThat(JudgementRequest.of(tea.id, listOf(tea), listOf(purchase(tea)), dictionary)).isNotNull()
    }

    @Test
    fun `手動の統合・分離がある商品は送らない`() {
        val target = product("ドレッシング")
        val merged = product("胡麻ドレ", mergedInto = target)
        val purchases = listOf(purchase(merged), purchase(target))
        assertThat(JudgementRequest.of(merged.id, listOf(merged, target), purchases, dictionary)).isNull()
        // まとめている側（統合先）も
        assertThat(JudgementRequest.of(target.id, listOf(merged, target), purchases, dictionary)).isNull()
        // 辞書から外した商品も
        val optedOut = product("ごまドレ", optOut = true)
        assertThat(JudgementRequest.of(optedOut.id, listOf(optedOut), listOf(purchase(optedOut)), dictionary)).isNull()
    }

    @Test
    fun `選択肢を含む名前は送らない`() {
        val choice = product("カブもしくは大根")
        assertThat(JudgementRequest.of(choice.id, listOf(choice), listOf(purchase(choice)), dictionary)).isNull()
    }

    @Test
    fun `関数が受け付けない長さの属性は送らない`() {
        val name = product("おやつ")
        val memo = "100g 200g 300g 400g"
        assertThat(JudgementRequest.of(name.id, listOf(name), listOf(purchase(name, memo = memo)), dictionary)).isNull()
    }

    private val now = Instant.parse("2026-11-09T03:00:00Z")

    @Test
    fun `判定がなければ問い合わせ、終わった判定があれば問い合わせない`() {
        assertThat(JudgementRequest.shouldAsk(null, now)).isTrue()
        assertThat(JudgementRequest.shouldAsk(judgement(goma, "ごまドレッシング"), now)).isFalse()
        assertThat(JudgementRequest.shouldAsk(judgement(goma, null), now)).isFalse()
    }

    @Test
    fun `判定のバージョンが違えば問い合わせ直す`() {
        assertThat(JudgementRequest.shouldAsk(judgement(goma, "ごまドレッシング", version = "old/0"), now)).isTrue()
    }

    @Test
    fun `pending は5分、failed は1日たったら問い合わせ直す`() {
        val pending = judgement(goma, null, status = NameJudgement.Status.PENDING).copy(requestedAt = now.minus(Duration.ofMinutes(5)))
        assertThat(JudgementRequest.shouldAsk(pending, now)).isTrue()
        assertThat(JudgementRequest.shouldAsk(pending, now.minusSeconds(1))).isFalse()
        val failed = judgement(goma, null, status = NameJudgement.Status.FAILED).copy(judgedAt = now.minus(Duration.ofDays(1)))
        assertThat(JudgementRequest.shouldAsk(failed, now)).isTrue()
        assertThat(JudgementRequest.shouldAsk(failed, now.minusSeconds(1))).isFalse()
    }
}
