package com.cheesecomer.cartmate.ui.lists

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cheesecomer.cartmate.data.memory.InMemoryStore
import com.cheesecomer.cartmate.domain.model.NewItem
import com.cheesecomer.cartmate.domain.model.UserId
import com.cheesecomer.cartmate.ui.theme.CartMateTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock

@RunWith(AndroidJUnit4::class)
class ListsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val store = InMemoryStore(Clock.systemUTC()) { UserId("taro") }

    private fun setContent() {
        val viewModel = ListsViewModel(store, store, store, Clock.systemUTC())
        composeRule.setContent {
            CartMateTheme { ListsScreen(onOpenList = {}, viewModel = viewModel) }
        }
    }

    @Test
    fun リストを作成すると一覧に表示される() {
        setContent()

        composeRule.onNodeWithContentDescription("リストを作成").performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("スーパー")
        composeRule.onNodeWithText("作成").performClick()

        composeRule.onNodeWithText("スーパー").assertIsDisplayed()
        composeRule.onNodeWithText("まだ買うものを登録していません").assertIsDisplayed()
    }

    @Test
    fun 未購入が0件のリストだけ削除できる() {
        runBlocking {
            val supermarket = store.createList("スーパー")
            store.addItem(NewItem(supermarket, "牛乳", null, null))
            store.createList("薬局")
        }
        setContent()

        composeRule.onNodeWithContentDescription("スーパー を削除").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("薬局 を削除").performClick()
        composeRule.onNodeWithText("削除").performClick()

        composeRule.waitUntil { runBlocking { store.observeLists().first().count { it.status.name == "ACTIVE" } } == 1 }
        composeRule.onNodeWithText("薬局").assertDoesNotExist()
    }

    @Test
    fun 編集ボタンからリスト名を変更できる() {
        runBlocking { store.createList("スーパー") }
        setContent()

        composeRule.onNodeWithContentDescription("スーパーの名前を変更").performClick()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("イオン")
        composeRule.onNodeWithText("変更").performClick()

        composeRule.onNodeWithText("イオン").assertIsDisplayed()
        composeRule.onNodeWithText("スーパー").assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun TalkBackのカスタムアクションでリストを上下に移動できる() {
        runBlocking {
            store.createList("スーパー")
            store.createList("薬局")
        }
        setContent()

        composeRule.onNodeWithText("薬局").performCustomAccessibilityActionWithLabel("上へ移動")

        composeRule.waitUntil { runBlocking { store.observeLists().first().map { it.name } } == listOf("薬局", "スーパー") }
    }
}
