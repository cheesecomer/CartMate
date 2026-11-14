package com.cheesecomer.cartmate.domain.product

import com.cheesecomer.cartmate.domain.model.Product
import com.cheesecomer.cartmate.domain.product.AiJudgementTestData.dictionary
import com.cheesecomer.cartmate.domain.product.AiJudgementTestData.judgement
import com.cheesecomer.cartmate.domain.product.AiJudgementTestData.product
import com.cheesecomer.cartmate.domain.product.AiJudgementTestData.purchase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** AI の候補にする条件（docs/design/suggestions.md「候補にする条件」）。偽の判定を与えて確かめる */
class AiCandidatesTest {

    private fun find(
        products: List<Product>,
        judgements: List<NameJudgement>,
        visible: List<Product> = products,
        dismissed: Set<AiCandidates.Pair> = emptySet(),
    ) = AiCandidates.find(products, visible, products.map { purchase(it) }, judgements.associateBy { it.key }, dictionary, dismissed)

    private val goma = product("胡麻ドレ", createdAt = 10)
    private val sesame = product("セサミドレ", createdAt = 20)

    @Test
    fun `AI の品目が同じ2つの商品を、後から登録した方を先の方にまとめる候補にする`() {
        val candidates = find(
            listOf(goma, sesame),
            listOf(judgement(goma, "ゴマドレッシング風味"), judgement(sesame, "ゴマドレッシング風味")),
        )
        assertThat(candidates).hasSize(1)
        assertThat(candidates[0].source).isEqualTo(sesame)
        assertThat(candidates[0].target).isEqualTo(goma)
        assertThat(candidates[0].reasons.map { it.name }).containsExactly("セサミドレ", "胡麻ドレ")
    }

    @Test
    fun `AI の品目が辞書にある名前なら、辞書でまとまる商品にまとめる候補にする`() {
        val dressing = product("ごまドレッシング", createdAt = 0)
        val candidates = find(listOf(goma, dressing), listOf(judgement(goma, "ごまドレッシング")))
        assertThat(candidates.map { it.source to it.target }).containsExactly(goma to dressing)
        assertThat(candidates[0].reasons).containsExactly(AiCandidates.Reason("胡麻ドレ", "胡麻ドレはごまドレッシング"))
    }

    @Test
    fun `どちらも辞書でまとまる商品は、AI の候補にしない（辞書がすでにまとめている）`() {
        assertThat(find(listOf(product("ごまドレ"), product("ごまドレッシング")), emptyList())).isEmpty()
    }

    @Test
    fun `AI の品目がカタカナとひらがなの違いだけなら、同じ品目として候補にする`() {
        // AI は入力の表記のまま品目を答えることがある（本番の「にら」と「ニラ」）
        val hiragana = product("にら", createdAt = 10)
        val katakana = product("ニラ", createdAt = 20)
        val candidates = find(listOf(hiragana, katakana), listOf(judgement(hiragana, "にら"), judgement(katakana, "ニラ")))
        assertThat(candidates.map { it.source to it.target }).containsExactly(katakana to hiragana)
    }

    @Test
    fun `AI の属性がカタカナとひらがなの違いだけなら、一致とみなす`() {
        val candidates = find(
            listOf(goma, sesame),
            listOf(judgement(goma, "豚肉", form = "こま切れ"), judgement(sesame, "豚肉", form = "コマ切れ")),
        )
        assertThat(candidates).hasSize(1)
        // 記号はそろえない（容量の 1.5L と 15L は別）
        assertThat(
            find(listOf(goma, sesame), listOf(judgement(goma, "コーラ", volume = "1.5L"), judgement(sesame, "コーラ", volume = "15L"))),
        ).isEmpty()
    }

    @Test
    fun `品目が違えば候補にしない`() {
        assertThat(find(listOf(goma, sesame), listOf(judgement(goma, "ごまドレッシング"), judgement(sesame, "和風ドレッシング")))).isEmpty()
    }

    @Test
    fun `統合禁止の品目どうしは候補にしない`() {
        val a = product("ミルク")
        val b = product("ソイ")
        val milk = product("牛乳")
        // AI がまちがえて「豆乳」と答えても、辞書の牛乳とは統合禁止
        assertThat(find(listOf(a, milk), listOf(judgement(a, "豆乳")))).isEmpty()
        assertThat(find(listOf(a, b), listOf(judgement(a, "牛乳"), judgement(b, "豆乳")))).isEmpty()
    }

    @Test
    fun `属性に矛盾があれば候補にしない。未指定と指定ありも一致とみなさない`() {
        val item = "ウーロン茶"
        fun pair(a: NameJudgement, b: NameJudgement) = find(listOf(goma, sesame), listOf(a, b))
        assertThat(pair(judgement(goma, item, form = "飲料"), judgement(sesame, item, form = "茶葉"))).isEmpty()
        assertThat(pair(judgement(goma, item, form = "飲料"), judgement(sesame, item))).isEmpty()
        assertThat(pair(judgement(goma, item, volume = "2000ml"), judgement(sesame, item, volume = "500ml"))).isEmpty()
        assertThat(pair(judgement(goma, item, variant = "詰め替え"), judgement(sesame, item))).isEmpty()
        assertThat(pair(judgement(goma, item, use = "洗濯用"), judgement(sesame, item, use = "食器用"))).isEmpty()
        assertThat(pair(judgement(goma, item, form = "飲料", volume = "2000ml"), judgement(sesame, item, form = "飲料", volume = "2000ml")))
            .hasSize(1)
    }

