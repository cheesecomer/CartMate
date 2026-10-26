package com.cheesecomer.cartmate.ui.lists

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cheesecomer.cartmate.data.memory.InMemoryStore
import com.cheesecomer.cartmate.data.memory.FixedAccountRepository
import com.cheesecomer.cartmate.domain.model.NewItem
import com.cheesecomer.cartmate.domain.model.NewList
import com.cheesecomer.cartmate.domain.model.UserId
import com.cheesecomer.cartmate.domain.model.Visibility
import com.cheesecomer.cartmate.ui.theme.CartMateTheme
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import kotlinx.coroutines.flow.flowOf

@RunWith(AndroidJUnit4::class)
class ListsScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val store = InMemoryStore(Clock.systemUTC()) { UserId("taro") }

    private fun createList(name: String, visibility: Visibility = Visibility.SHARED) =
        runBlocking { store.createList(NewList(name, null, visibility)) }

    private fun setContent() {
        // アプリと同じく、キーボードの高さを画面側で受け取る（シートはキーボードと一緒にせり上がる）
        composeRule.runOnUiThread {
            composeRule.activity.enableEdgeToEdge()
            composeRule.activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        val viewModel = ListsViewModel(store, store, store, Clock.systemUTC(), FixedAccountRepository(null), { flowOf(true) }, { flowOf(emptyList()) })
        composeRule.setContent {
            CartMateTheme { ListsScreen(onOpenList = {}, viewModel = viewModel) }
        }
    }

    /** シートのリスト名の欄が表示されるまで待つ */
    private fun waitForCreateSheet() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNode(hasText("リスト名") and hasSetTextAction()).assertIsDisplayed() }.isSuccess
        }
    }

    @Test
    fun リストがなければ空の状態を表示し_ボタンから作成できる() {
        setContent()

        composeRule.onNodeWithText("リストがありません").assertIsDisplayed()
        composeRule.onNodeWithText("リストを作成する").performClick()
        waitForCreateSheet()
        composeRule.onNode(hasText("リスト名")).performTextInput("スーパー")
        composeRule.onNodeWithText("作成する").performClick()

        composeRule.waitUntil { runBlocking { store.observeLists().first() }.isNotEmpty() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("スーパー").assertIsDisplayed()
        composeRule.onNodeWithText("リストがありません").assertDoesNotExist()
    }

    @Test
    fun リスト作成のシートで名前_公開範囲_メモを指定して作成すると_カードに表示される() {
        createList("イオン")
        setContent()

        // FAB の文字は、ボタンの要素にはまとめられない
        composeRule.onNodeWithText("リスト作成", useUnmergedTree = true).performClick()
        waitForCreateSheet()
        composeRule.onNode(hasText("リスト名")).performTextInput("薬局")
        composeRule.onNodeWithText("プライベート").performClick()
        composeRule.onNode(hasText("メモ（任意）")).performTextInput("日用品")
        composeRule.onNodeWithText("作成する").performClick()

        composeRule.waitUntil { runBlocking { store.observeLists().first() }.size == 2 }
        val list = runBlocking { store.observeLists().first() }.last()
        assertThat(list.name).isEqualTo("薬局")
        assertThat(list.memo).isEqualTo("日用品")
        assertThat(list.visibility).isEqualTo(Visibility.PRIVATE)
        // シートが閉じて落ち着いたあとのカードの表示
        composeRule.waitForIdle()
        composeRule.onNodeWithText("作成する").assertDoesNotExist()
        composeRule.onNodeWithText("日用品").assertIsDisplayed()
        composeRule.onNode(hasText("薬局") and hasContentDescription("プライベート"), useUnmergedTree = false).assertIsDisplayed()
    }

    @Test
    fun 件数の状態ごとに_のこり_登録なし_買い終わったを表示する() {
        val open = createList("スーパー")
        createList("ホームセンター")
        val done = createList("イオン")
        runBlocking {
            store.addItem(NewItem(open, "牛乳", 1.0, "本", null, Visibility.SHARED))
            val item = store.addItem(NewItem(done, "卵", 1.0, "パック", null, Visibility.SHARED))
            store.completeItem(item, java.time.Instant.now())
        }
        setContent()

        composeRule.onNodeWithText("件 のこり").assertIsDisplayed()
        composeRule.onNodeWithText("件 登録なし").assertIsDisplayed()
        composeRule.onNodeWithText("件 買い終わった！").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun TalkBackのカスタムアクションでリストを前後に移動できる() {
        createList("スーパー")
        createList("薬局")
        setContent()

        composeRule.onNodeWithText("薬局").performCustomAccessibilityActionWithLabel("前へ移動")

        composeRule.waitUntil { runBlocking { store.observeLists().first().map { it.name } } == listOf("薬局", "スーパー") }
    }
}
