package com.cheesecomer.cartmate.ui.lists

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cheesecomer.cartmate.data.memory.FixedAccountRepository
import com.cheesecomer.cartmate.data.memory.InMemoryStore
import com.cheesecomer.cartmate.domain.model.NewList
import com.cheesecomer.cartmate.domain.model.UserId
import com.cheesecomer.cartmate.domain.model.Visibility
import com.cheesecomer.cartmate.testing.ScreenSizeRule
import com.cheesecomer.cartmate.ui.theme.CartMateTheme
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock

/** 背の低い画面で、キーボードと一緒に出たリスト作成のシートの見え方 */
@RunWith(AndroidJUnit4::class)
class CreateListSheetLayoutTest {

    // 16:9 の画面（スクリーンショットと同じ大きさ）。キーボードを除くと、シートの中身が収まりきらない
    @get:Rule(order = 0)
    val screenSize = ScreenSizeRule(width = 1080, height = 1920)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val store = InMemoryStore(Clock.systemUTC()) { UserId("taro") }

    /** 画面に届いているソフトキーボードの高さ（px） */
    @Volatile
    private var imeBottom = 0

    @Test
    fun キーボードが出ていても_作成するボタンは潰れず_キーボードに隠れない() {
        runBlocking { store.createList(NewList("スーパー", null, Visibility.SHARED)) }
        // アプリと同じく、キーボードの高さを画面側で受け取る
        composeRule.runOnUiThread {
            composeRule.activity.enableEdgeToEdge()
            composeRule.activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        val viewModel = ListsViewModel(store, store, store, Clock.systemUTC(), FixedAccountRepository(null), { flowOf(true) })
        composeRule.setContent {
            val bottom = WindowInsets.ime.getBottom(LocalDensity.current)
            SideEffect { imeBottom = bottom }
            CartMateTheme { ListsScreen(onOpenList = {}, viewModel = viewModel) }
        }

        // FAB の文字は、ボタンの要素にはまとめられない
        composeRule.onNodeWithText("リスト作成", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            imeBottom > 0 && runCatching {
                composeRule.onNode(hasText("リスト名") and hasSetTextAction()).assertIsDisplayed()
            }.isSuccess
        }

        // 画面が落ち着いたあと: ボタンはもとの高さのまま、スクロールしなくてもキーボードの上に見えている
        composeRule.waitForIdle()
        val button = composeRule.onNode(hasText("作成する") and hasClickAction())
        button.assertIsDisplayed()
        val bounds = button.fetchSemanticsNode().boundsInWindow
        val minHeight = with(composeRule.density) { 48.dp.toPx() }
        assertWithMessage("作成するボタンの高さ（%s px）が潰れている", bounds.height)
            .that(bounds.height).isAtLeast(minHeight - 1f)
        val keyboardTop = composeRule.activity.window.decorView.height - imeBottom
        assertWithMessage("作成するボタンの下端（%s px）がキーボードの上端（%s px）より下にある", bounds.bottom, keyboardTop)
            .that(bounds.bottom).isAtMost(keyboardTop.toFloat())
    }
}
