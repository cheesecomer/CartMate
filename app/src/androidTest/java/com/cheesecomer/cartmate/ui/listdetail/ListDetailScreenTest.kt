package com.cheesecomer.cartmate.ui.listdetail

import com.cheesecomer.cartmate.domain.product.NormalizationDictionary
import com.cheesecomer.cartmate.ui.suggestions.SuggestionSource
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.test.assert
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.lifecycle.SavedStateHandle
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cheesecomer.cartmate.data.memory.InMemoryStore
import com.cheesecomer.cartmate.domain.model.ItemId
import com.cheesecomer.cartmate.domain.model.ListId
import com.cheesecomer.cartmate.domain.model.NewItem
import com.cheesecomer.cartmate.domain.model.NewList
import com.cheesecomer.cartmate.domain.model.UserId
import com.cheesecomer.cartmate.domain.model.Visibility
import com.cheesecomer.cartmate.ui.itementry.LastVisibilityStore
import com.cheesecomer.cartmate.ui.theme.CartMateTheme
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.flow.flowOf

@RunWith(AndroidJUnit4::class)
class ListDetailScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val clock = Clock.systemUTC()
    private val store = InMemoryStore(clock) { UserId("taro") }
    private var listId = runBlocking { store.createList(NewList("スーパー", null, Visibility.SHARED)) }

    private val openedEntries = mutableListOf<Triple<String, Double?, String?>>()
    private val editedItems = mutableListOf<ItemId>()
    private var backCount = 0

    private fun setContent() {
        val viewModel = ListDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("listId" to listId.value)),
            listRepository = store,
            itemRepository = store,
            lastVisibility = LastVisibilityStore(),
            clock = clock,
            currentUser = { UserId("taro") },
            memberDirectory = { flowOf(emptyList()) },
            suggestionSource = SuggestionSource(store, store, store, clock) { flowOf(NormalizationDictionary.EMPTY) },
        )
        // アプリと同じく、キーボードの高さを画面側で受け取る（シートはキーボードが出てから表示する）
        composeRule.runOnUiThread {
            composeRule.activity.enableEdgeToEdge()
            composeRule.activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        composeRule.setContent {
            CartMateTheme {
                ListDetailScreen(
                    onBack = { backCount++ },
                    onOpenEntry = { name, quantity, unit -> openedEntries += Triple(name, quantity, unit) },
                    onEditItem = { editedItems += it },
                    viewModel = viewModel,
                )
            }
        }
    }

    private fun addItem(
        name: String,
        quantity: Double? = 1.0,
        unit: String? = null,
        memo: String? = null,
        visibility: Visibility = Visibility.SHARED,
        listId: ListId = this.listId,
    ) = runBlocking { store.addItem(NewItem(listId, name, quantity, unit, memo, visibility)) }

    /** 商品追加欄をタップし、サジェストのシートが表示されるまで待つ */
    private fun openSheet() {
        composeRule.onNodeWithTag(AddItemTags.FIELD).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithTag(AddItemTags.SHEET_INPUT).assertIsDisplayed() }.isSuccess
        }
    }

    private fun openItemNames() = runBlocking { store.observeOpenItems().first().map { it.inputName } }

    private fun listItemNames() =
        runBlocking { store.observeListItems(listId, Instant.EPOCH).first().map { it.inputName } }

    /** Snackbar の「元に戻す」。チェック済みのカードにも同じ文字のボタンがあるので、読み上げの名前で区別する */
    private fun snackbarUndo() =
        composeRule.onNode(hasText("元に戻す") and !hasContentDescription("のチェックを外す", substring = true))

    private fun activePurchaseCount() =
        runBlocking { store.observePurchases(Instant.EPOCH).first().count { it.active } }

    @Test
    fun 商品追加欄をタップするとシートが開き_Enterで数量1単位個で追加され_シートは開いたまま入力欄が空になる() {
        setContent()

        openSheet()
        composeRule.onNodeWithTag(AddItemTags.SHEET_INPUT).assertIsFocused()
        composeRule.onNodeWithTag(AddItemTags.SHEET_INPUT).performTextInput("牛乳")
        composeRule.onNodeWithTag(AddItemTags.SHEET_INPUT).performImeAction()

        composeRule.waitUntil { openItemNames() == listOf("牛乳") }
        val item = runBlocking { store.observeOpenItems().first().single() }
        assertThat(item.quantity).isEqualTo(1.0)
        assertThat(item.unit).isEqualTo("個")
        // 画面が落ち着いたあとも、シートは開いたまま、入力欄は空でフォーカスを保つ
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(AddItemTags.SHEET_INPUT).assertIsFocused()
        // 空のときは placeholder が表示されるので、入力された文字だけを比べる
        composeRule.onNodeWithTag(AddItemTags.SHEET_INPUT)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
    }

    @Test
    fun シートを開いたまま続けて複数の商品を追加できる() {
        setContent()

        openSheet()
        listOf("牛乳", "卵", "パン").forEach { name ->
            composeRule.onNodeWithTag(AddItemTags.SHEET_INPUT).performTextInput(name)
            composeRule.onNodeWithTag(AddItemTags.SHEET_INPUT).performImeAction()
        }

        composeRule.waitUntil { openItemNames() == listOf("牛乳", "卵", "パン") }
    }

    @Test
    fun 未入力ではシートに案内を表示し_入力すると数量を設定して追加が出る() {
        setContent()

        openSheet()
        composeRule.onNodeWithText("商品名を入力すると候補が出ます").assertIsDisplayed()
        composeRule.onNodeWithText("数量を設定して追加").assertDoesNotExist()

        composeRule.onNodeWithTag(AddItemTags.SHEET_INPUT).performTextInput("豆乳")

        composeRule.onNodeWithText("数量を設定して追加").assertIsDisplayed()
    }

    @Test
    fun 数量を設定して追加で_入力した商品名で商品詳細入力画面を開く() {
        setContent()

        openSheet()
        composeRule.onNodeWithTag(AddItemTags.SHEET_INPUT).performTextInput("豆乳")
        composeRule.onNodeWithText("数量を設定して追加").performClick()

        composeRule.waitUntil { openedEntries.isNotEmpty() }
        assertThat(openedEntries).containsExactly(Triple("豆乳", 1.0, "個"))
        assertThat(openItemNames()).isEmpty()
    }

    @Test
    fun 戻るキーでシートを閉じても_入力途中の商品名は商品追加欄に残る() {
        setContent()

        openSheet()
        composeRule.onNodeWithTag(AddItemTags.SHEET_INPUT).performTextInput("牛")
        // キーボードが出ていれば1回目の戻るキーはキーボードを閉じるので、閉じてから戻るキーを押す
        Espresso.closeSoftKeyboard()
        Espresso.pressBack()

        composeRule.waitForIdle()
        composeRule.onNodeWithTag(AddItemTags.SHEET_INPUT).assertDoesNotExist()
        composeRule.onNodeWithTag(AddItemTags.FIELD).assert(hasText("牛"))
        assertThat(backCount).isEqualTo(0)
        assertThat(openItemNames()).isEmpty()
    }

    @Test
    fun 右スワイプでチェックすると購入が記録される() {
        addItem("牛乳")
        setContent()

        composeRule.onNodeWithText("牛乳").performTouchInput { swipeRight() }

        composeRule.waitUntil { openItemNames().isEmpty() }
        assertThat(activePurchaseCount()).isEqualTo(1)
    }

    @Test
    fun 左スワイプで削除し_Snackbarの元に戻すで復元できる() {
        addItem("牛乳")
        setContent()

        composeRule.onNodeWithText("牛乳").performTouchInput { swipeLeft() }
        composeRule.waitUntil { openItemNames().isEmpty() }

        snackbarUndo().performClick()
        composeRule.waitUntil { openItemNames() == listOf("牛乳") }
        // 元に戻した項目が、もう一度削除されないこと
        composeRule.waitForIdle()
        assertThat(openItemNames()).containsExactly("牛乳")
        composeRule.onNodeWithText("牛乳").assertIsDisplayed()
    }

    @Test
    fun 右スワイプでチェックし_Snackbarの元に戻すで未購入に戻る() {
        addItem("牛乳")
        setContent()

        composeRule.onNodeWithText("牛乳").performTouchInput { swipeRight() }
        composeRule.waitUntil { openItemNames().isEmpty() }

        snackbarUndo().performClick()
        composeRule.waitUntil { openItemNames() == listOf("牛乳") }
        // 元に戻した項目が、もう一度チェックされないこと
        composeRule.waitForIdle()
        assertThat(openItemNames()).containsExactly("牛乳")
        assertThat(activePurchaseCount()).isEqualTo(0)
    }

    @Test
    fun チェック済みの項目を左スワイプで未購入に戻せる() {
        addItem("牛乳")
        setContent()

        composeRule.onNodeWithText("牛乳").performTouchInput { swipeRight() }
        composeRule.waitUntil { openItemNames().isEmpty() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("牛乳").performTouchInput { swipeLeft() }

        composeRule.waitUntil { openItemNames() == listOf("牛乳") }
        // 戻した項目が、もう一度チェックされないこと
        composeRule.waitForIdle()
        assertThat(openItemNames()).containsExactly("牛乳")
        assertThat(activePurchaseCount()).isEqualTo(0)
    }

    @Test
    fun 未購入の項目をタップするとチェックされる() {
        addItem("牛乳")
        setContent()

        composeRule.onNodeWithText("牛乳").performClick()

        composeRule.waitUntil { openItemNames().isEmpty() }
        assertThat(activePurchaseCount()).isEqualTo(1)
        assertThat(editedItems).isEmpty()
    }

    @Test
    fun チェック済みの項目をタップするとチェックが外れる() {
        val itemId = addItem("牛乳")
        runBlocking { store.completeItem(itemId, Instant.now()) }
        setContent()

        composeRule.onNodeWithText("牛乳").performClick()

        composeRule.waitUntil { openItemNames() == listOf("牛乳") }
        composeRule.waitForIdle()
        assertThat(openItemNames()).containsExactly("牛乳")
        assertThat(activePurchaseCount()).isEqualTo(0)
    }

    @Test
    fun 編集ボタンで項目の商品詳細入力画面を開く() {
        val itemId = addItem("牛乳")
        setContent()

        composeRule.onNodeWithContentDescription("牛乳を編集").performClick()

        composeRule.waitUntil { editedItems.isNotEmpty() }
        assertThat(editedItems).containsExactly(itemId)
        assertThat(activePurchaseCount()).isEqualTo(0)
    }

    @Test
    fun 削除ボタンで削除できる() {
        addItem("牛乳")
        setContent()

        composeRule.onNodeWithContentDescription("牛乳を削除").performClick()

        composeRule.waitUntil { openItemNames().isEmpty() }
        assertThat(activePurchaseCount()).isEqualTo(0)
    }

    @Test
    fun チェック済みの項目は元に戻すボタンでチェックを外せる() {
        val itemId = addItem("牛乳")
        runBlocking { store.completeItem(itemId, Instant.now()) }
        setContent()

        composeRule.onNodeWithContentDescription("牛乳のチェックを外す").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("牛乳のチェックを外す").performClick()

        composeRule.waitUntil { openItemNames() == listOf("牛乳") }
        assertThat(activePurchaseCount()).isEqualTo(0)
    }

    @Test
    fun 数量と単位は商品名の横に_備考は商品名の下の行に表示される() {
        addItem("牛乳", 2.0, "本", memo = "無調整")
        setContent()

        // カード全体が1つの要素にまとまるので、個々の文字の要素で位置を比べる
        val name = composeRule.onNodeWithText("牛乳", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val quantity = composeRule.onNodeWithText("2本", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val memo = composeRule.onNodeWithText("無調整", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertThat(quantity.left).isAtLeast(name.right)
        assertThat(memo.top).isAtLeast(name.bottom)
    }

    @Test
    fun のこりと完了の件数をバッジで表示する() {
        addItem("牛乳")
        val egg = addItem("卵")
        runBlocking { store.completeItem(egg, Instant.now()) }
        setContent()

        composeRule.onNodeWithText("のこり 1件 / 1完了").assertIsDisplayed()
    }

    @Test
    fun 共有のリストのプライベートな項目にだけ鍵アイコンを付ける() {
        addItem("牛乳")
        addItem("プレゼント", visibility = Visibility.PRIVATE)
        setContent()

        composeRule.onNode(hasText("プレゼント") and hasContentDescription("プライベート")).assertIsDisplayed()
        composeRule.onNode(hasText("牛乳") and hasContentDescription("プライベート")).assertDoesNotExist()
    }

    @Test
    fun プライベートのリストでは_項目ではなく上部のバーに鍵アイコンを付ける() {
        listId = runBlocking { store.createList(NewList("自分用", null, Visibility.PRIVATE)) }
        addItem("目薬", visibility = Visibility.PRIVATE)
        setContent()

        composeRule.onNode(hasText("目薬") and hasContentDescription("プライベート")).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("プライベート").assertIsDisplayed()
    }

    @Test
    fun チェックした項目は位置を変えずに表示され続ける() {
        addItem("牛乳")
        addItem("卵")
        addItem("パン")
        setContent()
        val before = composeRule.onNodeWithText("卵").getUnclippedBoundsInRoot().top

        composeRule.onNodeWithText("卵").performTouchInput { swipeRight() }
        composeRule.waitUntil { "卵" !in openItemNames() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("卵").assertIsDisplayed()
        assertThat(composeRule.onNodeWithText("卵").getUnclippedBoundsInRoot().top).isEqualTo(before)
    }

    @Test
    fun メニューからリスト名と備考を変更できる() {
        setContent()

        composeRule.onNodeWithContentDescription("リストのメニュー").performClick()
        composeRule.onNodeWithText("リストを編集").performClick()
        composeRule.onNode(hasText("リスト名")).performTextReplacement("イオン")
        composeRule.onNode(hasText("メモ（任意）")).performTextInput("火曜市")
        composeRule.onNodeWithText("変更").performClick()

        composeRule.waitUntil { runBlocking { store.observeLists().first().single().name } == "イオン" }
        assertThat(runBlocking { store.observeLists().first().single().memo }).isEqualTo("火曜市")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("イオン").assertIsDisplayed()
    }

    @Test
    fun のこりの商品があるリストは削除できず_理由を表示する() {
        addItem("牛乳")
        setContent()

        composeRule.onNodeWithContentDescription("リストのメニュー").performClick()
        composeRule.onNodeWithText("リストを削除").performClick()

        composeRule.onNodeWithText("のこりの商品があるリストは削除できません").assertIsDisplayed()
        composeRule.onNodeWithText("リストを削除しますか？").assertDoesNotExist()
    }

    @Test
    fun のこりの商品がないリストは確認してから削除し_一覧に戻る() {
        setContent()

        composeRule.onNodeWithContentDescription("リストのメニュー").performClick()
        composeRule.onNodeWithText("リストを削除").performClick()
        composeRule.onNodeWithText("削除").performClick()

        composeRule.waitUntil { backCount == 1 }
        assertThat(runBlocking { store.observeLists().first().single().status.name }).isEqualTo("DELETED")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun TalkBackのカスタムアクションで項目を上下に移動できる() {
        addItem("牛乳")
        addItem("卵")
        addItem("パン")
        setContent()

        composeRule.onNodeWithText("パン").performCustomAccessibilityActionWithLabel("上へ移動")
        composeRule.waitUntil { listItemNames() == listOf("牛乳", "パン", "卵") }

        composeRule.onNodeWithText("牛乳").performCustomAccessibilityActionWithLabel("下へ移動")
        composeRule.waitUntil { listItemNames() == listOf("パン", "牛乳", "卵") }
    }

    @Test
    fun ロングタップしてドラッグすると項目を並び替えられる() {
        addItem("牛乳")
        addItem("卵")
        addItem("パン")
        setContent()
        val rowHeight = composeRule.onNodeWithText("卵").getUnclippedBoundsInRoot().let { it.bottom - it.top }

        val bread = composeRule.onNodeWithText("パン")
        bread.performTouchInput { down(center) }
        // 長押しとして認識させる（イベントの時刻ではなく、画面側の時計を進める）
        composeRule.mainClock.advanceTimeBy(1_000)
        // 2行ぶん上へ、少しずつ動かして先頭に置く
        repeat(STEPS) {
            bread.performTouchInput { moveBy(Offset(0f, -(rowHeight.toPx() * 2.5f) / STEPS)) }
            composeRule.mainClock.advanceTimeBy(50)
        }
        bread.performTouchInput { up() }

        composeRule.waitUntil(timeoutMillis = 3_000) { listItemNames().first() == "パン" }
        // 指を離したあとに、ほかの操作（チェック・削除）が起きていないこと
        composeRule.waitForIdle()
        assertThat(listItemNames()).containsExactly("パン", "牛乳", "卵").inOrder()
        assertThat(activePurchaseCount()).isEqualTo(0)
    }

    private companion object {
        const val STEPS = 10
    }
}
