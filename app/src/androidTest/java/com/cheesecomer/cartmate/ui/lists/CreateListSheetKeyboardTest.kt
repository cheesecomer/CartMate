package com.cheesecomer.cartmate.ui.lists

import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cheesecomer.cartmate.data.memory.InMemoryStore
import com.cheesecomer.cartmate.data.memory.FixedAccountRepository
import com.cheesecomer.cartmate.domain.model.NewList
import com.cheesecomer.cartmate.domain.model.UserId
import com.cheesecomer.cartmate.domain.model.Visibility
import com.cheesecomer.cartmate.testing.KeyboardTimingTest
import com.cheesecomer.cartmate.ui.theme.CartMateTheme
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import kotlinx.coroutines.flow.flowOf

/**
 * 「＋ リスト作成」を押したときの、ソフトキーボードとリスト作成のシートの順番。
 * シートはキーボードと一緒にせり上がり、キーボードより先に出て止まることはない
 */
@KeyboardTimingTest
@RunWith(AndroidJUnit4::class)
class CreateListSheetKeyboardTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val store = InMemoryStore(Clock.systemUTC()) { UserId("taro") }

    /** 画面に届いているソフトキーボードの高さ（px） */
    @Volatile
    private var imeBottom = 0

    private fun setContent() {
        runBlocking { store.createList(NewList("スーパー", null, Visibility.SHARED)) }
        // アプリと同じく、キーボードの高さを画面側で受け取る
        composeRule.runOnUiThread {
            composeRule.activity.enableEdgeToEdge()
            composeRule.activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        val viewModel = ListsViewModel(store, store, store, Clock.systemUTC(), FixedAccountRepository(null), { flowOf(true) }, { flowOf(emptyList()) })
        composeRule.setContent {
            val bottom = WindowInsets.ime.getBottom(LocalDensity.current)
            SideEffect { imeBottom = bottom }
            CartMateTheme { ListsScreen(onOpenList = {}, viewModel = viewModel) }
        }
    }

    /** シートのリスト名の欄 */
    private fun nameField() = composeRule.onNode(hasText("リスト名") and hasSetTextAction())

    private fun sheetDisplayed() = runCatching { nameField().assertIsDisplayed() }.isSuccess

    @Test
    fun リスト作成を押すと_シートはキーボードより先に出ない() {
        setContent()
        // 確かめたいのは表示の順番で、キーボードの起動の速さではない。起動したばかりの端末ではキーボードの
        // 初回の表示に数秒かかることがあるので、一度シートを開いてキーボードを出し、閉じてから測る
        composeRule.onNodeWithText("リスト作成", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) { imeBottom > 0 && sheetDisplayed() }
        Espresso.closeSoftKeyboard()
        Espresso.pressBack()
        composeRule.waitUntil(timeoutMillis = 5_000) { imeBottom == 0 && !sheetDisplayed() }

        composeRule.mainClock.autoAdvance = false

        // FAB の文字は、ボタンの要素にはまとめられない
        composeRule.onNodeWithText("リスト作成", useUnmergedTree = true).performClick()

        // 画面側の時計を実時間に合わせて少しずつ進め、シートが見えた時点でキーボードがすでに出ているかを確かめる
        // （キーボードの表示はシステム側で実時間で進むので、画面側の時計だけを先に進めない）
        var sheetShownAt = -1L
        val start = SystemClock.uptimeMillis()
        var advanced = 0L
        while (advanced < MAX_WAIT_MILLIS) {
            Thread.sleep(POLL_MILLIS)
            val elapsed = SystemClock.uptimeMillis() - start
            composeRule.mainClock.advanceTimeBy(elapsed - advanced)
            advanced = elapsed
            if (sheetDisplayed()) {
                sheetShownAt = elapsed
                assertWithMessage("シートが見えた時点（%s ms）で、キーボードが出ていない", elapsed)
                    .that(imeBottom).isGreaterThan(0)
                break
            }
        }
        assertWithMessage("シートが表示されない").that(sheetShownAt).isAtLeast(0)

        // 画面が落ち着いたあと: シートは表示されたまま、リスト名の欄にフォーカスがある
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        nameField().assertIsDisplayed()
        nameField().assertIsFocused()
        assertWithMessage("落ち着いたあともキーボードが出ている").that(imeBottom).isGreaterThan(0)
    }

    private companion object {
        const val MAX_WAIT_MILLIS = 5_000L

        // 画面の状態を読む間隔。短くしすぎるとメインスレッドを占有し、キーボードの表示が遅れる
        const val POLL_MILLIS = 100L
    }
}
