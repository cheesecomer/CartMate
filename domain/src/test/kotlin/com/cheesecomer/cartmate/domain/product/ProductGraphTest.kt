package com.cheesecomer.cartmate.domain.product

import com.cheesecomer.cartmate.domain.model.Product
import com.cheesecomer.cartmate.domain.model.ProductId
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class ProductGraphTest {

    private fun product(id: String, mergedInto: String? = null) =
        Product(ProductId(id), id, id, id, Instant.EPOCH, mergedInto?.let(::ProductId))

    @Test
    fun `統合していなければ自分自身`() {
        assertThat(ProductGraph(listOf(product("milk"))).resolve(ProductId("milk"))).isEqualTo(ProductId("milk"))
    }

    @Test
    fun `統合先をたどる`() {
        val graph = ProductGraph(listOf(product("ぎゅうにゅう", "牛乳"), product("牛乳", "牛乳1L"), product("牛乳1L")))
        assertThat(graph.resolve(ProductId("ぎゅうにゅう"))).isEqualTo(ProductId("牛乳1L"))
    }

    @Test
    fun `知らない商品は自分自身`() {
        assertThat(ProductGraph(emptyList()).resolve(ProductId("x"))).isEqualTo(ProductId("x"))
    }

    @Test
    fun `統合先が手元にない（未同期）なら、たどれるところまで`() {
        assertThat(ProductGraph(listOf(product("a", "b"))).resolve(ProductId("a"))).isEqualTo(ProductId("b"))
    }

    @Test
    fun `循環していれば自分自身`() {
        val graph = ProductGraph(listOf(product("a", "b"), product("b", "a")))
        assertThat(graph.resolve(ProductId("a"))).isEqualTo(ProductId("a"))
    }

    @Test
    fun `深すぎる統合は自分自身`() {
        val chain = (0..11).map { product("p$it", if (it < 11) "p${it + 1}" else null) }
        val graph = ProductGraph(chain)
        assertThat(graph.resolve(ProductId("p0"))).isEqualTo(ProductId("p0"))
        assertThat(graph.resolve(ProductId("p2"))).isEqualTo(ProductId("p11"))
    }
}
