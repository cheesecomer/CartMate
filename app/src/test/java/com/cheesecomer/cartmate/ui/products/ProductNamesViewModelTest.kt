package com.cheesecomer.cartmate.ui.products

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.cheesecomer.cartmate.data.memory.InMemoryStore
import com.cheesecomer.cartmate.domain.model.ProductId
import com.cheesecomer.cartmate.domain.model.UserId
import com.cheesecomer.cartmate.domain.product.NormalizationDictionary
import com.cheesecomer.cartmate.domain.product.ProductMerge
import com.cheesecomer.cartmate.testing.MainDispatcherExtension
import com.cheesecomer.cartmate.testing.MutableClock
import com.cheesecomer.cartmate.testing.awaitUntil
import com.cheesecomer.cartmate.testing.createList
import com.cheesecomer.cartmate.testing.newItem
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class ProductNamesViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val clock = MutableClock()
    /** store を操作している人。ほかのメンバーの購入を再現するときに切り替える */
    private var actor = UserId("taro")
    private val store = InMemoryStore(clock) { actor }
    private val listId = runBlocking { store.createList("スーパー") }
    private val catalog = ProductCatalog(store, store, store, clock) { flowOf(NormalizationDictionary.EMPTY) }

    private suspend fun add(vararg names: String) = names.forEach { store.addItem(newItem(listId, it)) }

    private suspend fun productId(name: String) = store.observeProducts().first().first { it.displayName == name }.id

    private suspend fun mergedInto(name: String) = store.observeProducts().first().first { it.displayName == name }.mergedInto

    private fun merging(target: ProductId) =
        MergeProductsViewModel(SavedStateHandle(mapOf("targetId" to target.value)), catalog, store)

    private fun detail(id: ProductId) =
        ProductDetailViewModel(SavedStateHandle(mapOf("productId" to id.value)), catalog, store, store, { UserId("taro") }, clock)

    @Test
    fun `一覧には未統合の商品を出し、まとめている商品の件数を添える`() = runTest {
        add("豚バラ肉", "豚ばら肉", "豚バラ")
        val target = productId("豚バラ肉")
        store.applyMergeChanges(ProductMerge.mergeChanges(target, listOf(productId("豚ばら肉")), store.observeProducts().first())!!)
        ProductNamesViewModel(catalog) { flowOf(emptyList()) }.lists.test {
            val entries = awaitUntil { it != null && it.entries.size == 2 }!!.entries
            assertThat(entries.map { it.product.displayName }).containsExactly("豚バラ", "豚バラ肉")
            assertThat(entries.first { it.product.id == target }.members.map { it.displayName }).containsExactly("豚ばら肉")
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** 候補の中の [name] の行 */
    private fun MergeProductsViewModel.State.choice(name: String) = choices.first { it.product.displayName == name }

    @Test
    fun `統合先に複数の商品を選んで一度にまとめ、統合先の画面に戻る`() = runTest {
        add("豚バラ肉", "豚ばら肉", "豚バラ", "牛乳")
        val target = productId("豚バラ肉")
        val viewModel = merging(target)
        viewModel.events.test {
            viewModel.state.test {
                val state = awaitUntil { it.choices.size == 3 }
                viewModel.toggle(state.choice("豚ばら肉"))
                viewModel.toggle(state.choice("豚バラ"))
                assertThat(awaitUntil { it.selectedCount == 2 }.canSubmit).isTrue()
                viewModel.submit()
                cancelAndIgnoreRemainingEvents()
            }
            assertThat(awaitItem()).isEqualTo(MergeProductsViewModel.Event.Done)
        }
        assertThat(mergedInto("豚ばら肉")).isEqualTo(target)
        assertThat(mergedInto("豚バラ")).isEqualTo(target)
        assertThat(mergedInto("牛乳")).isNull()
    }

    @Test
    fun `ほかの商品をまとめている商品を選ぶと、その下の商品も同じ統合先に移る`() = runTest {
        add("豚バラ肉", "豚ばら肉", "豚バラ")
        val middle = productId("豚ばら肉")
        store.applyMergeChanges(ProductMerge.mergeChanges(middle, listOf(productId("豚バラ")), store.observeProducts().first())!!)
        val target = productId("豚バラ肉")
        val viewModel = merging(target)
        viewModel.state.test {
            viewModel.toggle(awaitUntil { it.choices.size == 1 }.choice("豚ばら肉"))
            awaitUntil { it.selectedCount == 1 }
            viewModel.submit()
            awaitUntil { it.submitted }
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(mergedInto("豚ばら肉")).isEqualTo(target)
        assertThat(mergedInto("豚バラ")).isEqualTo(target)
    }

    @Test
    fun `今まとめている商品はチェックした状態で並び、外してまとめると解除し、新しく選んだ商品はまとめる`() = runTest {
        add("豚バラ肉", "豚ばら肉", "豚バラ", "ぶたばら")
        val target = productId("豚バラ肉")
        store.applyMergeChanges(ProductMerge.mergeChanges(target, listOf(productId("豚ばら肉"), productId("豚バラ")), store.observeProducts().first())!!)
        val viewModel = merging(target)
        viewModel.state.test {
            val state = awaitUntil { it.choices.size == 3 }
            assertThat(state.isChecked(state.choice("豚ばら肉"))).isTrue()
            assertThat(state.isChecked(state.choice("ぶたばら"))).isFalse()
            assertThat(state.selectedCount).isEqualTo(2)
            assertThat(state.canSubmit).isFalse()

            viewModel.toggle(state.choice("豚バラ"))
            viewModel.toggle(state.choice("ぶたばら"))
            val edited = awaitUntil { it.removed.size == 1 && it.added.size == 1 }
            assertThat(edited.selectedCount).isEqualTo(2)
            viewModel.submit()
            awaitUntil { it.submitted }
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(mergedInto("豚ばら肉")).isEqualTo(target)
        assertThat(mergedInto("豚バラ")).isNull()
        assertThat(mergedInto("ぶたばら")).isEqualTo(target)
    }

    @Test
    fun `すべて解除は、今まとめている商品も含めてチェックを外す`() = runTest {
        add("豚バラ肉", "豚ばら肉", "豚バラ")
        val target = productId("豚バラ肉")
        store.applyMergeChanges(ProductMerge.mergeChanges(target, listOf(productId("豚ばら肉")), store.observeProducts().first())!!)
        val viewModel = merging(target)
        viewModel.state.test {
            val state = awaitUntil { it.choices.size == 2 }
            viewModel.toggle(state.choice("豚バラ"))
            awaitUntil { it.selectedCount == 2 }
            viewModel.clearAll()
            val cleared = awaitUntil { it.selectedCount == 0 }
            assertThat(cleared.added).isEmpty()
            assertThat(cleared.removed).containsExactly(productId("豚ばら肉"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `件数に上限はなく、19件より多くても一度にまとめられる`() = runTest {
        add("統合先", *(1..25).map { "商品$it" }.toTypedArray())
        val target = productId("統合先")
        val viewModel = merging(target)
        viewModel.state.test {
            awaitUntil { it.choices.size == 25 }.choices.forEach { viewModel.toggle(it) }
            awaitUntil { it.selectedCount == 25 }
            viewModel.submit()
            awaitUntil { it.submitted }
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(store.observeProducts().first().count { it.mergedInto == target }).isEqualTo(25)
    }

    @Test
    fun `検索欄で候補を絞り込んでも、選択は残る`() = runTest {
        add("豚バラ肉", "豚ばら肉", "牛乳")
        val viewModel = merging(productId("豚バラ肉"))
        viewModel.state.test {
            viewModel.toggle(awaitUntil { it.choices.size == 2 }.choice("牛乳"))
            viewModel.search("バラ")
            val filtered = awaitUntil { it.query == "バラ" }
            assertThat(filtered.shownChoices.map { it.product.displayName }).containsExactly("豚ばら肉")
            assertThat(filtered.selectedCount).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `商品名の管理は、検索欄の文字で一覧と非表示の商品を絞り込む`() = runTest {
        add("豚バラ肉", "牛乳")
        val viewModel = ProductNamesViewModel(catalog) { flowOf(emptyList()) }
        viewModel.lists.test {
            awaitUntil { it != null && it.entries.size == 2 }
            viewModel.search("ぎゅう")
            val filtered = awaitUntil { it?.query == "ぎゅう" }!!
            assertThat(filtered.entries).isEmpty()
            viewModel.search("牛")
            assertThat(awaitUntil { it?.query == "牛" }!!.entries.map { it.product.displayName }).containsExactly("牛乳")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `まとめる操作は一度だけ送る`() = runTest {
        add("豚バラ肉", "豚ばら肉")
        val viewModel = merging(productId("豚バラ肉"))
        viewModel.events.test {
            viewModel.state.test {
                viewModel.toggle(awaitUntil { it.choices.size == 1 }.choice("豚ばら肉"))
                awaitUntil { it.selectedCount == 1 }
                viewModel.submit()
                assertThat(awaitUntil { it.submitted }.canSubmit).isFalse()
                viewModel.submit()
                cancelAndIgnoreRemainingEvents()
            }
            assertThat(awaitItem()).isEqualTo(MergeProductsViewModel.Event.Done)
            expectNoEvents()
        }
    }

    @Test
    fun `統合先の画面で中間の商品を解除すると、その商品だけが外れる`() = runTest {
        add("豚バラ肉", "豚ばら肉", "豚バラ")
        val target = productId("豚バラ肉")
        val middle = productId("豚ばら肉")
        // 「豚バラ」→「豚ばら肉」→「豚バラ肉」の多段の統合（以前の画面で作られたもの）
        store.applyMergeChanges(listOf(ProductMerge.Change.Merge(productId("豚バラ"), middle), ProductMerge.Change.Merge(middle, target)))
        val viewModel = detail(target)
        viewModel.state.test {
            val loaded = awaitUntil { it is ProductDetailViewModel.State.Loaded } as ProductDetailViewModel.State.Loaded
            val entry = (loaded.detail as ProductMerge.Detail.Target).entry
            assertThat(entry.members.map { it.displayName }).containsExactly("豚ばら肉", "豚バラ")
            viewModel.unmerge(middle)
            val after = awaitUntil {
                ((it as? ProductDetailViewModel.State.Loaded)?.detail as? ProductMerge.Detail.Target)?.entry?.members?.size == 1
            } as ProductDetailViewModel.State.Loaded
            assertThat((after.detail as ProductMerge.Detail.Target).entry.members.map { it.displayName }).containsExactly("豚バラ")
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(mergedInto("豚ばら肉")).isNull()
        assertThat(mergedInto("豚バラ")).isEqualTo(target)
    }

    @Test
    fun `非表示にすると一覧から外れて非表示の商品に移り、同じ名前をまた追加すると一覧に戻る`() = runTest {
        add("牛乳", "テスト")
        val test = productId("テスト")
        val names = ProductNamesViewModel(catalog) { flowOf(emptyList()) }
        names.lists.test {
            awaitUntil { it != null && it.entries.size == 2 }
            detail(test).setMode(ProductMerge.SuggestionMode.TEMPORARILY_HIDDEN)
            val hidden = awaitUntil { it != null && it.hidden.isNotEmpty() }!!
            assertThat(hidden.entries.map { it.product.displayName }).containsExactly("牛乳")
            assertThat(hidden.hidden.map { it.entry.product.displayName }).containsExactly("テスト")

            // 非表示にしたあとに、同じ名前をまた追加した
            clock.advance(java.time.Duration.ofMinutes(1))
            add("テスト")
            val shown = awaitUntil { it != null && it.hidden.isEmpty() }!!
            assertThat(shown.entries.map { it.product.displayName }).containsExactly("テスト", "牛乳")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `非表示の商品は表示するで一覧に戻る`() = runTest {
        add("テスト")
        val test = productId("テスト")
        val viewModel = detail(test)
        viewModel.state.test {
            fun mode(state: ProductDetailViewModel.State) =
                ((state as? ProductDetailViewModel.State.Loaded)?.detail as? ProductMerge.Detail.Target)?.entry?.mode
            awaitUntil { mode(it) == ProductMerge.SuggestionMode.NORMAL }
            viewModel.setMode(ProductMerge.SuggestionMode.TEMPORARILY_HIDDEN)
            // 一時的に非表示にしても、同じ統合先の画面のまま、出し方だけが変わる
            awaitUntil { mode(it) == ProductMerge.SuggestionMode.TEMPORARILY_HIDDEN }
            viewModel.show()
            awaitUntil { mode(it) == ProductMerge.SuggestionMode.NORMAL }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `誤字の名前を訂正すると、正しい名前の商品を作ってまとめ、その商品の画面に移る`() = runTest {
        add("牛入")
        val typo = productId("牛入")
        val viewModel = detail(typo)
        viewModel.events.test {
            viewModel.state.test {
                awaitUntil { it is ProductDetailViewModel.State.Loaded }
                viewModel.rename("牛乳")
                cancelAndIgnoreRemainingEvents()
            }
            val renamed = awaitItem() as ProductDetailViewModel.Event.Renamed
            assertThat(renamed.target).isEqualTo(productId("牛乳"))
        }
        assertThat(mergedInto("牛入")).isEqualTo(productId("牛乳"))
        ProductNamesViewModel(catalog) { flowOf(emptyList()) }.lists.test {
            val entries = awaitUntil { it != null && it.entries.isNotEmpty() }!!.entries
            assertThat(entries.map { it.product.displayName }).containsExactly("牛乳")
            assertThat(entries.single().members.map { it.displayName }).containsExactly("牛入")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `常に非表示は再購入しても戻らず、一時的な非表示は再購入で戻り、どちらも通常どおり表示に戻せる`() = runTest {
        add("カーテンフック", "パテ")
        val curtain = productId("カーテンフック")
        val putty = productId("パテ")
        detail(curtain).setMode(ProductMerge.SuggestionMode.ALWAYS_HIDDEN)
        detail(putty).setMode(ProductMerge.SuggestionMode.TEMPORARILY_HIDDEN)
        val names = ProductNamesViewModel(catalog) { flowOf(emptyList()) }
        names.lists.test {
            val hidden = awaitUntil { it != null && it.hidden.size == 2 }!!.hidden
            assertThat(hidden.associate { it.entry.product.displayName to it.entry.mode }).containsExactly(
                "カーテンフック", ProductMerge.SuggestionMode.ALWAYS_HIDDEN,
                "パテ", ProductMerge.SuggestionMode.TEMPORARILY_HIDDEN,
            )

            // どちらも再購入した
            clock.advance(java.time.Duration.ofMinutes(1))
            add("カーテンフック", "パテ")
            val after = awaitUntil { it != null && it.hidden.size == 1 }!!
            assertThat(after.hidden.single().entry.product.displayName).isEqualTo("カーテンフック")
            assertThat(after.entries.map { it.product.displayName }).containsExactly("パテ")

            detail(curtain).setMode(ProductMerge.SuggestionMode.NORMAL)
            awaitUntil { it != null && it.hidden.isEmpty() }
            cancelAndIgnoreRemainingEvents()
        }
        // 常に非表示を解除したら、設定した人も消える
        assertThat(store.observeProducts().first().first { it.id == curtain }.suggestionExcludedBy).isNull()
    }

    @Test
    fun `一時的に非表示から常に非表示に変えると、再購入しても戻らない`() = runTest {
        add("パテ")
        val putty = productId("パテ")
        detail(putty).setMode(ProductMerge.SuggestionMode.TEMPORARILY_HIDDEN)
        detail(putty).setMode(ProductMerge.SuggestionMode.ALWAYS_HIDDEN)
        clock.advance(java.time.Duration.ofMinutes(1))
        add("パテ")
        val product = store.observeProducts().first().first { it.id == putty }
        assertThat(product.suggestionExcluded).isTrue()
        assertThat(product.hiddenAt).isNull()
    }

    @Test
    fun `常に非表示の商品の名前を訂正して新しく作る商品は、常に非表示を引き継ぐ`() = runTest {
        add("カーテンに引っ掛けるやつ")
        val vague = productId("カーテンに引っ掛けるやつ")
        detail(vague).setMode(ProductMerge.SuggestionMode.ALWAYS_HIDDEN)
        val viewModel = detail(vague)
        viewModel.events.test {
            viewModel.state.test {
                awaitUntil { it is ProductDetailViewModel.State.Loaded }
                viewModel.rename("カーテンフック")
                cancelAndIgnoreRemainingEvents()
            }
            awaitItem()
        }
        assertThat(store.observeProducts().first().first { it.displayName == "カーテンフック" }.suggestionExcluded).isTrue()
    }

    private suspend fun buy(name: String) {
        val id = store.addItem(newItem(listId, name))
        store.completeItem(id, clock.instant())
    }

    @Test
    fun `商品の画面から自分の購入だけをまとめて削除でき、ほかのメンバーの購入は残る`() = runTest {
        buy("ケーキ")
        buy("ケーキ")
        actor = UserId("hanako")
        buy("ケーキ")
        actor = UserId("taro")
        val viewModel = detail(productId("ケーキ"))
        viewModel.state.test {
            val loaded = awaitUntil { (it as? ProductDetailViewModel.State.Loaded)?.ownPurchases == 2 } as ProductDetailViewModel.State.Loaded
            assertThat(loaded.othersPurchases).isEqualTo(1)
            viewModel.deleteMyPurchases()
            val after = awaitUntil { (it as? ProductDetailViewModel.State.Loaded)?.ownPurchases == 0 } as ProductDetailViewModel.State.Loaded
            assertThat(after.othersPurchases).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(store.observePurchases(java.time.Instant.EPOCH).first().map { it.purchasedBy }).containsExactly(UserId("hanako"))
    }

    @Test
    fun `自分の購入しかない商品をまとめて削除すると、商品は一覧から外れる`() = runTest {
        buy("ケーキ")
        buy("ケーキ")
        val viewModel = detail(productId("ケーキ"))
        viewModel.state.test {
            awaitUntil { (it as? ProductDetailViewModel.State.Loaded)?.ownPurchases == 2 }
            viewModel.deleteMyPurchases()
            assertThat(awaitUntil { it is ProductDetailViewModel.State.Gone }).isEqualTo(ProductDetailViewModel.State.Gone("ケーキ"))
            cancelAndIgnoreRemainingEvents()
        }
    }
}

