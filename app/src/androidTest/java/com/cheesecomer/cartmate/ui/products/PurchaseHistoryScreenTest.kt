package com.cheesecomer.cartmate.ui.products

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

/** 購入履歴の画面（docs/design/ui.md「購入履歴」） */
@RunWith(AndroidJUnit4::class)
class PurchaseHistoryScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val clock = Clock.systemUTC()
    private val store = InMemoryStore(clock) { UserId("taro") }
    private var backCount = 0
    private var emptiedCount = 0

    private fun buy(name: String) = runBlocking {
        val listId = store.createList(NewList("スーパー", null, Visibility.SHARED))
        val id = store.addItem(NewItem(listId, name, 1.0, "個", memo = null, Visibility.SHARED))
        store.completeItem(id, clock.instant())
    }

    private fun setContent(name: String) {
        val productId = runBlocking { store.observeProducts().first().first { it.displayName == name }.id }
        val viewModel = PurchaseHistoryViewModel(
            savedStateHandle = SavedStateHandle(mapOf("productId" to productId.value)),
            productRepository = store,
            purchaseRepository = store,
            itemRepository = store,
            currentUser = { UserId("taro") },
            memberDirectory = { flowOf(emptyList()) },
            clock = clock,
            dictionaryProvider = { flowOf(NormalizationDictionary.EMPTY) },
        )
        composeRule.setContent {
            CartMateTheme {
                PurchaseHistoryScreen(onBack = { backCount++ }, onEmptied = { emptiedCount++ }, viewModel = viewModel)
            }
        }
    }

    /** 最後の購入を1件ずつ削除して0件になったら、商品の画面を経由せずに商品名の管理まで戻る */
    @Test
    fun 最後の購入を削除すると_商品名の管理まで戻る() {
        buy("ケーキ")
        setContent("ケーキ")
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithContentDescription("ケーキの購入を削除").assertIsDisplayed() }.isSuccess
        }

        composeRule.onNodeWithContentDescription("ケーキの購入を削除").performClick()
        composeRule.onNodeWithText("削除").performClick()
        composeRule.waitForIdle()

        assertThat(emptiedCount).isEqualTo(1)
        assertThat(backCount).isEqualTo(0)
    }
}
