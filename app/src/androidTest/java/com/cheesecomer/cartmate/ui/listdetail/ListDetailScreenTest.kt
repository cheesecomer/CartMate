package com.cheesecomer.cartmate.ui.listdetail

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.onAllNodesWithText
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.test.getUnclippedBoundsInRoot
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
import com.cheesecomer.cartmate.domain.model.NewItem
import com.cheesecomer.cartmate.domain.model.UserId
import com.cheesecomer.cartmate.ui.theme.CartMateTheme
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class ListDetailScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val clock = Clock.systemUTC()
    private val store = InMemoryStore(clock) { UserId("taro") }
    private val listId = runBlocking { store.createList("スーパー") }

    private fun setContent() {
        val viewModel = ListDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("listId" to listId.value)),
            listRepository = store,
            itemRepository = store,
            clock = clock,
        )
        composeRule.setContent {
            CartMateTheme { ListDetailScreen(onBack = {}, viewModel = viewModel) }
        }
    }

    private fun openItemNames() = runBlocking { store.observeOpenItems().first().map { it.inputName } }

    private fun activePurchaseCount() =
        runBlocking { store.observePurchases(Instant.EPOCH).first().count { it.active } }

    @Test
    fun 商品名_Enter_Enterで数量1のまま追加され_商品名の欄にフォーカスが戻る() {
        setContent()

        composeRule.onNodeWithTag(AddItemBarTags.NAME).performTextInput("牛乳")
        composeRule.onNodeWithTag(AddItemBarTags.NAME).performImeAction()
        composeRule.onNodeWithTag(AddItemBarTags.QUANTITY).assertIsFocused()
        composeRule.onNodeWithTag(AddItemBarTags.QUANTITY).performImeAction()

        composeRule.waitUntil { openItemNames() == listOf("牛乳") }
        val item = runBlocking { store.observeOpenItems().first().single() }
        assertThat(item.quantity).isEqualTo(1.0)
        composeRule.onNodeWithTag(AddItemBarTags.NAME).assertIsFocused()
    }

    @Test
    fun 続けて複数の商品を追加できる() {
        setContent()

        listOf("牛乳", "卵").forEach { name ->
            composeRule.onNodeWithTag(AddItemBarTags.NAME).performTextInput(name)
            composeRule.onNodeWithTag(AddItemBarTags.NAME).performImeAction()
            composeRule.onNodeWithTag(AddItemBarTags.QUANTITY).performImeAction()
        }

        composeRule.waitUntil { openItemNames() == listOf("牛乳", "卵") }
    }

    @Test
    fun 右スワイプでチェックすると購入が記録される() {
        runBlocking { store.addItem(NewItem(listId, "牛乳", null, null)) }
        setContent()

        composeRule.onNodeWithText("牛乳").performTouchInput { swipeRight() }

        composeRule.waitUntil { openItemNames().isEmpty() }
        val purchases = runBlocking { store.observePurchases(Instant.EPOCH).first() }
        assertThat(purchases.single().active).isTrue()
    }

    @Test
    fun 左スワイプで削除し_Snackbarの元に戻すで復元できる() {
        runBlocking { store.addItem(NewItem(listId, "牛乳", null, null)) }
        setContent()

        composeRule.onNodeWithText("牛乳").performTouchInput { swipeLeft() }
        composeRule.waitUntil { openItemNames().isEmpty() }

        composeRule.onNodeWithText("元に戻す").performClick()
        composeRule.waitUntil { openItemNames() == listOf("牛乳") }
        // 元に戻した項目が、もう一度削除されないこと
        composeRule.waitForIdle()
        assertThat(openItemNames()).containsExactly("牛乳")
        composeRule.onNodeWithText("牛乳").assertIsDisplayed()
    }

    @Test
    fun 右スワイプでチェックし_Snackbarの元に戻すで未購入に戻る() {
        runBlocking { store.addItem(NewItem(listId, "牛乳", null, null)) }
        setContent()

        composeRule.onNodeWithText("牛乳").performTouchInput { swipeRight() }
        composeRule.waitUntil { openItemNames().isEmpty() }

        composeRule.onNodeWithText("元に戻す").performClick()
        composeRule.waitUntil { openItemNames() == listOf("牛乳") }
        // 元に戻した項目が、もう一度チェックされないこと
        composeRule.waitForIdle()
        assertThat(openItemNames()).containsExactly("牛乳")
        assertThat(activePurchaseCount()).isEqualTo(0)
    }

    @Test
    fun チェック済みの項目を左スワイプで未購入に戻せる() {
        runBlocking { store.addItem(NewItem(listId, "牛乳", null, null)) }
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
    fun 項目をタップして編集できる() {
        runBlocking { store.addItem(NewItem(listId, "牛乳", 1.0, null)) }
        setContent()

        composeRule.onNodeWithText("牛乳").performClick()
        composeRule.onNodeWithTag(AddItemBarTags.QUANTITY).assertIsFocused()
        composeRule.onNodeWithTag(AddItemBarTags.QUANTITY).performTextInput("2")
        composeRule.onNodeWithTag(AddItemBarTags.EDIT_NAME).performTextReplacement("低脂肪乳")
        composeRule.onNodeWithText("更新").performClick()

        composeRule.waitUntil { openItemNames() == listOf("低脂肪乳") }
        val item = runBlocking { store.observeOpenItems().first().single() }
        assertThat(item.quantity).isEqualTo(2.0)
        // 編集が終わると商品名の入力欄に戻る
        composeRule.onNodeWithTag(AddItemBarTags.NAME).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun TalkBackのカスタムアクションでチェックと削除ができる() {
        runBlocking {
            store.addItem(NewItem(listId, "牛乳", null, null))
            store.addItem(NewItem(listId, "卵", null, null))
        }
        setContent()

        composeRule.onNodeWithText("牛乳").performCustomAccessibilityActionWithLabel("チェック")
        composeRule.onNodeWithText("卵").performCustomAccessibilityActionWithLabel("削除")

        composeRule.waitUntil { openItemNames().isEmpty() }
        val purchases = runBlocking { store.observePurchases(Instant.EPOCH).first() }
        assertThat(purchases.map { it.inputName }).containsExactly("牛乳")
    }

    @Test
    fun 編集中に戻るキーを押すと編集をやめる() {
        runBlocking { store.addItem(NewItem(listId, "牛乳", 1.0, null)) }
        setContent()

        composeRule.onNodeWithText("牛乳").performClick()
        composeRule.onNodeWithTag(AddItemBarTags.EDIT_NAME).assertIsDisplayed()

        // キーボードが出ていれば1回目の戻るキーはキーボードを閉じるので、閉じてから戻るキーを押す
        Espresso.closeSoftKeyboard()
        Espresso.pressBack()

        composeRule.onNodeWithTag(AddItemBarTags.EDIT_NAME).assertDoesNotExist()
        composeRule.onNodeWithTag(AddItemBarTags.NAME).assertIsDisplayed()
    }

    @Test
    fun 編集中に閉じるボタンを押すと編集をやめる() {
        runBlocking { store.addItem(NewItem(listId, "牛乳", 1.0, null)) }
        setContent()

        composeRule.onNodeWithText("牛乳").performClick()
        composeRule.onNodeWithContentDescription("編集をやめる").performClick()

        composeRule.onNodeWithTag(AddItemBarTags.EDIT_NAME).assertDoesNotExist()
        composeRule.onNodeWithTag(AddItemBarTags.NAME).assertIsDisplayed()
    }

    @Test
    fun チェックした項目は位置を変えずに表示され続ける() {
        runBlocking {
            store.addItem(NewItem(listId, "牛乳", null, null))
            store.addItem(NewItem(listId, "卵", null, null))
            store.addItem(NewItem(listId, "パン", null, null))
        }
        setContent()
        val before = composeRule.onNodeWithText("卵").getUnclippedBoundsInRoot().top

        composeRule.onNodeWithText("卵").performTouchInput { swipeRight() }
        composeRule.waitUntil { "卵" !in openItemNames() }
        composeRule.waitForIdle()

        // チェック済みセクションに移らず、同じ位置に残る
        composeRule.onNodeWithText("チェック済み", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("卵").assertIsDisplayed()
        assertThat(composeRule.onNodeWithText("卵").getUnclippedBoundsInRoot().top).isEqualTo(before)
    }

    /** 実際のアプリと同じく、NavHost の中でリスト詳細を表示する */
    private fun setContentInNavHost() {
        val viewModel = ListDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("listId" to listId.value)),
            listRepository = store,
            itemRepository = store,
            clock = clock,
        )
        composeRule.setContent {
            val navController = rememberNavController()
            CartMateTheme {
                NavHost(navController, startDestination = "lists") {
                    composable("lists") { Text("リスト一覧") }
                    composable("detail") { ListDetailScreen(onBack = { navController.popBackStack() }, viewModel = viewModel) }
                }
            }
            LaunchedEffect(Unit) { navController.navigate("detail") }
        }
    }

    @Test
    fun 画面遷移の中でも_編集中に戻るキーを押すと前の画面に戻らずに編集をやめる() {
        runBlocking { store.addItem(NewItem(listId, "牛乳", 1.0, null)) }
        setContentInNavHost()
        composeRule.waitUntil { composeRule.onAllNodesWithText("牛乳").fetchSemanticsNodes().isNotEmpty() }

        composeRule.onNodeWithText("牛乳").performClick()
        composeRule.onNodeWithTag(AddItemBarTags.EDIT_NAME).assertIsDisplayed()
        Espresso.closeSoftKeyboard()
        Espresso.pressBack()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("リスト一覧").assertDoesNotExist()
        composeRule.onNodeWithTag(AddItemBarTags.EDIT_NAME).assertDoesNotExist()
        composeRule.onNodeWithTag(AddItemBarTags.NAME).assertIsDisplayed()
    }

    /**
     * 報告: エミュレータ（物理キーボードあり）で編集中に戻るを押すと、1回目は入力用のツールバーが閉じ、
     * 2回目は何も起きなかった。期待は2回目で編集をやめること。
     * このテストは報告に近い手順だが、再現できていない（修正前のコードでも通る）。
     */
    @Test
    fun 編集中_キーボードを閉じたあとの2回目の戻るキーで編集をやめる() {
        runBlocking { store.addItem(NewItem(listId, "牛乳", 1.0, null)) }
        setContentInNavHost()
        composeRule.waitUntil { composeRule.onAllNodesWithText("牛乳").fetchSemanticsNodes().isNotEmpty() }

        composeRule.onNodeWithText("牛乳").performClick()
        composeRule.onNodeWithTag(AddItemBarTags.QUANTITY).assertIsFocused()

        Espresso.pressBack()
        composeRule.waitForIdle()
        Espresso.pressBack()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("リスト一覧").assertDoesNotExist()
        composeRule.onNodeWithTag(AddItemBarTags.EDIT_NAME).assertDoesNotExist()
        composeRule.onNodeWithTag(AddItemBarTags.NAME).assertIsDisplayed()
    }
}
