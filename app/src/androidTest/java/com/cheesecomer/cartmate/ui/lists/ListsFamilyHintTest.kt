package com.cheesecomer.cartmate.ui.lists

import android.os.SystemClock
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cheesecomer.cartmate.data.memory.FixedAccountRepository
import com.cheesecomer.cartmate.data.memory.InMemoryStore
import com.cheesecomer.cartmate.domain.model.Account
import com.cheesecomer.cartmate.domain.model.NewList
import com.cheesecomer.cartmate.domain.model.UserId
import com.cheesecomer.cartmate.domain.model.Visibility
import com.cheesecomer.cartmate.domain.product.NormalizationDictionary
import com.cheesecomer.cartmate.ui.hints.Hint
import com.cheesecomer.cartmate.ui.hints.HintStore
import com.cheesecomer.cartmate.ui.hints.InMemoryHintStore
import com.cheesecomer.cartmate.ui.suggestions.SuggestionSource
import com.cheesecomer.cartmate.ui.theme.CartMateTheme
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock

/** アバターから家族とアカウントの設定を開けることを、初めてのときだけ吹き出しで伝える（フィードバック #014） */
@RunWith(AndroidJUnit4::class)
class ListsFamilyHintTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val clock = Clock.systemUTC()
    private val store = InMemoryStore(clock) { UserId("taro") }
    private var openedFamily = 0

    private fun setContent(hints: HintStore) {
        runBlocking { store.createList(NewList("スーパー", null, Visibility.SHARED)) }
        val viewModel = ListsViewModel(
            store, store, store, clock,
            FixedAccountRepository(Account(UserId("taro"), "山田 太郎", "taro@example.com", null)),
            { flowOf(true) },
            SuggestionSource(store, store, store, clock, { flowOf(NormalizationDictionary.EMPTY) }, Dispatchers.Default),
            hints,
        )
        composeRule.setContent {
            CartMateTheme { ListsScreen(onOpenList = {}, onOpenFamily = { openedFamily++ }, viewModel = viewModel) }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { displayed("スーパー") }
    }

    private fun displayed(text: String) = runCatching { composeRule.onNodeWithText(text).assertIsDisplayed() }.isSuccess

    /**
     * 画面の [text] の位置を、端末の画面に指で触れるのと同じようにタップする。吹き出しは別のウィンドウなので、
     * その外のタップで閉じるかは、画面のウィンドウへ直接イベントを送る performClick では確かめられない
     */
    private fun tapOnScreen(text: String) {
        val bounds = composeRule.onNodeWithText(text).fetchSemanticsNode().boundsInWindow
        val x = bounds.center.x
        val y = bounds.center.y
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val down = SystemClock.uptimeMillis()
        instrumentation.sendPointerSync(MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, x, y, 0))
        instrumentation.sendPointerSync(MotionEvent.obtain(down, down + 50, MotionEvent.ACTION_UP, x, y, 0))
    }

    @Test
    fun 初めてなら吹き出しを出し_外をタップすると閉じて二度と出さない() {
        val hints = InMemoryHintStore()
        setContent(hints)
        composeRule.waitUntil(timeoutMillis = 5_000) { displayed(HINT) }

        tapOnScreen("長押しで並び替えができます")

        composeRule.waitUntil(timeoutMillis = 5_000) { !displayed(HINT) }
        composeRule.waitForIdle()
        assertWithMessage("落ち着いたあとも吹き出しは出ていない").that(displayed(HINT)).isFalse()
        assertWithMessage("見たことを覚える").that(runBlocking { hints.isShown(Hint.FAMILY_ENTRY) }).isTrue()
        assertWithMessage("外のタップでは家族画面を開かない").that(openedFamily).isEqualTo(0)
    }

    @Test
    fun 吹き出しが出ていてもアバターのタップで家族画面を開き_二度と出さない() {
        val hints = InMemoryHintStore()
        setContent(hints)
        composeRule.waitUntil(timeoutMillis = 5_000) { displayed(HINT) }

        composeRule.onNodeWithContentDescription("家族").performClick()

        composeRule.waitForIdle()
        assertWithMessage("家族画面を開く").that(openedFamily).isEqualTo(1)
        assertWithMessage("見たことを覚える").that(runBlocking { hints.isShown(Hint.FAMILY_ENTRY) }).isTrue()
        composeRule.waitUntil(timeoutMillis = 5_000) { !displayed(HINT) }
    }

    @Test
    fun 一度見ていれば吹き出しを出さない() {
        setContent(InMemoryHintStore(Hint.FAMILY_ENTRY))

        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("家族").assertIsDisplayed()
        assertWithMessage("吹き出しを出さない").that(displayed(HINT)).isFalse()
    }

    private companion object {
        const val HINT = "家族とアカウントの設定は\nここから"
    }
}
