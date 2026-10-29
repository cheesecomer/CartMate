package com.cheesecomer.cartmate.ui.itementry

import com.cheesecomer.cartmate.ui.suggestions.SuggestionSource
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cheesecomer.cartmate.data.memory.InMemoryStore
import com.cheesecomer.cartmate.domain.model.ListId
import com.cheesecomer.cartmate.domain.model.NewItem
import com.cheesecomer.cartmate.domain.model.NewList
import com.cheesecomer.cartmate.domain.model.UserId
import com.cheesecomer.cartmate.domain.model.Visibility
import com.cheesecomer.cartmate.ui.closeEntry
import com.cheesecomer.cartmate.ui.editEntryRoute
import com.cheesecomer.cartmate.ui.listdetail.AddItemTags
import com.cheesecomer.cartmate.ui.listdetail.ListDetailRoute
import com.cheesecomer.cartmate.ui.listdetail.ListDetailScreen
import com.cheesecomer.cartmate.ui.listdetail.ListDetailViewModel
import com.cheesecomer.cartmate.ui.newEntryRoute
import com.cheesecomer.cartmate.ui.rememberEntryResults
import com.cheesecomer.cartmate.ui.theme.CartMateTheme
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.flow.flowOf
import com.cheesecomer.cartmate.domain.product.NormalizationDictionary

