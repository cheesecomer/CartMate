package com.cheesecomer.cartmate.domain.product

import com.cheesecomer.cartmate.domain.model.Product
import com.cheesecomer.cartmate.domain.model.ProductId
import com.cheesecomer.cartmate.domain.product.NormalizationDictionary.Alias
import com.cheesecomer.cartmate.domain.product.NormalizationDictionary.Match
import com.cheesecomer.cartmate.domain.product.ProductResolver.CycleKey
import com.cheesecomer.cartmate.domain.product.ProductResolver.Reason
import com.cheesecomer.cartmate.domain.product.ProductResolver.ResolvedKey
import com.cheesecomer.cartmate.domain.text.NameNormalizer
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class ProductResolverTest {

    private val egg = CanonicalId("egg")
    private val bread = CanonicalId("bread")
    private val bread6 = CanonicalId("bread6")
    private val bread8 = CanonicalId("bread8")

    private val dictionary = NormalizationDictionary(
        version = "test",
        canonicalNames = mapOf(egg to "卵", bread6 to "食パン6枚切り", bread8 to "食パン8枚切り", bread to "食パン"),
        aliases = mapOf(
            "卵" to Alias(egg, Match.PREFIX),
            "たまご" to Alias(egg, Match.PREFIX),
            "食パン6枚切り" to Alias(bread6, Match.PREFIX),
            "食パン8枚切り" to Alias(bread8, Match.PREFIX),
        ),
        cycleGroups = mapOf(bread6 to CycleGroupId("食パン"), bread8 to CycleGroupId("食パン")),
        neverMerge = setOf(setOf(bread6, bread8)),
    )

    private fun product(name: String, mergedInto: Product? = null, optOut: Boolean = false) = Product(
        id = ProductId(NameNormalizer.productId(name)),
        displayName = NameNormalizer.displayName(name),
        normalizedName = NameNormalizer.normalizedName(name),
        searchKey = NameNormalizer.searchKey(name),
        createdAt = Instant.EPOCH,
        mergedInto = mergedInto?.id,
        dictionaryOptOut = optOut,
    )

    @Test
    fun `辞書の別名に一致した商品は、同じ品目にまとめ、一致した名前を理由にする`() {
        val kana = product("たまご")
        val kanji = product("卵")
        val resolver = ProductResolver(listOf(kana, kanji), dictionary)

        val resolved = resolver.resolve(kana.id)
        assertThat(resolved.key).isEqualTo(ResolvedKey.OfCanonical(egg))
        assertThat(resolved.reason).isEqualTo(Reason.Dictionary("たまご", egg, merged = false))
        assertThat(resolver.resolve(kanji.id).key).isEqualTo(resolved.key)
    }

    @Test
    fun `辞書は正規化した名前で照合する`() {
        val halfWidth = product(" たまご ")
        assertThat(ProductResolver(listOf(halfWidth), dictionary).resolve(halfWidth.id).key).isEqualTo(ResolvedKey.OfCanonical(egg))
    }

    @Test
    fun `辞書にない商品は自分自身`() {
        val milk = product("牛乳")
        val resolved = ProductResolver(listOf(milk), dictionary).resolve(milk.id)
        assertThat(resolved).isEqualTo(ProductResolver.Resolution(ResolvedKey.OfProduct(milk.id), Reason.Self))
    }

    @Test
    fun `手動で分離した商品は、辞書にあってもまとめない`() {
        val kana = product("たまご", optOut = true)
        val resolved = ProductResolver(listOf(kana), dictionary).resolve(kana.id)
        assertThat(resolved).isEqualTo(ProductResolver.Resolution(ResolvedKey.OfProduct(kana.id), Reason.OptedOut(merged = false)))
    }

    @Test
    fun `手動の統合は辞書より優先し、統合先を辞書で照合する`() {
        val kanji = product("卵")
        val custom = product("たまごLサイズ", mergedInto = kanji)
        val resolved = ProductResolver(listOf(kanji, custom), dictionary).resolve(custom.id)
        assertThat(resolved.key).isEqualTo(ResolvedKey.OfCanonical(egg))
        assertThat(resolved.reason).isEqualTo(Reason.Dictionary("卵", egg, merged = true))
    }

    @Test
    fun `手動で統合した先が辞書にない場合は、統合先にまとめる`() {
        val milk = product("牛乳")
        val kana = product("ぎゅうにゅう", mergedInto = milk)
        val resolved = ProductResolver(listOf(milk, kana), dictionary).resolve(kana.id)
        assertThat(resolved).isEqualTo(ProductResolver.Resolution(ResolvedKey.OfProduct(milk.id), Reason.Manual))
    }

    @Test
    fun `周期だけの関係は、同じ商品にはしないが同じ周期のグループになる`() {
        val six = product("食パン6枚切り")
        val eight = product("食パン8枚切り")
        val resolver = ProductResolver(listOf(six, eight), dictionary)

        assertThat(resolver.resolve(six.id).key).isNotEqualTo(resolver.resolve(eight.id).key)
        assertThat(resolver.cycleKey(six.id)).isEqualTo(CycleKey.OfGroup(CycleGroupId("食パン")))
        assertThat(resolver.cycleKey(eight.id)).isEqualTo(resolver.cycleKey(six.id))
    }

    @Test
    fun `周期のグループがなければ、名寄せのまとめ先で周期を数える`() {
        val kana = product("たまご")
        assertThat(ProductResolver(listOf(kana), dictionary).cycleKey(kana.id)).isEqualTo(CycleKey.Of(ResolvedKey.OfCanonical(egg)))
    }

    @Test
    fun `統合禁止の組は自動でまとめない`() {
        assertThat(dictionary.mayMerge(bread6, bread8)).isFalse()
        assertThat(dictionary.mayMerge(bread8, bread6)).isFalse()
        assertThat(dictionary.mayMerge(egg, bread6)).isTrue()
        assertThat(dictionary.mayMerge(egg, egg)).isTrue()
    }

    @Test
    fun `既定の同義語（お茶 → 緑茶）は、備考を使う判定ができるまで自動でまとめない`() {
        val greenTea = CanonicalId("green_tea")
        val withDefault = dictionary.copy(
            canonicalNames = dictionary.canonicalNames + (greenTea to "緑茶"),
            aliases = dictionary.aliases + ("お茶" to Alias(greenTea, Match.DEFAULT)) + ("緑茶" to Alias(greenTea, Match.PREFIX)),
        )
        val tea = product("お茶")
        val green = product("緑茶")
        val resolver = ProductResolver(listOf(tea, green), withDefault)

        assertThat(resolver.resolve(tea.id)).isEqualTo(ProductResolver.Resolution(ResolvedKey.OfProduct(tea.id), Reason.Self))
        assertThat(resolver.resolve(tea.id).key).isNotEqualTo(resolver.resolve(green.id).key)
    }
}
