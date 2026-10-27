package com.cheesecomer.cartmate.ui.itementry

import com.cheesecomer.cartmate.ui.suggestions.SuggestionSource
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.cheesecomer.cartmate.data.memory.InMemoryStore
import com.cheesecomer.cartmate.domain.model.ItemId
import com.cheesecomer.cartmate.domain.model.ListId
import com.cheesecomer.cartmate.domain.model.UserId
import com.cheesecomer.cartmate.domain.model.Visibility
import com.cheesecomer.cartmate.testing.MainDispatcherExtension
import com.cheesecomer.cartmate.testing.MutableClock
import com.cheesecomer.cartmate.testing.createList
import com.cheesecomer.cartmate.testing.newItem
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Instant
import kotlinx.coroutines.flow.flowOf
import com.cheesecomer.cartmate.domain.product.NormalizationDictionary
import com.cheesecomer.cartmate.domain.product.ProductMerge
import com.cheesecomer.cartmate.testing.awaitUntil

class ItemEntryViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val clock = MutableClock()
    private val store = InMemoryStore(clock) { UserId("taro") }
    private val lastVisibility = LastVisibilityStore()
    private val listId: ListId = runBlocking { store.createList("スーパー") }

    private fun viewModel(
        listId: ListId = this.listId,
        itemId: ItemId? = null,
        name: String = "",
        quantity: String? = null,
        unit: String? = null,
    ) = ItemEntryViewModel(
        savedStateHandle = SavedStateHandle(
            mapOf("listId" to listId.value, "itemId" to itemId?.value, "name" to name, "quantity" to quantity, "unit" to unit),
        ),
        listRepository = store,
        itemRepository = store,
        lastVisibility = lastVisibility,
        clock = clock,
        memberDirectory = { flowOf(emptyList()) },
        productRepository = store,
        purchaseRepository = store,
        dictionaryProvider = { flowOf(NormalizationDictionary.EMPTY) },
        suggestionSource = SuggestionSource(store, store, store, clock) { flowOf(NormalizationDictionary.EMPTY) },
    )

    private suspend fun listItems(listId: ListId = this.listId) = store.observeListItems(listId, Instant.EPOCH).first()

    @Test
    fun `渡された商品名・数量・単位を初期値にし、入力した内容でリストの末尾に追加する`() = runTest {
        store.addItem(newItem(listId, "牛乳"))
        val viewModel = viewModel(name = "卵", quantity = "2", unit = "パック")
        val entry = viewModel.uiState.value.entry!!
        assertThat(listOf(entry.name, entry.quantity, entry.unit)).containsExactly("卵", "2", "パック").inOrder()
        assertThat(viewModel.uiState.value.listName).isEqualTo("スーパー")

        viewModel.results.test {
            viewModel.increment()
            viewModel.onMemoChange("Lサイズ")
            viewModel.confirm()
            assertThat(awaitItem()).isEqualTo(ItemEntryResult.ADDED)
        }

        val added = listItems().last()
        assertThat(added.inputName).isEqualTo("卵")
        assertThat(added.quantity).isEqualTo(3.0)
        assertThat(added.unit).isEqualTo("パック")
        assertThat(added.memo).isEqualTo("Lサイズ")
        assertThat(added.visibility).isEqualTo(Visibility.SHARED)
    }

    @Test
    fun `選んだ公開範囲で追加し、そのリストの次の追加の初期値にする`() = runTest {
        val viewModel = viewModel(name = "プレゼント")
        viewModel.onVisibilityChange(Visibility.PRIVATE)
        viewModel.results.test {
            viewModel.confirm()
            awaitItem()
        }

        assertThat(listItems().single().visibility).isEqualTo(Visibility.PRIVATE)
        assertThat(lastVisibility.get(listId)).isEqualTo(Visibility.PRIVATE)
        assertThat(viewModel(name = "カード").uiState.value.entry!!.visibility).isEqualTo(Visibility.PRIVATE)
    }

    @Test
    fun `プライベートのリストでは公開範囲を選べず、プライベートで追加し、直前の公開範囲も変えない`() = runTest {
        val privateList = store.createList("自分用", Visibility.PRIVATE)
        val viewModel = viewModel(listId = privateList, name = "目薬")
        assertThat(viewModel.uiState.value.entry!!.visibilitySelectable).isFalse()

        viewModel.results.test {
            viewModel.confirm()
            awaitItem()
        }

        assertThat(listItems(privateList).single().visibility).isEqualTo(Visibility.PRIVATE)
        assertThat(lastVisibility.get(privateList)).isNull()
    }

    @Test
    fun `新しい商品名で、似た名前の商品があれば表記ゆれのチップを未選択で出す`() = runTest {
        store.addItem(newItem(listId, "牛乳"))
        val viewModel = viewModel(name = "牛乳 1L")
        viewModel.mergeChip.test {
            val chip = awaitUntil { it != null }!!
            assertThat(chip.candidateName).isEqualTo("牛乳")
            assertThat(chip.selected).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `チップを選んで追加すると、新しい商品を似た名前の商品に統合して記録する`() = runTest {
        store.addItem(newItem(listId, "牛乳"))
        val viewModel = viewModel(name = "牛乳 1L")
        viewModel.mergeChip.test {
            awaitUntil { it != null }
            viewModel.toggleMerge()
            assertThat(awaitUntil { it?.selected == true }!!.selected).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.confirm()

        val products = store.observeProducts().first().associateBy { it.displayName }
        assertThat(products.getValue("牛乳 1L").mergedInto).isEqualTo(products.getValue("牛乳").id)
    }

    @Test
    fun `チップを選ばずに追加すると、統合しない`() = runTest {
        store.addItem(newItem(listId, "牛乳"))
        val viewModel = viewModel(name = "牛乳 1L")
        viewModel.mergeChip.test {
            awaitUntil { it != null }
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.confirm()

        val products = store.observeProducts().first().associateBy { it.displayName }
        assertThat(products.getValue("牛乳 1L").mergedInto).isNull()
    }

    @Test
    fun `すでにある商品名ではチップを出さない`() = runTest {
        store.addItem(newItem(listId, "牛乳"))
        val viewModel = viewModel(name = "牛乳")
        viewModel.mergeChip.test {
            assertThat(awaitItem()).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `商品名を変えて候補が変わったら、選択を外す`() = runTest {
        store.addItem(newItem(listId, "牛乳"))
        store.addItem(newItem(listId, "豆腐"))
        val viewModel = viewModel(name = "牛乳 1L")
        viewModel.mergeChip.test {
            awaitUntil { it != null }
            viewModel.toggleMerge()
            awaitUntil { it?.selected == true }

            viewModel.onNameChange("豆腐 絹ごし")
            val chip = awaitUntil { it?.candidateName == "豆腐" }!!
            assertThat(chip.selected).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `編集ではチップを出さない`() = runTest {
        store.addItem(newItem(listId, "牛乳"))
        val itemId = store.addItem(newItem(listId, "牛乳 1L"))
        store.applyMergeChanges(listOf(ProductMerge.Change.Unmerge(store.observeProducts().first().first { it.displayName == "牛乳 1L" }.id)))
        val viewModel = viewModel(itemId = itemId)
        viewModel.mergeChip.test {
            assertThat(awaitItem()).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `確定を続けて押しても、追加は1件だけ`() = runTest {
        val viewModel = viewModel(name = "牛乳")
        viewModel.results.test {
            viewModel.confirm()
            viewModel.confirm()
            assertThat(awaitItem()).isEqualTo(ItemEntryResult.ADDED)
        }
        assertThat(listItems()).hasSize(1)
    }

    @Test
    fun `数量が正しくなければ追加しない`() = runTest {
        val viewModel = viewModel(name = "牛乳")
        viewModel.onQuantityChange("0")
        viewModel.confirm()
        assertThat(listItems()).isEmpty()
    }

    @Test
    fun `編集は項目の内容を初期値にし、確定すると内容が変わる。公開範囲は変えられない`() = runTest {
        val itemId = store.addItem(newItem(listId, "牛乳", 1.0, "本", memo = "無調整"))
        val viewModel = viewModel(itemId = itemId)
        val entry = viewModel.uiState.value.entry!!
        assertThat(listOf(entry.name, entry.quantity, entry.unit, entry.memo)).containsExactly("牛乳", "1", "本", "無調整").inOrder()

        viewModel.results.test {
            viewModel.onNameChange("低脂肪乳")
            viewModel.onVisibilityChange(Visibility.PRIVATE)
            viewModel.confirm()
            assertThat(awaitItem()).isEqualTo(ItemEntryResult.UPDATED)
        }

        val updated = listItems().single()
        assertThat(updated.inputName).isEqualTo("低脂肪乳")
        assertThat(updated.visibility).isEqualTo(Visibility.SHARED)
    }

    @Test
    fun `編集中にチェックされた項目は更新せず、その結果を返す`() = runTest {
        val itemId = store.addItem(newItem(listId, "牛乳", 1.0, "本"))
        val viewModel = viewModel(itemId = itemId)
        store.completeItem(itemId, clock.now)

        viewModel.results.test {
            viewModel.onNameChange("低脂肪乳")
            viewModel.confirm()
            assertThat(awaitItem()).isEqualTo(ItemEntryResult.EDIT_REJECTED_COMPLETED)
        }
        assertThat(listItems().single().inputName).isEqualTo("牛乳")
    }

    @Test
    fun `編集しようとした項目がなければ、その結果を返して閉じる`() = runTest {
        viewModel(itemId = ItemId("none")).results.test {
            assertThat(awaitItem()).isEqualTo(ItemEntryResult.EDIT_REJECTED_NOT_FOUND)
        }
    }

    @Test
    fun `新しい商品では、前回の購入と過去の備考を候補として出す`() = runTest {
        repeat(3) { index ->
            if (index > 0) clock.advance(java.time.Duration.ofDays(14))
            val id = store.addItem(newItem(listId, "卵", quantity = 1.0, unit = "パック", memo = if (index == 2) "赤玉" else "特売なら2個"))
            store.completeItem(id, clock.instant())
        }
        clock.advance(java.time.Duration.ofDays(2))
        val viewModel = viewModel(name = "卵")
        viewModel.history.test {
            val history = awaitUntil { it?.previous != null }!!
            assertThat(history.previous!!.unit).isEqualTo("パック")
            assertThat(history.previous!!.typicalIntervalDays).isEqualTo(14)
            // 前回から2日しかたっていないので、早すぎる再購入
            assertThat(history.previous!!.early).isNotNull()
            assertThat(history.memoCandidates).containsExactly("赤玉", "特売なら2個").inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }
}

