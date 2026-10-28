package com.cheesecomer.cartmate.ui.listdetail

import kotlinx.coroutines.Dispatchers
import com.cheesecomer.cartmate.domain.product.NormalizationDictionary
import com.cheesecomer.cartmate.ui.suggestions.SuggestionSource
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import app.cash.turbine.turbineScope
import com.cheesecomer.cartmate.data.memory.InMemoryStore
import com.cheesecomer.cartmate.domain.input.AddItemEvent
import com.cheesecomer.cartmate.domain.input.AddItemState
import com.cheesecomer.cartmate.domain.model.ItemStatus
import com.cheesecomer.cartmate.domain.model.ListId
import com.cheesecomer.cartmate.domain.model.NewList
import com.cheesecomer.cartmate.domain.model.UserId
import com.cheesecomer.cartmate.domain.model.Visibility
import com.cheesecomer.cartmate.domain.repository.ShoppingListRepository
import com.cheesecomer.cartmate.domain.repository.DeleteListResult
import com.cheesecomer.cartmate.testing.MainDispatcherExtension
import com.cheesecomer.cartmate.testing.MutableClock
import com.cheesecomer.cartmate.testing.awaitUntil
import com.cheesecomer.cartmate.testing.createList
import com.cheesecomer.cartmate.testing.newItem
import com.cheesecomer.cartmate.ui.itementry.ItemEntryResult
import com.cheesecomer.cartmate.ui.itementry.LastVisibilityStore
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.flowOf
import com.cheesecomer.cartmate.domain.model.Member
import com.cheesecomer.cartmate.domain.model.MemberRole
import com.cheesecomer.cartmate.domain.repository.ItemRepository
import com.cheesecomer.cartmate.domain.repository.RejectedCheck
import com.cheesecomer.cartmate.domain.suggest.EarlyPurchaseNotice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.ExperimentalCoroutinesApi

class ListDetailViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val clock = MutableClock()
    /** store を操作している人。ほかのメンバーの操作を再現するときに切り替える */
    private var actor = UserId("taro")
    private val store = InMemoryStore(clock) { actor }
    private val rejectedChecks = MutableSharedFlow<RejectedCheck>(extraBufferCapacity = 8)
    private val members = MutableStateFlow(
        listOf(
            Member(UserId("taro"), MemberRole.OWNER, "山田 太郎", null, Instant.EPOCH),
            Member(UserId("hanako"), MemberRole.MEMBER, "山田 花子", null, Instant.EPOCH),
        ),
    )
    private val listId: ListId = runBlocking { store.createList("スーパー") }
    private val otherListId: ListId = runBlocking { store.createList("薬局") }

    private val lastVisibility = LastVisibilityStore()
    private val viewModel = viewModelFor(listId)

    private fun viewModelFor(listId: ListId) = ListDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("listId" to listId.value)),
        listRepository = store,
        itemRepository = object : ItemRepository by store {
            override fun observeRejectedChecks(): Flow<RejectedCheck> = rejectedChecks
        },
        lastVisibility = lastVisibility,
        clock = clock,
        currentUser = { UserId("taro") },
        memberDirectory = { members },
        suggestionSource = SuggestionSource(store, store, store, clock, { flowOf(NormalizationDictionary.EMPTY) }, Dispatchers.Main),
    )

    @Test
    fun `シートで商品名を入れて Enter を押すと数量1・単位「個」で追加され、シートは開いたまま入力欄が空になる`() = runTest {
        viewModel.uiState.test {
            assertThat(awaitUntil { !it.loading }.listName).isEqualTo("スーパー")

            viewModel.onAddItemEvent(AddItemEvent.FieldFocused)
            viewModel.onAddItemEvent(AddItemEvent.TextChanged("牛乳"))
            viewModel.onAddItemEvent(AddItemEvent.Submitted)

            val state = awaitUntil { it.items.isNotEmpty() }
            assertThat(state.addItem).isEqualTo(AddItemState.Open(""))
            val item = state.items.single()
            assertThat(item.inputName).isEqualTo("牛乳")
            assertThat(item.quantity).isEqualTo(1.0)
            assertThat(item.unit).isEqualTo("個")
            assertThat(item.visibility).isEqualTo(Visibility.SHARED)
        }
    }

    @Test
    fun `Enter による追加は、そのリストで直前に選んだ公開範囲を使う`() = runTest {
        lastVisibility.set(listId, Visibility.PRIVATE)
        lastVisibility.set(otherListId, Visibility.SHARED)
        viewModel.onAddItemEvent(AddItemEvent.FieldFocused)
        viewModel.onAddItemEvent(AddItemEvent.TextChanged("プレゼント"))
        viewModel.onAddItemEvent(AddItemEvent.Submitted)

        viewModel.uiState.test {
            assertThat(awaitUntil { it.items.isNotEmpty() }.items.single().visibility).isEqualTo(Visibility.PRIVATE)
        }
        assertThat(lastVisibility.get(listId)).isEqualTo(Visibility.PRIVATE)
    }

    @Test
    fun `プライベートのリストでは、Enter による追加はプライベートになる`() = runTest {
        val privateList = store.createList("自分用", Visibility.PRIVATE)
        val viewModel = viewModelFor(privateList)
        viewModel.onAddItemEvent(AddItemEvent.FieldFocused)
        viewModel.onAddItemEvent(AddItemEvent.TextChanged("目薬"))
        viewModel.onAddItemEvent(AddItemEvent.Submitted)

        viewModel.uiState.test {
            assertThat(awaitUntil { it.items.isNotEmpty() }.items.single().visibility).isEqualTo(Visibility.PRIVATE)
        }
    }

    @Test
    fun `「数量を設定して追加」で商品詳細入力画面を開き、やめて戻るとシートに戻る`() = runTest {
        turbineScope {
            val states = viewModel.uiState.testIn(backgroundScope)
            val navigation = viewModel.navigation.testIn(backgroundScope)
            viewModel.onAddItemEvent(AddItemEvent.FieldFocused)
            viewModel.onAddItemEvent(AddItemEvent.TextChanged("豆乳"))
            viewModel.onAddItemEvent(AddItemEvent.DetailRequested)

            assertThat(navigation.awaitItem()).isEqualTo(DetailNavigation.OpenEntry("豆乳", 1.0, "個"))
            states.awaitUntil { it.addItem == AddItemState.Entering("豆乳") }

            viewModel.onEntryResult(ItemEntryResult.CANCELLED)
            states.awaitUntil { it.addItem == AddItemState.Open("豆乳") }

            viewModel.onAddItemEvent(AddItemEvent.DetailRequested)
            navigation.awaitItem()
            viewModel.onEntryResult(ItemEntryResult.ADDED)
            states.awaitUntil { it.addItem == AddItemState.Idle("") }
            // そのあとのシートの候補の更新は、この確認の対象外
            states.cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ほかのリストの項目は表示しない`() = runTest {
        store.addItem(newItem(otherListId, "目薬"))
        store.addItem(newItem(listId, "牛乳"))

        viewModel.uiState.test {
            assertThat(awaitUntil { !it.loading }.items.map { it.inputName }).containsExactly("牛乳")
        }
    }

    @Test
    fun `チェックしても同じ位置に残り、元に戻せるメッセージが出る。元に戻すと購入が無効になる`() = runTest {
        store.addItem(newItem(listId, "牛乳"))
        clock.advance(Duration.ofSeconds(1))
        store.addItem(newItem(listId, "卵"))
        turbineScope {
            val states = viewModel.uiState.testIn(backgroundScope)
            val messages = viewModel.messages.testIn(backgroundScope)
            val item = states.awaitUntil { it.items.size == 2 }.items.first()

            viewModel.check(item)

            assertThat(messages.awaitItem()).isEqualTo(DetailMessage.Checked(item))
            val afterCheck = states.awaitUntil { it.items.first().status == ItemStatus.COMPLETED }
            assertThat(afterCheck.items.map { it.inputName }).containsExactly("牛乳", "卵").inOrder()

            viewModel.uncheck(item)

            states.awaitUntil { it.items.first().status == ItemStatus.OPEN }
            assertThat(store.observePurchases(Instant.EPOCH).first().single().active).isFalse()
        }
    }

    @Test
    fun `すでにチェック済みの項目をチェックすると、その旨のメッセージが出る`() = runTest {
        val itemId = store.addItem(newItem(listId, "牛乳"))
        val item = store.observeOpenItems().first().single()
        store.completeItem(itemId, clock.now)

        viewModel.messages.test {
            viewModel.check(item)
            assertThat(awaitItem()).isEqualTo(DetailMessage.AlreadyCompleted("牛乳", "山田 太郎", clock.now))
        }
    }

    @Test
    fun `削除すると元に戻せるメッセージが出る。元に戻すと項目が復活する`() = runTest {
        store.addItem(newItem(listId, "牛乳"))
        turbineScope {
            val states = viewModel.uiState.testIn(backgroundScope)
            val messages = viewModel.messages.testIn(backgroundScope)
            val item = states.awaitUntil { it.items.isNotEmpty() }.items.single()

            viewModel.delete(item)

            assertThat(messages.awaitItem()).isEqualTo(DetailMessage.Deleted(item))
            states.awaitUntil { it.items.isEmpty() }

            viewModel.undoDelete(item)

            assertThat(states.awaitUntil { it.items.isNotEmpty() }.items.map { it.id }).containsExactly(item.id)
        }
    }


    @Test
    fun `編集が拒否されて戻ると、その旨のメッセージが出る`() = runTest {
        viewModel.messages.test {
            viewModel.onEntryResult(ItemEntryResult.EDIT_REJECTED_COMPLETED)
            assertThat(awaitItem()).isEqualTo(DetailMessage.CannotEditCompleted)
            viewModel.onEntryResult(ItemEntryResult.EDIT_REJECTED_NOT_FOUND)
            assertThat(awaitItem()).isEqualTo(DetailMessage.EditedItemNotFound)
        }
    }

    @Test
    fun `リスト名と備考を変更できる。空白だけの名前には変更しない`() = runTest {
        viewModel.uiState.test {
            awaitUntil { !it.loading }

            viewModel.updateList("   ", "")
            viewModel.updateList(" イオン ", " 火曜市 ")

            val state = awaitUntil { it.listName != "スーパー" }
            assertThat(state.listName).isEqualTo("イオン")
            assertThat(state.listMemo).isEqualTo("火曜市")
        }
    }

    @Test
    fun `のこりの商品があるリストは削除できず、理由のメッセージが出る`() = runTest {
        store.addItem(newItem(listId, "牛乳"))
        turbineScope {
            val states = viewModel.uiState.testIn(backgroundScope)
            val messages = viewModel.messages.testIn(backgroundScope)
            states.awaitUntil { it.items.isNotEmpty() }

            assertThat(viewModel.canDeleteList()).isFalse()
            assertThat(messages.awaitItem()).isEqualTo(DetailMessage.CannotDeleteList)
        }
    }

    @Test
    fun `買い終わったリストに追加すると、前回のチェック済みを片付けて新しい項目だけを表示する`() = runTest {
        val milk = store.addItem(newItem(listId, "牛乳"))
        store.completeItem(milk, clock.now)
        viewModel.uiState.test {
            assertThat(awaitUntil { it.items.isNotEmpty() }.items.map { it.inputName }).containsExactly("牛乳")

            store.addItem(newItem(listId, "卵"))

            // リストと項目は別の購読なので、項目だけが先に反映された途中の状態を経ることがある。片付いた状態になるまで待つ
            val items = awaitUntil { state -> state.items.map { it.inputName } == listOf("卵") }.items
            assertThat(items.map { it.inputName }).containsExactly("卵")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `買い物の途中の追加では、チェック済みを残す`() = runTest {
        val milk = store.addItem(newItem(listId, "牛乳"))
        store.addItem(newItem(listId, "卵"))
        store.completeItem(milk, clock.now)
        viewModel.uiState.test {
            store.addItem(newItem(listId, "パン"))

            val items = awaitUntil { state -> state.items.any { it.inputName == "パン" } }.items
            assertThat(items.map { it.inputName }).containsExactly("牛乳", "卵", "パン").inOrder()
            assertThat(items.first { it.inputName == "牛乳" }.status).isEqualTo(ItemStatus.COMPLETED)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `シートで自分のみに切り替えると、Enter で追加した項目がプライベートになる`() = runTest {
        viewModel.uiState.test {
            val initial = awaitUntil { !it.loading }
            assertThat(initial.addVisibility).isEqualTo(Visibility.SHARED)
            assertThat(initial.addVisibilitySelectable).isTrue()

            viewModel.setAddVisibility(Visibility.PRIVATE)
            assertThat(awaitUntil { it.addVisibility == Visibility.PRIVATE }.addVisibility).isEqualTo(Visibility.PRIVATE)

            viewModel.onAddItemEvent(AddItemEvent.FieldFocused)
            viewModel.onAddItemEvent(AddItemEvent.TextChanged("プレゼント"))
            viewModel.onAddItemEvent(AddItemEvent.Submitted)

            val added = awaitUntil { state -> state.items.any { it.inputName == "プレゼント" } }.items.single()
            assertThat(added.visibility).isEqualTo(Visibility.PRIVATE)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `商品詳細入力画面で選んだ公開範囲が、シートの表示にも反映される`() = runTest {
        viewModel.uiState.test {
            awaitUntil { !it.loading }

            lastVisibility.set(listId, Visibility.PRIVATE)

            assertThat(awaitUntil { it.addVisibility == Visibility.PRIVATE }.addVisibility).isEqualTo(Visibility.PRIVATE)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `プライベートのリストでは公開範囲を選べず、常にプライベートで追加する`() = runTest {
        val privateList = store.createList(NewList("自分用", memo = null, visibility = Visibility.PRIVATE))
        val viewModel = viewModelFor(privateList)
        viewModel.uiState.test {
            val state = awaitUntil { !it.loading }
            assertThat(state.addVisibility).isEqualTo(Visibility.PRIVATE)
            assertThat(state.addVisibilitySelectable).isFalse()

            viewModel.setAddVisibility(Visibility.SHARED)
            assertThat(lastVisibility.get(privateList)).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `削除したリストでも、未購入の項目が残っていれば開いて項目を表示する`() = runTest {
        store.deleteList(listId)
        store.addItem(newItem(listId, "牛乳"))

        viewModel.uiState.test {
            val state = awaitUntil { !it.loading }
            assertThat(state.listName).isEqualTo("スーパー")
            assertThat(state.items.map { it.inputName }).containsExactly("牛乳")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `開いている間にほかのメンバーがチェックしたら、約3秒分をまとめて知らせる`() = runTest {
        val milk = store.addItem(newItem(listId, "牛乳"))
        val egg = store.addItem(newItem(listId, "卵"))
        turbineScope {
            val states = viewModel.uiState.testIn(backgroundScope)
            val messages = viewModel.messages.testIn(backgroundScope)
            states.awaitUntil { it.items.size == 2 }

            actor = UserId("hanako")
            store.completeItem(milk, clock.now)
            store.completeItem(egg, clock.now)
            actor = UserId("taro")

            assertThat(messages.awaitItem()).isEqualTo(DetailMessage.OthersChecked("山田 花子", "牛乳", othersCount = 1))
            states.cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `画面を開いた時点ですでにチェック済みの項目は知らせない`() = runTest {
        val milk = store.addItem(newItem(listId, "牛乳"))
        actor = UserId("hanako")
        store.completeItem(milk, clock.now)
        actor = UserId("taro")
        turbineScope {
            val states = viewModel.uiState.testIn(backgroundScope)
            val messages = viewModel.messages.testIn(backgroundScope)
            states.awaitUntil { it.items.isNotEmpty() }
            advanceTimeBy(5_000)
            messages.expectNoEvents()
        }
    }

    @Test
    fun `すでにチェック済みなら、誰がいつチェックしたかを知らせる`() = runTest {
        val milk = store.addItem(newItem(listId, "牛乳"))
        turbineScope {
            val states = viewModel.uiState.testIn(backgroundScope)
            val messages = viewModel.messages.testIn(backgroundScope)
            val item = states.awaitUntil { it.items.isNotEmpty() }.items.single()

            actor = UserId("hanako")
            store.completeItem(milk, clock.now)
            actor = UserId("taro")
            viewModel.check(item)

            val notices = listOf(messages.awaitItem(), messages.awaitItem())
            assertThat(notices).contains(DetailMessage.AlreadyCompleted("牛乳", "山田 花子", clock.now))
            states.cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `送ったチェックが拒否されたら知らせる。家族にいない人は名前なし`() = runTest {
        turbineScope {
            val states = viewModel.uiState.testIn(backgroundScope)
            val messages = viewModel.messages.testIn(backgroundScope)
            states.awaitUntil { !it.loading }

            rejectedChecks.emit(RejectedCheck.AlreadyCompleted("牛乳", UserId("kenta"), clock.now))
            assertThat(messages.awaitItem()).isEqualTo(DetailMessage.AlreadyCompleted("牛乳", checkerName = null, at = clock.now))

            rejectedChecks.emit(RejectedCheck.NotApplied("卵"))
            assertThat(messages.awaitItem()).isEqualTo(DetailMessage.CheckNotApplied("卵"))
        }
    }

    @Test
    fun `チェック済みの項目に、チェックした人の名前を出す`() = runTest {
        val milk = store.addItem(newItem(listId, "牛乳"))
        actor = UserId("hanako")
        store.completeItem(milk, clock.now)
        actor = UserId("taro")
        viewModel.uiState.test {
            val state = awaitUntil { it.items.isNotEmpty() }
            assertThat(state.checkerName(state.items.single())).isEqualTo("山田 花子")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `のこりの商品がないリストは削除でき、一覧に戻る`() = runTest {
        turbineScope {
            val states = viewModel.uiState.testIn(backgroundScope)
            val navigation = viewModel.navigation.testIn(backgroundScope)
            states.awaitUntil { !it.loading }

            assertThat(viewModel.canDeleteList()).isTrue()
            viewModel.deleteList()

            assertThat(navigation.awaitItem()).isEqualTo(DetailNavigation.ListDeleted)
            // 削除したリストは表示しなくなる
            states.awaitUntil { it.listName.isEmpty() }
        }
    }

    @Test
    fun `項目を動かすと並び順が変わる`() = runTest {
        val milk = store.addItem(newItem(listId, "牛乳"))
        val egg = store.addItem(newItem(listId, "卵"))
        viewModel.uiState.test {
            awaitUntil { it.items.size == 2 }

            viewModel.move(egg, previous = null, next = milk)

            awaitUntil { state -> state.items.map { it.inputName } == listOf("卵", "牛乳") }
        }
    }

    /** リストの削除だけを [delete] に差し替えた ViewModel */
    private fun viewModelDeleting(delete: suspend (ListId) -> DeleteListResult) = ListDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("listId" to listId.value)),
        listRepository = object : ShoppingListRepository by store {
            override suspend fun deleteList(id: ListId): DeleteListResult = delete(id)
        },
        itemRepository = object : ItemRepository by store {
            override fun observeRejectedChecks(): Flow<RejectedCheck> = rejectedChecks
        },
        lastVisibility = lastVisibility,
        clock = clock,
        currentUser = { UserId("taro") },
        memberDirectory = { members },
        suggestionSource = SuggestionSource(store, store, store, clock, { flowOf(NormalizationDictionary.EMPTY) }, Dispatchers.Main),
    )

    @Test
    fun `リストの削除に失敗しても落ちずに失敗を知らせる`() = runTest {
        // 書き込みの準備（キャッシュの読み取りなど）で例外が起きた
        val viewModel = viewModelDeleting { throw IllegalStateException("cache unavailable") }
        turbineScope {
            val states = viewModel.uiState.testIn(backgroundScope)
            val messages = viewModel.messages.testIn(backgroundScope)
            states.awaitUntil { !it.loading }

            viewModel.deleteList()

            assertThat(messages.awaitItem()).isEqualTo(DetailMessage.Failed)
        }
    }

    /** [name] を [listId] のリストで、[intervalDays] 日ごとに [times] 回買った。最後に買った日が今日 */
    private suspend fun bought(name: String, times: Int, intervalDays: Long, listId: ListId = this.listId) {
        repeat(times) { index ->
            if (index > 0) clock.advance(java.time.Duration.ofDays(intervalDays))
            val id = store.addItem(newItem(listId, name, quantity = 1.0, unit = "本"))
            store.completeItem(id, clock.instant())
        }
    }

    @Test
    fun `シートを開くと、時期が近い商品を周期と前回からの日数を添えて出す`() = runTest {
        bought("牛乳", times = 3, intervalDays = 10)
        clock.advance(java.time.Duration.ofDays(9))
        viewModel.uiState.test {
            viewModel.onAddItemEvent(AddItemEvent.FieldFocused)
            val items = awaitUntil { (it.suggestions as? SheetSuggestions.Items)?.rows?.isNotEmpty() == true }.suggestions as SheetSuggestions.Items
            val row = items.rows.single()
            assertThat(row.suggestion.name).isEqualTo("牛乳")
            assertThat(row.suggestion.lastQuantity).isEqualTo(1.0)
            assertThat(row.suggestion.lastUnit).isEqualTo("本")
            assertThat(row.note).isEqualTo(SuggestionNote.Due(intervalDays = 10, daysSinceLast = 9))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `入力すると補完に切り替わり、候補の＋で前回の数量と単位のまま追加する`() = runTest {
        bought("牛乳", times = 1, intervalDays = 0)
        viewModel.uiState.test {
            viewModel.onAddItemEvent(AddItemEvent.FieldFocused)
            viewModel.onAddItemEvent(AddItemEvent.TextChanged("ぎゅ"))
            awaitUntil { it.suggestions == SheetSuggestions.Items(emptyList()) }
            viewModel.onAddItemEvent(AddItemEvent.TextChanged("牛"))
            val row = (awaitUntil { (it.suggestions as? SheetSuggestions.Items)?.rows?.isNotEmpty() == true }.suggestions as SheetSuggestions.Items)
                .rows.single()
            // 今日買ったばかりなので、周期が分からなくても前回の購入日を添える
            assertThat(row.note).isEqualTo(SuggestionNote.Completion(alreadyAdded = false, early = EarlyPurchaseNotice(LocalDate.now(clock), null)))

            viewModel.onAddItemEvent(AddItemEvent.SuggestionAdded(row.suggestion))
            val added = awaitUntil { state -> state.items.any { it.status == com.cheesecomer.cartmate.domain.model.ItemStatus.OPEN } }
            val item = added.items.single { it.status == com.cheesecomer.cartmate.domain.model.ItemStatus.OPEN }
            assertThat(item.inputName).isEqualTo("牛乳")
            assertThat(item.unit).isEqualTo("本")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `関連する履歴がないリストでは、商品名を入力すると候補が出るとだけ表示する`() = runTest {
        bought("牛乳", times = 3, intervalDays = 10, listId = otherListId)
        clock.advance(java.time.Duration.ofDays(9))
        viewModel.uiState.test {
            viewModel.onAddItemEvent(AddItemEvent.FieldFocused)
            assertThat(awaitUntil { it.suggestions != SheetSuggestions.Loading }.suggestions).isEqualTo(SheetSuggestions.Hint)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ほかのメンバーがチェックした項目は、チェックを外さずに理由を知らせる`() = runTest {
        val id = store.addItem(newItem(listId, "牛乳"))
        actor = UserId("hanako")
        store.completeItem(id, clock.instant())
        actor = UserId("taro")
        val item = store.observeListItems(listId, Instant.EPOCH).first().single()
        viewModel.messages.test {
            viewModel.uncheck(item)
            assertThat(awaitItem()).isEqualTo(DetailMessage.CheckedByOther("山田 花子"))
        }
        val purchase = store.observePurchases(Instant.EPOCH).first().single()
        assertThat(purchase.active).isTrue()
        assertThat(purchase.purchasedBy).isEqualTo(UserId("hanako"))
    }

    @Test
    fun `自分がチェックした項目でも、購入履歴から削除したあとに外そうとすると、すでに外れていると知らせる`() = runTest {
        val id = store.addItem(newItem(listId, "ケーキ"))
        store.completeItem(id, clock.instant())
        val item = store.observeListItems(listId, Instant.EPOCH).first().single()
        store.deletePurchase(id)
        viewModel.messages.test {
            viewModel.uncheck(item)
            assertThat(awaitItem()).isEqualTo(DetailMessage.AlreadyOpen)
        }
    }

    @Test
    fun `候補を長押しすると商品の画面を開き、そこで非表示にして戻ると、シートを開き直して変更後の候補を出す`() = runTest {
        bought("牛乳", times = 3, intervalDays = 10)
        clock.advance(java.time.Duration.ofDays(9))
        turbineScope {
            val states = viewModel.uiState.testIn(backgroundScope)
            val navigation = viewModel.navigation.testIn(backgroundScope)
            viewModel.onAddItemEvent(AddItemEvent.FieldFocused)
            val row = (states.awaitUntil { (it.suggestions as? SheetSuggestions.Items)?.rows?.isNotEmpty() == true }.suggestions as SheetSuggestions.Items)
                .rows.single()
            val milk = store.observeProducts().first().single { it.displayName == "牛乳" }.id
            assertThat(row.suggestion.productId).isEqualTo(milk)

            viewModel.onAddItemEvent(AddItemEvent.SuggestionLongPressed(row.suggestion))
            assertThat(navigation.awaitItem()).isEqualTo(DetailNavigation.OpenProduct(milk))
            states.awaitUntil { it.addItem == AddItemState.Managing("") }

            // 商品の画面で「一時的に非表示」にした
            store.applyMergeChanges(listOf(com.cheesecomer.cartmate.domain.product.ProductMerge.Change.Hide(milk)))
            viewModel.onResumed()

            states.awaitUntil { it.addItem == AddItemState.Open("") && it.suggestions == SheetSuggestions.Hint }
            navigation.cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `シートを開いていないときにリスト詳細に戻っても、シートは開かない`() = runTest {
        viewModel.uiState.test {
            awaitUntil { it.addItem == AddItemState.Idle("") }
            viewModel.onResumed()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `追加済みの補完には、未購入の項目があるリストの名前を、開いているリストを先に添える`() = runTest {
        bought("牛乳", times = 1, intervalDays = 0)
        store.addItem(newItem(otherListId, "牛乳"))
        store.addItem(newItem(listId, "牛乳"))
        viewModel.uiState.test {
            viewModel.onAddItemEvent(AddItemEvent.FieldFocused)
            viewModel.onAddItemEvent(AddItemEvent.TextChanged("牛"))
            val row = (awaitUntil { (it.suggestions as? SheetSuggestions.Items)?.rows?.isNotEmpty() == true }.suggestions as SheetSuggestions.Items)
                .rows.single()
            assertThat(row.note).isEqualTo(SuggestionNote.Completion(alreadyAdded = true, early = EarlyPurchaseNotice(LocalDate.now(clock), null), addedLists = listOf("スーパー", "薬局")))
            cancelAndIgnoreRemainingEvents()
        }
    }
}
