package com.cheesecomer.cartmate.domain.product

import com.cheesecomer.cartmate.domain.model.Product
import com.cheesecomer.cartmate.domain.model.ProductId
import com.cheesecomer.cartmate.domain.text.NameNormalizer
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class ProductHidingTest {

    private val hiddenAt = Instant.parse("2026-10-20T00:00:00Z")

    private fun product(name: String, mergedInto: Product? = null, hiddenAt: Instant? = null) = Product(
        id = ProductId(NameNormalizer.productId(name)),
        displayName = name,
        normalizedName = NameNormalizer.normalizedName(name),
        searchKey = NameNormalizer.searchKey(name),
        createdAt = Instant.EPOCH,
        mergedInto = mergedInto?.id,
        hiddenAt = hiddenAt,
    )

    private fun usage(vararg uses: Pair<Product, Instant>) =
        ProductUsage(uses.mapTo(HashSet()) { it.first.id }, uses.associate { (product, at) -> product.id to at })

    @Test
    fun `非表示にした商品は、そのあとに使われていなければ非表示`() {
        val test = product("テスト", hiddenAt = hiddenAt)
        val milk = product("牛乳")
        val hidden = ProductHiding.hiddenRoots(listOf(test, milk), usage(test to hiddenAt.minusSeconds(60), milk to hiddenAt))
        assertThat(hidden).containsExactly(test.id)
    }

    @Test
    fun `非表示にしたあとに同じ名前を追加すると、書き込みなしでまた表示する`() {
        val putty = product("パテ", hiddenAt = hiddenAt)
        assertThat(ProductHiding.hiddenRoots(listOf(putty), usage(putty to hiddenAt.plusSeconds(1)))).isEmpty()
    }

    @Test
    fun `まとめている商品が非表示のあとに使われても、また表示する`() {
        val target = product("テストこ", hiddenAt = hiddenAt)
        val member = product("テスト", mergedInto = target)
        assertThat(ProductHiding.hiddenRoots(listOf(target, member), usage(member to hiddenAt.plusSeconds(1)))).isEmpty()
    }

    @Test
    fun `統合先が非表示なら、まとめている商品も非表示`() {
        val target = product("テストこ", hiddenAt = hiddenAt)
        val member = product("テスト", mergedInto = target)
        val products = listOf(target, member)
        val hidden = ProductHiding.hiddenRoots(products, ProductUsage.EMPTY)
        assertThat(ProductHiding.isHidden(member.id, products, hidden)).isTrue()
    }

    @Test
    fun `統合されている商品の非表示の時刻は使わない`() {
        val target = product("牛乳")
        val member = product("ぎゅうにゅう", mergedInto = target, hiddenAt = hiddenAt)
        assertThat(ProductHiding.hiddenRoots(listOf(target, member), ProductUsage.EMPTY)).isEmpty()
    }
}
