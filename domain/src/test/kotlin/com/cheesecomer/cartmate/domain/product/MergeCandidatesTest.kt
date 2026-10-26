package com.cheesecomer.cartmate.domain.product

import com.cheesecomer.cartmate.domain.model.Product
import com.cheesecomer.cartmate.domain.model.ProductId
import com.cheesecomer.cartmate.domain.product.NormalizationDictionary.Alias
import com.cheesecomer.cartmate.domain.product.NormalizationDictionary.Match
import com.cheesecomer.cartmate.domain.text.NameNormalizer
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class MergeCandidatesTest {

    private fun product(name: String, mergedInto: Product? = null) = Product(
        id = ProductId(NameNormalizer.productId(name)),
        displayName = NameNormalizer.displayName(name),
        normalizedName = NameNormalizer.normalizedName(name),
        searchKey = NameNormalizer.searchKey(name),
        createdAt = Instant.EPOCH,
        mergedInto = mergedInto?.id,
    )

    private val empty = NormalizationDictionary.EMPTY

    private fun find(name: String, products: List<Product>, dictionary: NormalizationDictionary = empty) =
        MergeCandidates.find(name, visible = products, all = products, dictionary = dictionary)

    @Test
    fun `前方一致する既存の商品を候補にする`() {
        val milk = product("牛乳")
        assertThat(find("牛乳 1L", listOf(milk))).isEqualTo(milk)
    }

    @Test
    fun `部分一致する既存の商品も候補にする`() {
        val milk = product("牛乳")
        assertThat(find("低脂肪牛乳", listOf(milk))).isEqualTo(milk)
    }

    @Test
    fun `前方一致を部分一致より優先する`() {
        val milk = product("牛乳")
        val lowFat = product("低脂肪牛乳")
        assertThat(find("牛乳パック", listOf(lowFat, milk))).isEqualTo(milk)
    }

    @Test
    fun `カタカナとひらがなの違いは、候補として示す`() {
        val milk = product("ミルク")
        assertThat(find("みるく", listOf(milk))).isEqualTo(milk)
    }

    @Test
    fun `同じ名前の商品があれば、新しい商品ではないので候補を出さない`() {
        val milk = product("牛乳")
        assertThat(find("牛乳", listOf(milk))).isNull()
        assertThat(find(" 牛乳 ", listOf(milk))).isNull()
    }

    @Test
    fun `1文字の名前では候補を出さない`() {
        assertThat(find("す", listOf(product("レタス")))).isNull()
        assertThat(find("お酢", listOf(product("す")))).isNull()
    }

    @Test
    fun `統合されていれば統合先を候補にする`() {
        val milk = product("牛乳")
        val kana = product("ぎゅうにゅう瓶", mergedInto = milk)
        assertThat(find("ぎゅうにゅう", listOf(milk, kana))).isEqualTo(milk)
    }

    @Test
    fun `自分に見えない商品は候補にしない`() {
        val secret = product("プレゼント用ケーキ")
        val result = MergeCandidates.find("プレゼント", visible = emptyList(), all = listOf(secret), dictionary = empty)
        assertThat(result).isNull()
    }

    @Test
    fun `辞書で統合禁止の組は候補にしない`() {
        val six = CanonicalId("bread6")
        val eight = CanonicalId("bread8")
        val dictionary = NormalizationDictionary(
            version = "test",
            canonicalNames = mapOf(six to "食パン6枚切り", eight to "食パン8枚切り"),
            aliases = mapOf("食パン6枚切り" to Alias(six, Match.EXACT), "食パン8枚切り" to Alias(eight, Match.EXACT)),
            neverMerge = setOf(setOf(six, eight)),
        )
        assertThat(find("食パン8枚切り", listOf(product("食パン6枚切り")), dictionary)).isNull()
    }

    @Test
    fun `どれにも当たらなければ候補なし`() {
        assertThat(find("卵", listOf(product("牛乳")))).isNull()
    }
}
