package com.cheesecomer.cartmate.ui.products

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cheesecomer.cartmate.data.memory.InMemoryStore
import com.cheesecomer.cartmate.domain.model.ItemId
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

/** 商品の画面（docs/design/ui.md「商品（統合先）」） */
@RunWith(AndroidJUnit4::class)
class ProductDetailScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val clock = Clock.systemUTC()
    private val store = InMemoryStore(clock) { UserId("taro") }
    private var backCount = 0

    /** [name] を1回買った。購入の ID を返す */
    private fun buy(name: String): ItemId = runBlocking {
        val listId = store.createList(NewList("スーパー", null, Visibility.SHARED))
        val id = store.addItem(NewItem(listId, name, 1.0, "個", memo = null, Visibility.SHARED))
        store.completeItem(id, clock.instant())
        id
    }

    private fun setContent(name: String) {
        val productId = runBlocking { store.observeProducts().first().first { it.displayName == name }.id }
        val catalog = ProductCatalog(store, store, store, clock) { flowOf(NormalizationDictionary.EMPTY) }
        val viewModel = ProductDetailViewModel(SavedStateHandle(mapOf("productId" to productId.value)), catalog, store, store) { UserId("taro") }
        composeRule.setContent {
            CartMateTheme {
                ProductDetailScreen(
                    onBack = { backCount++ },
                    onMerge = {},
                    onOpen = {},
                    onRenamed = {},
                    onOpenHistory = {},
                    viewModel = viewModel,
                )
            }
        }
    }

    /**
     * 最後の購入を削除して商品が一覧から外れたら、「この商品は見つかりません」を見せずに商品名の管理に戻る。
     * 画面が落ち着いたあとの状態で確かめる
     */
    @Test
    fun 購入の記録がすべてなくなったら_見つかりませんを出さずに商品の画面を閉じる() {
        val id = buy("ケーキ")
        setContent("ケーキ")
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithText("ケーキ").assertIsDisplayed() }.isSuccess
        }

        // 購入履歴の画面で、最後の購入を削除した
        runBlocking { store.deletePurchase(id) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("この商品は見つかりません").assertDoesNotExist()
        assertThat(backCount).isEqualTo(1)
    }

    @Test
    fun 自分の購入をすべて削除すると_確認のあとで項目ごと消え_商品名の管理に戻る() {
        buy("ケーキ")
        buy("ケーキ")
        setContent("ケーキ")
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithText("自分の購入をすべて削除（2件）").assertIsDisplayed() }.isSuccess
        }

        composeRule.onNodeWithText("自分の購入をすべて削除（2件）").performScrollTo().performClick()
        composeRule.onNodeWithText("「ケーキ」の自分の購入2件を削除しますか？").assertIsDisplayed()
        composeRule.onNodeWithText("削除").performClick()
        composeRule.waitForIdle()

        assertThat(runBlocking { store.observePurchases(java.time.Instant.EPOCH).first() }).isEmpty()
        composeRule.onNodeWithText("この商品は見つかりません").assertDoesNotExist()
        assertThat(backCount).isEqualTo(1)
    }
}

