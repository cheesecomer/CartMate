package com.cheesecomer.cartmate.ui.lists

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
        val viewModel = ListsViewModel(store, store)
        composeRule.setContent {
            CartMateTheme { ListsScreen(onOpenList = {}, viewModel = viewModel) }
        }
    }

    @Test
    fun リストを作成すると一覧に表示される() {
        setContent()

        composeRule.onNodeWithText("リストを作成", useUnmergedTree = true).performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("スーパー")
        composeRule.onNodeWithText("作成").performClick()

        composeRule.onNodeWithText("スーパー").assertIsDisplayed()
        composeRule.onNodeWithText("未購入 0").assertIsDisplayed()
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
}