    @Test
    fun `辞書でまとまる商品の属性は、名前から読み取って比べる`() {
        val refill = product("ごまドレッシング詰め替え")
        val dictionaryWithRefill = dictionary.copy(
            aliases = dictionary.aliases + ("ごまドレッシング詰め替え" to NormalizationDictionary.Alias(AiJudgementTestData.sesameDressing, NormalizationDictionary.Match.EXACT)),
        )
        val products = listOf(goma, refill)
        fun find(judgement: NameJudgement) =
            AiCandidates.find(products, products, products.map { purchase(it) }, mapOf(judgement.key to judgement), dictionaryWithRefill)
        assertThat(find(judgement(goma, "ごまドレッシング"))).isEmpty()
        assertThat(find(judgement(goma, "ごまドレッシング", variant = "詰め替え"))).hasSize(1)
    }

    @Test
    fun `信頼度がしきい値より低い判定は候補にしない`() {
        val dressing = product("ごまドレッシング")
        assertThat(find(listOf(goma, dressing), listOf(judgement(goma, "ごまドレッシング", confidence = 0.69)))).isEmpty()
        assertThat(find(listOf(goma, dressing), listOf(judgement(goma, "ごまドレッシング", confidence = AiCandidates.MIN_CONFIDENCE)))).hasSize(1)
    }

    @Test
    fun `終わっていない判定、古いバージョンの判定、品目のない判定は使わない`() {
        val dressing = product("ごまドレッシング")
        fun find(judgement: NameJudgement) = find(listOf(goma, dressing), listOf(judgement))
        assertThat(find(judgement(goma, "ごまドレッシング", status = NameJudgement.Status.PENDING))).isEmpty()
        assertThat(find(judgement(goma, "ごまドレッシング", status = NameJudgement.Status.FAILED))).isEmpty()
        assertThat(find(judgement(goma, "ごまドレッシング", version = "old/0"))).isEmpty()
        assertThat(find(judgement(goma, null))).isEmpty()
    }

    @Test
    fun `判定は、その商品の最初の共有の購入から読み取った属性のキーで探す`() {
        val dressing = product("ごまドレッシング")
        val products = listOf(goma, dressing)
        val purchases = listOf(purchase(goma, memo = "詰め替え"), purchase(dressing))
        // 名前だけのキーの判定は、この商品の判定ではない
        val nameOnly = judgement(goma, "ごまドレッシング")
        assertThat(AiCandidates.find(products, products, purchases, mapOf(nameOnly.key to nameOnly), dictionary)).isEmpty()
        val withVariant = judgement(goma, "ごまドレッシング", qualifiers = mapOf("variant" to listOf("詰め替え")))
        assertThat(AiCandidates.find(products, products, purchases, mapOf(withVariant.key to withVariant), dictionary)).hasSize(1)
    }

    @Test
    fun `手動の訂正を覆さない（統合済み・辞書から外した商品は候補にしない）`() {
        val dressing = product("ごまドレッシング")
        val other = product("ドレッシング")
        val merged = product("胡麻ドレ", mergedInto = other)
        assertThat(find(listOf(merged, other, dressing), listOf(judgement(merged, "ごまドレッシング")))).isEmpty()
        val optedOut = product("ごまドレ", optOut = true)
        // 辞書から外した商品は、辞書の品目でも AI の判定でも比べない
        assertThat(find(listOf(goma, optedOut), listOf(judgement(goma, "ごまドレッシング")))).isEmpty()
    }

    @Test
    fun `自分に見えない商品と、選択肢を含む名前は候補にしない`() {
        val dressing = product("ごまドレッシング")
        assertThat(find(listOf(goma, dressing), listOf(judgement(goma, "ごまドレッシング")), visible = listOf(goma))).isEmpty()
        val choice = product("ごまドレかポン酢")
        assertThat(find(listOf(choice, dressing), listOf(judgement(choice, "ごまドレッシング")))).isEmpty()
    }

    @Test
    fun `「この候補を出さない」にした組は出さない（向きによらない）`() {
        val dressing = product("ごまドレッシング")
        val dismissed = setOf(AiCandidates.Pair.of(dressing.id, goma.id))
        assertThat(find(listOf(goma, dressing), listOf(judgement(goma, "ごまドレッシング")), dismissed = dismissed)).isEmpty()
        assertThat(AiCandidates.Pair.decode(dismissed.single().encode())).isEqualTo(AiCandidates.Pair.of(goma.id, dressing.id))
    }

    @Test
    fun `商品ごとの候補は、1つに決まるときだけ返す`() {
        val dressing = product("ごまドレッシング")
        val third = product("ごまだれドレ", createdAt = 30)
        val one = find(listOf(goma, dressing), listOf(judgement(goma, "ごまドレッシング")))
        assertThat(AiCandidates.forProduct(goma.id, one)?.target).isEqualTo(dressing)
        assertThat(AiCandidates.forProduct(dressing.id, one)?.source).isEqualTo(goma)
        val two = find(listOf(goma, dressing, third), listOf(judgement(goma, "ごまドレッシング"), judgement(third, "ごまドレッシング")))
        // 「胡麻ドレ」は「ごまドレッシング」とも「ごまだれドレ」とも組になるので、どちらにまとめるか決められない
        assertThat(AiCandidates.forProduct(dressing.id, two)).isNull()
        assertThat(AiCandidates.forProduct(goma.id, two)).isNull()
    }
}