/** リスト詳細と商品詳細入力画面の行き来。アプリと同じ Navigation の配線で確かめる */
@RunWith(AndroidJUnit4::class)
class ItemEntryFlowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val clock = Clock.systemUTC()
    private val store = InMemoryStore(clock) { UserId("taro") }
    private val suggestions = SuggestionSource(store, store, store, clock) { flowOf(NormalizationDictionary.EMPTY) }
    private val lastVisibility = LastVisibilityStore()

    private fun createList(visibility: Visibility = Visibility.SHARED) =
        runBlocking { store.createList(NewList("スーパー", null, visibility)) }

    private fun setContent(listId: ListId) {
        // アプリと同じく、キーボードの高さを画面側で受け取る（シートはキーボードが出てから表示する）
        composeRule.runOnUiThread {
            composeRule.activity.enableEdgeToEdge()
            composeRule.activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        composeRule.setContent {
            val navController = rememberNavController()
            CartMateTheme {
                NavHost(navController, startDestination = ListDetailRoute(listId.value)) {
                    composable<ListDetailRoute> { backStackEntry ->
                        val viewModel = viewModel {
                            ListDetailViewModel(createSavedStateHandle(), store, store, lastVisibility, clock, { UserId("taro") }, { flowOf(emptyList()) }, suggestions)
                        }
                        ListDetailScreen(
                            onBack = {},
                            onOpenEntry = { name, quantity, unit ->
                                navController.navigate(newEntryRoute(listId.value, name, quantity, unit))
                            },
                            onEditItem = { navController.navigate(editEntryRoute(listId.value, it)) },
                            entryResults = rememberEntryResults(backStackEntry),
                            viewModel = viewModel,
                        )
                    }
                    composable<ItemEntryRoute> { backStackEntry ->
                        val viewModel = viewModel {
                            ItemEntryViewModel(createSavedStateHandle(), store, store, lastVisibility, clock, { flowOf(emptyList()) }, store, store, { flowOf(NormalizationDictionary.EMPTY) }, suggestions)
                        }
                        ItemEntryScreen(onClose = { navController.closeEntry(backStackEntry, it) }, viewModel = viewModel)
                    }
                }
            }
        }
    }

    private fun items(listId: ListId) = runBlocking { store.observeListItems(listId, Instant.EPOCH).first() }

    /** 商品追加欄をタップし、サジェストのシートが表示されるまで待つ */
    private fun openSheet() {
        composeRule.onNodeWithTag(AddItemTags.FIELD).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithTag(AddItemTags.SHEET_INPUT).assertIsDisplayed() }.isSuccess
        }
    }

    private fun openEntryFor(name: String) {
        openSheet()
        composeRule.onNodeWithTag(AddItemTags.SHEET_INPUT).performTextInput(name)
        composeRule.onNodeWithText("数量を設定して追加").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { composeRule.onAllNodesWithText("商品の詳細入力").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun 数量_単位_公開範囲_備考を指定して追加すると_リスト詳細に戻り_末尾に追加され_商品追加欄は空になる() {
        val listId = createList()
        runBlocking { store.addItem(NewItem(listId, "牛乳", 1.0, "本", null, Visibility.SHARED)) }
        setContent(listId)

        openEntryFor("卵")
        composeRule.onNodeWithText("追加先:").assertIsDisplayed()
        composeRule.onNodeWithTag(ItemEntryTags.NAME).assertTextEquals("商品名", "卵")
        composeRule.onNodeWithContentDescription("数量を1増やす").performClick()
        composeRule.onNodeWithText("パック").performClick()
        composeRule.onNodeWithText("自分のみ").performScrollTo().performClick()
        composeRule.onNodeWithTag(ItemEntryTags.MEMO).performScrollTo().performTextInput("Lサイズ")
        composeRule.onNodeWithText("リストに追加する").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { items(listId).size == 2 }
        val added = items(listId).last()
        assertThat(added.inputName).isEqualTo("卵")
        assertThat(added.quantity).isEqualTo(2.0)
        assertThat(added.unit).isEqualTo("パック")
        assertThat(added.memo).isEqualTo("Lサイズ")
        assertThat(added.visibility).isEqualTo(Visibility.PRIVATE)
        // 画面が落ち着いたあと: リスト詳細に戻り、シートは閉じ、商品追加欄は空
        composeRule.waitForIdle()
        composeRule.onNodeWithText("商品の詳細入力").assertDoesNotExist()
        composeRule.onNodeWithTag(AddItemTags.SHEET_INPUT).assertDoesNotExist()
        composeRule.onNodeWithTag(AddItemTags.FIELD).assert(hasText("商品名を入力して追加…"))
        composeRule.onNode(hasText("卵") and hasContentDescription("プライベート")).assertIsDisplayed()
    }

    @Test
    fun 閉じるボタンでやめると_追加せずにシートに戻り_入力した商品名が残る() {
        val listId = createList()
        setContent(listId)

        openEntryFor("豆乳")
        composeRule.onNodeWithContentDescription("閉じる").performClick()

        composeRule.waitForIdle()
        composeRule.onNodeWithText("商品の詳細入力").assertDoesNotExist()
        composeRule.onNodeWithTag(AddItemTags.SHEET_INPUT).assertTextEquals("豆乳")
        assertThat(items(listId)).isEmpty()
    }

    @Test
    fun 戻るキーでやめると_リスト詳細より前には戻らずにシートに戻る() {
        val listId = createList()
        setContent(listId)

        openEntryFor("豆乳")
        Espresso.closeSoftKeyboard()
        Espresso.pressBack()

        composeRule.waitForIdle()
        composeRule.onNodeWithText("商品の詳細入力").assertDoesNotExist()
        composeRule.onNodeWithTag(AddItemTags.SHEET_INPUT).assertTextEquals("豆乳")
        assertThat(items(listId)).isEmpty()
    }

    @Test
    fun 自分のみを選んで追加すると_そのリストでの次のEnterによる追加もプライベートになる() {
        val listId = createList()
        setContent(listId)

        openEntryFor("プレゼント")
        composeRule.onNodeWithText("自分のみ").performScrollTo().performClick()
        composeRule.onNodeWithText("リストに追加する").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { items(listId).size == 1 }
        composeRule.waitForIdle()

        openSheet()
        composeRule.onNodeWithTag(AddItemTags.SHEET_INPUT).assertIsFocused()
        composeRule.onNodeWithTag(AddItemTags.SHEET_INPUT).performTextInput("カード")
        composeRule.onNodeWithTag(AddItemTags.SHEET_INPUT).performImeAction()

        composeRule.waitUntil(timeoutMillis = 5_000) { items(listId).size == 2 }
        assertThat(items(listId).map { it.visibility }).containsExactly(Visibility.PRIVATE, Visibility.PRIVATE)
    }

    @Test
    fun プライベートのリストでは公開範囲を選ぶ欄を表示せず_プライベートで追加する() {
        val listId = createList(Visibility.PRIVATE)
        setContent(listId)

        openEntryFor("目薬")
        composeRule.onNodeWithText("公開範囲").assertDoesNotExist()
        composeRule.onNodeWithText("（プライベートリスト）").assertIsDisplayed()
        composeRule.onNodeWithText("リストに追加する").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { items(listId).size == 1 }
        assertThat(items(listId).single().visibility).isEqualTo(Visibility.PRIVATE)
    }

    @Test
    fun メニューの編集から商品の編集を開き_更新すると内容が変わる_公開範囲は変えられない() {
        val listId = createList()
        runBlocking { store.addItem(NewItem(listId, "牛乳", 1.0, "本", "無調整", Visibility.SHARED)) }
        setContent(listId)

        composeRule.onNodeWithContentDescription("牛乳のメニュー").performClick()
        composeRule.onNodeWithText("編集").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { composeRule.onAllNodesWithText("商品の編集").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag(ItemEntryTags.NAME).performTextReplacement("低脂肪乳")
        composeRule.onNodeWithText("自分のみ").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithText("更新する").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { items(listId).single().inputName == "低脂肪乳" }
        val item = items(listId).single()
        assertThat(item.memo).isEqualTo("無調整")
        assertThat(item.visibility).isEqualTo(Visibility.SHARED)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("商品の編集").assertDoesNotExist()
        composeRule.onNodeWithText("低脂肪乳").assertIsDisplayed()
        // 編集から戻っても、サジェストのシートは開かない
        composeRule.onNodeWithTag(AddItemTags.SHEET_INPUT).assertDoesNotExist()
    }

    @Test
    fun 数量の数字をタップして直接入力すると_その数量で追加される() {
        val listId = createList()
        setContent(listId)

        openEntryFor("卵")
        composeRule.onNodeWithTag(ItemEntryTags.QUANTITY).performClick()
        composeRule.onNodeWithTag(ItemEntryTags.QUANTITY).assertIsFocused()
        composeRule.onNodeWithTag(ItemEntryTags.QUANTITY).performTextReplacement("12")
        composeRule.onNodeWithText("リストに追加する").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { items(listId).size == 1 }
        assertThat(items(listId).single().quantity).isEqualTo(12.0)
    }

    @Test
    fun 似た名前の商品があるときの同じ商品として扱うは_カードのどこを押してもチェックが切り替わる() {
        val listId = createList()
        runBlocking { store.addItem(NewItem(listId, "ヨーグルト", 1.0, "個", null, Visibility.SHARED)) }
        setContent(listId)

        openEntryFor("プレーンヨーグルト")
        val option = composeRule.onNode(hasText("「ヨーグルト」と同じ商品として扱う"))
        option.assertIsOff()

        // 説明の文を押しても、見出しを押しても切り替わる
        composeRule.onNodeWithText("購入履歴を合わせて、次の購入時期を予測します", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        option.assertIsOn()
        composeRule.onNodeWithText("「ヨーグルト」と同じ商品として扱う", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        option.assertIsOff()
    }

    @Test
    fun 数量が3桁の商品の編集を開くと_数量の数字が欠けずに表示される() {
        val listId = createList()
        runBlocking { store.addItem(NewItem(listId, "豚こま切れ肉", 300.0, "g", null, Visibility.SHARED)) }
        setContent(listId)

        composeRule.onNodeWithContentDescription("豚こま切れ肉のメニュー").performClick()
        composeRule.onNodeWithText("編集").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { composeRule.onAllNodesWithText("商品の編集").fetchSemanticsNodes().isNotEmpty() }

        // 画面が落ち着いたあと: 数量の数字は、欄の中に収まって描かれている
        composeRule.waitForIdle()
        val quantity = composeRule.onNodeWithTag(ItemEntryTags.QUANTITY)
        quantity.assertTextEquals("300")
        val node = quantity.fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val layout = layouts.single()
        val left = layout.getLineLeft(0)
        val right = layout.getLineRight(0)
        assertWithMessage("数量の数字（%s〜%s px）が、欄（幅 %s px）からはみ出している", left, right, node.size.width)
            .that(left >= 0f && right <= node.size.width).isTrue()
    }
}
