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

    private fun product(name: String, mergedInto: Product? = null, optOut: Boolean = false, hiddenAt: Instant? = null): Product = Product(
        id = ProductId(NameNormalizer.productId(name)),
        displayName = name,
        normalizedName = NameNormalizer.normalizedName(name),
        searchKey = NameNormalizer.searchKey(name),
        createdAt = Instant.EPOCH,
        mergedInto = mergedInto?.id,
        dictionaryOptOut = optOut,
        hiddenAt = hiddenAt,
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

    @Test
    fun `非表示の商品は一覧とまとめる候補に出さず、非表示の商品に出す`() {
        val milk = product("牛乳")
        val test = product("テスト", hiddenAt = Instant.EPOCH)
        val all = listOf(milk, test)
        val hidden = setOf(test.id)
        assertThat(ProductMerge.entries(all, ids(milk, test), dictionary, hidden).map { it.product }).containsExactly(milk)
        assertThat(ProductMerge.hiddenEntries(all, ids(milk, test), dictionary, hidden).map { it.product }).containsExactly(test)
        assertThat(ProductMerge.candidates(milk.id, all, ids(milk, test), dictionary, hidden)).isEmpty()
        // 一時的に非表示の商品も、通常と同じ統合先の画面で開く
        val detail = ProductMerge.detail(test.id, all, ids(milk, test), dictionary, hidden) as ProductMerge.Detail.Target
        assertThat(detail.entry.mode).isEqualTo(ProductMerge.SuggestionMode.TEMPORARILY_HIDDEN)
    }

    @Test
    fun `統合されている商品は非表示にできない`() {
        val milk = product("牛乳")
        val kana = product("ぎゅうにゅう", mergedInto = milk)
        assertThat(ProductMerge.hideChanges(kana.id, listOf(milk, kana))).isNull()
        assertThat(ProductMerge.hideChanges(milk.id, listOf(milk, kana))).containsExactly(Change.Hide(milk.id))
    }

    @Test
    fun `名前の訂正で、正しい名前の商品がまだなければ作ってまとめる（1件目の記録の訂正）`() {
        val typo = product("牛入")
        val milkId = ProductId(NameNormalizer.productId("牛乳"))
        assertThat(ProductMerge.renameChanges(typo.id, "牛乳", listOf(typo)))
            .containsExactly(Change.Create(milkId, "牛乳"), Change.Merge(typo.id, milkId))
    }

    @Test
    fun `名前の訂正で、正しい名前の商品があればその統合先にまとめ、まとまっていた商品も移す`() {
        val dairy = product("乳製品")
        val milk = product("牛乳", mergedInto = dairy)
        val typo = product("牛入")
        val underTypo = product("ぎゅうにう", mergedInto = typo)
        assertThat(ProductMerge.renameChanges(typo.id, "牛乳", listOf(dairy, milk, typo, underTypo)))
            .containsExactly(Change.Merge(typo.id, dairy.id), Change.Repoint(underTypo.id, dairy.id))
    }

    @Test
    fun `名前の訂正で、正しい名前の商品が非表示ならまた表示する`() {
        val milk = product("牛乳", hiddenAt = Instant.EPOCH)
        val typo = product("牛入")
        assertThat(ProductMerge.renameChanges(typo.id, "牛乳", listOf(milk, typo), hidden = setOf(milk.id)))
            .containsExactly(Change.Merge(typo.id, milk.id), Change.Show(milk.id))
    }

    @Test
    fun `名前の訂正で、正しい名前の商品がこの商品にまとめられていれば、入れ替えて統合先にする`() {
        val typo = product("牛入")
        val milk = product("牛乳", mergedInto = typo)
        val other = product("ぎゅうにゅう", mergedInto = typo)
        assertThat(ProductMerge.renameChanges(typo.id, "牛乳", listOf(typo, milk, other))).containsExactly(
            Change.Unmerge(milk.id),
            Change.Merge(typo.id, milk.id),
            Change.Repoint(other.id, milk.id),
        )
    }

    @Test
    fun `同じ名前（正規化すると同じ）や空の名前、統合されている商品の名前は訂正できない`() {
        val milk = product("牛乳")
        val kana = product("ぎゅうにゅう", mergedInto = milk)
        val products = listOf(milk, kana)
        assertThat(ProductMerge.renameChanges(milk.id, "牛乳 ", products)).isNull()
        assertThat(ProductMerge.renameChanges(milk.id, "  ", products)).isNull()
        assertThat(ProductMerge.renameChanges(kana.id, "牛にゅう", products)).isNull()
    }

    @Test
    fun `常に非表示の商品は一覧から外れて非表示の商品に出て、一時的な非表示より優先する`() {
        val milk = product("牛乳")
        val curtain = product("カーテンフック").copy(suggestionExcluded = true)
        val putty = product("パテ", hiddenAt = Instant.EPOCH)
        val all = listOf(milk, curtain, putty)
        val visible = ids(milk, curtain, putty)
        val hidden = setOf(putty.id)
        assertThat(ProductMerge.entries(all, visible, dictionary, hidden).map { it.product }).containsExactly(milk)
        assertThat(ProductMerge.hiddenEntries(all, visible, dictionary, hidden).associate { it.product.displayName to it.mode })
            .containsExactly("カーテンフック", ProductMerge.SuggestionMode.ALWAYS_HIDDEN, "パテ", ProductMerge.SuggestionMode.TEMPORARILY_HIDDEN)
        assertThat(ProductMerge.candidates(milk.id, all, visible, dictionary, hidden)).isEmpty()
        // 常に非表示の商品を開くと、設定を変えられる統合先の画面
        assertThat(ProductMerge.detail(curtain.id, all, visible, dictionary, hidden)).isInstanceOf(ProductMerge.Detail.Target::class.java)
    }

    @Test
    fun `名前の訂正で新しく作る商品には、常に非表示を引き継ぐ`() {
        val vague = product("カーテンに引っ掛けるやつ").copy(suggestionExcluded = true)
        val hookId = ProductId(NameNormalizer.productId("カーテンフック"))
        assertThat(ProductMerge.renameChanges(vague.id, "カーテンフック", listOf(vague)))
            .contains(Change.Create(hookId, "カーテンフック", alwaysHidden = true))
        assertThat(ProductMerge.renameTarget(vague.id, "カーテンフック", listOf(vague))).isNull()
    }

    @Test
    fun `名前の訂正で既にある商品にまとめるときは、その統合先を返す（設定が統合先のものになる）`() {
        val hook = product("カーテンフック")
        val vague = product("カーテンに引っ掛けるやつ").copy(suggestionExcluded = true)
        assertThat(ProductMerge.renameTarget(vague.id, "カーテンフック", listOf(hook, vague))).isEqualTo(hook)
    }
}

