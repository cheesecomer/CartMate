package com.cheesecomer.cartmate.domain.product

import com.cheesecomer.cartmate.domain.model.Product
import com.cheesecomer.cartmate.domain.model.ProductId
import com.cheesecomer.cartmate.domain.product.NormalizationDictionary.Alias
import com.cheesecomer.cartmate.domain.product.NormalizationDictionary.Match
import com.cheesecomer.cartmate.domain.product.ProductMerge.Change
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

    private fun product(name: String, mergedInto: Product? = null, optOut: Boolean = false): Product = Product(
        id = ProductId(NameNormalizer.productId(name)),
        displayName = name,
        normalizedName = NameNormalizer.normalizedName(name),
        searchKey = NameNormalizer.searchKey(name),
        createdAt = Instant.EPOCH,
        mergedInto = mergedInto?.id,
        dictionaryOptOut = optOut,
    )

    private fun ids(vararg products: Product) = products.map { it.id }.toSet()

    @Test
    fun `一覧には未統合の商品を出し、まとめている商品を多段の統合もたどって添える`() {
        val target = product("豚バラ肉")
        val middle = product("豚ばら肉", mergedInto = target)
        val leaf = product("豚バラ", mergedInto = middle)
        val all = listOf(target, middle, leaf)
        val entries = ProductMerge.entries(all, ids(target, middle, leaf), dictionary)
        assertThat(entries.map { it.product }).containsExactly(target)
        assertThat(entries.single().members).containsExactly(middle, leaf)
    }

    @Test
    fun `自分に見えない商品は、一覧にもまとめている商品にも出さない`() {
        val milk = product("牛乳")
        val secret = product("プレゼント")
        val hidden = product("ぎゅうにゅう", mergedInto = milk)
        val entries = ProductMerge.entries(listOf(milk, secret, hidden), ids(milk), dictionary)
        assertThat(entries.map { it.product }).containsExactly(milk)
        assertThat(entries.single().members).isEmpty()
    }

    @Test
    fun `統合先が見えなくても、まとめている商品が見えれば統合先を出す`() {
        val milk = product("牛乳")
        val kana = product("ぎゅうにゅう", mergedInto = milk)
        assertThat(ProductMerge.entries(listOf(milk, kana), ids(kana), dictionary).map { it.product }).containsExactly(milk)
    }

    @Test
    fun `辞書の名寄せは、同じ品目にまとまる名前を添えて手動の統合と分けて示す`() {
        val kanji = product("卵")
        val kana = product("たまご")
        val entries = ProductMerge.entries(listOf(kanji, kana), ids(kanji, kana), dictionary)
        val group = entries.first { it.product == kana }.dictionary!!
        assertThat(group.canonicalName).isEqualTo("卵")
        assertThat(group.names).containsExactly("たまご", "卵")
        assertThat(group.isMeaningful).isTrue()
        assertThat(entries.first { it.product == kana }.members).isEmpty()
    }

    @Test
    fun `辞書の名前と同じ名前の商品が1つだけなら、辞書の名寄せは出さない`() {
        val kanji = product("卵")
        val group = ProductMerge.entries(listOf(kanji), ids(kanji), dictionary).single().dictionary!!
        assertThat(group.isMeaningful).isFalse()
    }

    @Test
    fun `辞書の名寄せを外した商品には、辞書の名寄せを出さない`() {
        val kana = product("たまご", optOut = true)
        val entry = ProductMerge.entries(listOf(kana), ids(kana), dictionary).single()
        assertThat(entry.dictionary).isNull()
        assertThat(entry.product.dictionaryOptOut).isTrue()
    }

    @Test
    fun `統合されている商品を開くと、たどった先の統合先を出す`() {
        val target = product("豚バラ肉")
        val middle = product("豚ばら肉", mergedInto = target)
        val leaf = product("豚バラ", mergedInto = middle)
        val all = listOf(target, middle, leaf)
        assertThat(ProductMerge.detail(leaf.id, all, ids(target, middle, leaf), dictionary))
            .isEqualTo(ProductMerge.Detail.Merged(leaf, target))
        assertThat(ProductMerge.detail(target.id, all, ids(target, middle, leaf), dictionary))
            .isInstanceOf(ProductMerge.Detail.Target::class.java)
    }

    @Test
    fun `まとめる候補には、統合先そのものとすでにまとまっている商品を出さない`() {
        val target = product("豚バラ肉")
        val merged = product("豚ばら肉", mergedInto = target)
        val other = product("豚バラ")
        val candidates = ProductMerge.candidates(target.id, listOf(target, merged, other), ids(target, merged, other), dictionary)
        assertThat(candidates.map { it.product }).containsExactly(other)
    }

    @Test
    fun `複数の商品をまとめて統合し、それぞれにまとまっていた商品を同じ統合先に付け替える`() {
        val target = product("豚バラ肉")
        val a = product("豚ばら肉")
        val b = product("豚バラ")
        val underA = product("ぶたばら", mergedInto = a)
        val changes = ProductMerge.mergeChanges(target.id, listOf(a.id, b.id), listOf(target, a, b, underA))
        assertThat(changes).containsExactly(
            Change.Merge(a.id, target.id),
            Change.Repoint(underA.id, target.id),
            Change.Merge(b.id, target.id),
        )
    }

    @Test
    fun `統合先そのもの、統合済みの商品、統合済みの統合先は選べない`() {
        val target = product("豚バラ肉")
        val merged = product("豚ばら肉", mergedInto = target)
        val other = product("豚バラ")
        val products = listOf(target, merged, other)
        assertThat(ProductMerge.mergeChanges(target.id, listOf(target.id), products)).isNull()
        assertThat(ProductMerge.mergeChanges(target.id, listOf(merged.id), products)).isNull()
        assertThat(ProductMerge.mergeChanges(merged.id, listOf(other.id), products)).isNull()
        assertThat(ProductMerge.mergeChanges(target.id, emptyList(), products)).isNull()
    }

    @Test
    fun `一度にまとめられるのは18件まで`() {
        val target = product("統合先")
        val others = (1..19).map { product("商品$it") }
        val products = listOf(target) + others
        assertThat(ProductMerge.mergeChanges(target.id, others.take(18).map { it.id }, products)).hasSize(18)
        assertThat(ProductMerge.mergeChanges(target.id, others.map { it.id }, products)).isNull()
    }

    @Test
    fun `中間の商品を解除すると、その商品だけが外れ、下の商品は元の統合先に残る`() {
        val target = product("豚バラ肉")
        val middle = product("豚ばら肉", mergedInto = target)
        val leaf = product("豚バラ", mergedInto = middle)
        assertThat(ProductMerge.unmergeChanges(middle.id, listOf(target, middle, leaf)))
            .containsExactly(Change.Unmerge(middle.id), Change.Repoint(leaf.id, target.id))
    }

    @Test
    fun `3段の統合で中間を解除すると、直接まとまっていた商品を1つ上に付け替える`() {
        val root = product("A")
        val second = product("B", mergedInto = root)
        val third = product("C", mergedInto = second)
        val fourth = product("D", mergedInto = third)
        val products = listOf(root, second, third, fourth)
        assertThat(ProductMerge.unmergeChanges(third.id, products))
            .containsExactly(Change.Unmerge(third.id), Change.Repoint(fourth.id, second.id))
    }

    @Test
    fun `枝分かれしていても、直接まとまっている商品をすべて付け替える`() {
        val target = product("豚バラ肉")
        val middle = product("豚ばら肉", mergedInto = target)
        val leaf1 = product("豚バラ", mergedInto = middle)
        val leaf2 = product("ぶたばら", mergedInto = middle)
        val sibling = product("ブタバラ", mergedInto = target)
        assertThat(ProductMerge.unmergeChanges(middle.id, listOf(target, middle, leaf1, leaf2, sibling))).containsExactly(
            Change.Unmerge(middle.id),
            Change.Repoint(leaf1.id, target.id),
            Change.Repoint(leaf2.id, target.id),
        )
    }

    @Test
    fun `統合していない商品は解除できない`() {
        val target = product("豚バラ肉")
        assertThat(ProductMerge.unmergeChanges(target.id, listOf(target))).isNull()
    }
}
