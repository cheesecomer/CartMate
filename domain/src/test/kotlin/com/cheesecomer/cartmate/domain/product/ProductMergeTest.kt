package com.cheesecomer.cartmate.domain.product

import com.cheesecomer.cartmate.domain.model.Product
import com.cheesecomer.cartmate.domain.model.ProductId
import com.cheesecomer.cartmate.domain.product.NormalizationDictionary.Alias
import com.cheesecomer.cartmate.domain.product.NormalizationDictionary.Match
import com.cheesecomer.cartmate.domain.text.NameNormalizer
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class ProductMergeTest {

    private val egg = CanonicalId("egg")
    private val dictionary = NormalizationDictionary(
        version = "test",
        canonicalNames = mapOf(egg to "卵"),
        aliases = mapOf("たまご" to Alias(egg, Match.PREFIX), "卵" to Alias(egg, Match.PREFIX)),
    )

    private fun product(name: String, mergedInto: Product? = null, optOut: Boolean = false) = Product(
        id = ProductId(NameNormalizer.productId(name)),
        displayName = name,
        normalizedName = NameNormalizer.normalizedName(name),
        searchKey = NameNormalizer.searchKey(name),
        createdAt = Instant.EPOCH,
        mergedInto = mergedInto?.id,
        dictionaryOptOut = optOut,
    )

    @Test
    fun `自分に見える商品だけを出す`() {
        val milk = product("牛乳")
        val secret = product("プレゼント")
        val rows = ProductMerge.rows(listOf(milk, secret), setOf(milk.id), dictionary)
        assertThat(rows.map { it.product }).containsExactly(milk)
    }

    @Test
    fun `手動で統合した商品は、統合先の名前と「統合を解除」を出す`() {
        val milk = product("牛乳")
        val kana = product("ぎゅうにゅう", mergedInto = milk)
        val row = ProductMerge.rows(listOf(milk, kana), setOf(milk.id, kana.id), dictionary).single { it.product == kana }
        assertThat(row.mergedIntoName).isEqualTo("牛乳")
        assertThat(row.canUnmerge).isTrue()
        assertThat(row.canOptOut).isFalse()
    }

    @Test
    fun `辞書でまとめた商品は、品目の名前と「別の商品として扱う」を出す`() {
        val kana = product("たまご")
        val row = ProductMerge.rows(listOf(kana), setOf(kana.id), dictionary).single()
        assertThat(row.dictionaryName).isEqualTo("卵")
        assertThat(row.canOptOut).isTrue()
        assertThat(row.canOptIn).isFalse()
    }

    @Test
    fun `辞書の名寄せを外した商品は「辞書でまとめる」を出す`() {
        val kana = product("たまご", optOut = true)
        val row = ProductMerge.rows(listOf(kana), setOf(kana.id), dictionary).single()
        assertThat(row.dictionaryName).isNull()
        assertThat(row.canOptOut).isFalse()
        assertThat(row.canOptIn).isTrue()
    }

    @Test
    fun `統合先は、たどった先の商品にする`() {
        val dairy = product("乳製品")
        val milk = product("牛乳", mergedInto = dairy)
        val kana = product("ぎゅうにゅう")
        assertThat(ProductMerge.mergeTarget(kana.id, milk.id, listOf(dairy, milk, kana))).isEqualTo(dairy.id)
    }

    @Test
    fun `自分自身や、自分に統合されている商品には統合できない`() {
        val milk = product("牛乳")
        val kana = product("ぎゅうにゅう", mergedInto = milk)
        val products = listOf(milk, kana)
        assertThat(ProductMerge.mergeTarget(milk.id, milk.id, products)).isNull()
        assertThat(ProductMerge.mergeTarget(milk.id, kana.id, products)).isNull()
    }
}
