package com.cheesecomer.cartmate.ui.products

import app.cash.turbine.test
import com.cheesecomer.cartmate.data.memory.InMemoryStore
import com.cheesecomer.cartmate.domain.model.UserId
import com.cheesecomer.cartmate.domain.product.NormalizationDictionary
import com.cheesecomer.cartmate.testing.MainDispatcherExtension
import com.cheesecomer.cartmate.testing.MutableClock
import com.cheesecomer.cartmate.testing.awaitUntil
import com.cheesecomer.cartmate.testing.createList
import com.cheesecomer.cartmate.testing.newItem
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlinx.coroutines.flow.flowOf

class ProductMergeViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val clock = MutableClock()
    private val store = InMemoryStore(clock) { UserId("taro") }
    private val listId = runBlocking { store.createList("スーパー") }
    private val viewModel = ProductMergeViewModel(store, store, store, clock, { flowOf(NormalizationDictionary.EMPTY) })

    private suspend fun productId(name: String) = store.observeProducts().first().first { it.displayName == name }.id

    @Test
    fun `自分に見える商品を一覧にする`() = runTest {
        store.addItem(newItem(listId, "牛乳"))
        store.addItem(newItem(listId, "ぎゅうにゅう"))
        viewModel.rows.test {
            assertThat(awaitUntil { it.size == 2 }.map { it.product.displayName }).containsExactly("ぎゅうにゅう", "牛乳")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `別の商品に統合し、統合を解除できる`() = runTest {
        store.addItem(newItem(listId, "牛乳"))
        store.addItem(newItem(listId, "ぎゅうにゅう"))
        val kana = productId("ぎゅうにゅう")
        val milk = productId("牛乳")
        viewModel.rows.test {
            awaitUntil { it.size == 2 }

            viewModel.merge(kana, milk)
            val merged = awaitUntil { rows -> rows.any { it.mergedIntoName != null } }.single { it.product.id == kana }
            assertThat(merged.mergedIntoName).isEqualTo("牛乳")

            viewModel.unmerge(kana)
            awaitUntil { rows -> rows.none { it.mergedIntoName != null } }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `統合の相手には、自分と、自分に統合されている商品を出さない`() = runTest {
        store.addItem(newItem(listId, "牛乳"))
        store.addItem(newItem(listId, "ぎゅうにゅう"))
        store.addItem(newItem(listId, "卵"))
        val milk = productId("牛乳")
        store.merge(productId("ぎゅうにゅう"), milk)
        viewModel.rows.test {
            awaitUntil { it.size == 3 }
            assertThat(viewModel.mergeTargets(milk).map { it.product.displayName }).containsExactly("卵")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `辞書の名寄せを外し、戻せる`() = runTest {
        store.addItem(newItem(listId, "たまご"))
        val kana = productId("たまご")
        viewModel.rows.test {
            awaitUntil { it.isNotEmpty() }
            viewModel.setDictionaryOptOut(kana, true)
            assertThat(awaitUntil { it.single().canOptIn }.single().product.dictionaryOptOut).isTrue()
            viewModel.setDictionaryOptOut(kana, false)
            awaitUntil { !it.single().canOptIn }
            cancelAndIgnoreRemainingEvents()
        }
    }
}
