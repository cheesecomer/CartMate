package com.cheesecomer.cartmate.ui.products

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cheesecomer.cartmate.data.memory.InMemoryStore
import com.cheesecomer.cartmate.domain.model.NewItem
import com.cheesecomer.cartmate.domain.model.NewList
import com.cheesecomer.cartmate.domain.model.UserId
import com.cheesecomer.cartmate.domain.model.Visibility
import com.cheesecomer.cartmate.domain.product.NormalizationDictionary
import com.cheesecomer.cartmate.ui.theme.CartMateTheme
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock

/** 「商品をまとめる」（docs/design/ui.md「商品をまとめる」） */
@RunWith(AndroidJUnit4::class)
class MergeProductsScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val store = InMemoryStore(Clock.systemUTC()) { UserId("taro") }

    private fun setContent() {
        val target = runBlocking {
            val listId = store.createList(NewList("スーパー", memo = null, Visibility.SHARED))
            listOf("豚バラ肉", "豚ばら肉", "豚バラ").forEach { store.addItem(NewItem(listId, it, 1.0, "個", memo = null, Visibility.SHARED)) }
            store.observeProducts().first().first { it.displayName == "豚バラ肉" }.id
        }
        val catalog = ProductCatalog(store, store, store, Clock.systemUTC()) { flowOf(NormalizationDictionary.EMPTY) }
        val viewModel = MergeProductsViewModel(SavedStateHandle(mapOf("targetId" to target.value)), catalog, store)
        // アプリと同じく、画面の端（ステータスバーとナビゲーションバーの下）まで描く
        composeRule.runOnUiThread { composeRule.activity.enableEdgeToEdge() }
        composeRule.setContent {
            CartMateTheme { MergeProductsScreen(onBack = {}, onDone = {}, viewModel = viewModel) }
        }
    }

    @Test
    fun 商品を選ぶと_まとめるボタンはナビゲーションバーに重ならずに押せる() {
        setContent()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithText("豚ばら肉").assertIsDisplayed() }.isSuccess
        }

        composeRule.onNodeWithText("豚ばら肉").performClick()
        composeRule.waitForIdle()

        val button = composeRule.onNode(hasText("まとめる") and hasClickAction())
        button.assertIsEnabled()
        val bottom = button.fetchSemanticsNode().boundsInWindow.bottom
        val decor = composeRule.activity.window.decorView
        val navigationBar = composeRule.runOnUiThread {
            ViewCompat.getRootWindowInsets(decor)!!.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
        }
        assertThat(navigationBar).isGreaterThan(0)
        // ボタンの下端が、ナビゲーションバーの上端より上にある
        assertThat(bottom).isAtMost((decor.height - navigationBar).toFloat())
    }
}
