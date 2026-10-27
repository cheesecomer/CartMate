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
    private val store = InMemoryStore(clock) { UserId("taro") }
    private val listId = runBlocking { store.createList("スーパー") }
    private val catalog = ProductCatalog(store, store, store, clock) { flowOf(NormalizationDictionary.EMPTY) }

    private suspend fun add(vararg names: String) = names.forEach { store.addItem(newItem(listId, it)) }

    private suspend fun productId(name: String) = store.observeProducts().first().first { it.displayName == name }.id

    private suspend fun mergedInto(name: String) = store.observeProducts().first().first { it.displayName == name }.mergedInto

    private fun merging(target: ProductId) =
        MergeProductsViewModel(SavedStateHandle(mapOf("targetId" to target.value)), catalog, store)

    private fun detail(id: ProductId) =
        ProductDetailViewModel(SavedStateHandle(mapOf("productId" to id.value)), catalog, store)

    @Test
    fun `一覧には未統合の商品を出し、まとめている商品の件数を添える`() = runTest {
        add("豚バラ肉", "豚ばら肉", "豚バラ")
        val target = productId("豚バラ肉")
        store.applyMergeChanges(ProductMerge.mergeChanges(target, listOf(productId("豚ばら肉")), store.observeProducts().first())!!)
        ProductNamesViewModel(catalog).entries.test {
            val entries = awaitUntil { it != null && it.size == 2 }!!
            assertThat(entries.map { it.product.displayName }).containsExactly("豚バラ", "豚バラ肉")
            assertThat(entries.first { it.product.id == target }.members.map { it.displayName }).containsExactly("豚ばら肉")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `統合先に複数の商品を選んで一度にまとめ、統合先の画面に戻る`() = runTest {
        add("豚バラ肉", "豚ばら肉", "豚バラ", "牛乳")
        val target = productId("豚バラ肉")
        val viewModel = merging(target)
        viewModel.events.test {
            viewModel.state.test {
                awaitUntil { it.candidates.size == 3 }
                viewModel.toggle(productId("豚ばら肉"))
                viewModel.toggle(productId("豚バラ"))
                assertThat(awaitUntil { it.selected.size == 2 }.canMerge).isTrue()
                viewModel.merge()
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
            awaitUntil { it.candidates.size == 1 }
            viewModel.toggle(middle)
            awaitUntil { it.selected.size == 1 }
            viewModel.merge()
            awaitUntil { it.submitted }
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(mergedInto("豚ばら肉")).isEqualTo(target)
        assertThat(mergedInto("豚バラ")).isEqualTo(target)
    }

    @Test
    fun `18件を超えては選べない`() = runTest {
        add("統合先", *(1..19).map { "商品$it" }.toTypedArray())
        val viewModel = merging(productId("統合先"))
        viewModel.events.test {
            viewModel.state.test {
                val candidates = awaitUntil { it.candidates.size == 19 }.candidates
                candidates.forEach { viewModel.toggle(it.product.id) }
                assertThat(awaitUntil { it.selected.size == ProductMerge.MAX_SELECTION }.selected).hasSize(ProductMerge.MAX_SELECTION)
                cancelAndIgnoreRemainingEvents()
            }
            assertThat(awaitItem()).isEqualTo(MergeProductsViewModel.Event.TooMany)
        }
    }

    @Test
    fun `まとめる操作は一度だけ送る`() = runTest {
        add("豚バラ肉", "豚ばら肉")
        val viewModel = merging(productId("豚バラ肉"))
        viewModel.events.test {
            viewModel.state.test {
                awaitUntil { it.candidates.size == 1 }
                viewModel.toggle(productId("豚ばら肉"))
                awaitUntil { it.selected.size == 1 }
                viewModel.merge()
                assertThat(awaitUntil { it.submitted }.canMerge).isFalse()
                viewModel.merge()
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
}
