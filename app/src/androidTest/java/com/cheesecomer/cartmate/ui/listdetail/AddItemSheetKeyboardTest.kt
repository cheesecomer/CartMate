package com.cheesecomer.cartmate.ui.listdetail

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
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cheesecomer.cartmate.data.memory.InMemoryStore
import com.cheesecomer.cartmate.domain.model.NewList
import com.cheesecomer.cartmate.domain.model.UserId
import com.cheesecomer.cartmate.domain.model.Visibility
import com.cheesecomer.cartmate.testing.KeyboardTimingTest
import com.cheesecomer.cartmate.ui.itementry.LastVisibilityStore
import com.cheesecomer.cartmate.ui.theme.CartMateTheme
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import kotlinx.coroutines.flow.flowOf

/**
 * 商品追加欄をタップしたときの、ソフトキーボードとサジェストのシートの順番。
 * キーボードが出てから、その上にシートが出る（シートだけが先に出て、あとからキーボードが出ることはない）
 */
@KeyboardTimingTest
@RunWith(AndroidJUnit4::class)
class AddItemSheetKeyboardTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val clock = Clock.systemUTC()
    private val store = InMemoryStore(clock) { UserId("taro") }
    private val listId = runBlocking { store.createList(NewList("スーパー", null, Visibility.SHARED)) }

    /** 画面に届いているソフトキーボードの高さ（px） */
    @Volatile
    private var imeBottom = 0

    private fun setContent() {
        // アプリと同じく、キーボードの高さを画面側で受け取る
        composeRule.runOnUiThread {
            composeRule.activity.enableEdgeToEdge()
            composeRule.activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        val viewModel = ListDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("listId" to listId.value)),
            listRepository = store,
            itemRepository = store,
            lastVisibility = LastVisibilityStore(),
            clock = clock,
            memberDirectory = { flowOf(emptyList()) },
        )
        composeRule.setContent {
            val bottom = WindowInsets.ime.getBottom(LocalDensity.current)
            SideEffect { imeBottom = bottom }
            CartMateTheme {
                ListDetailScreen(onBack = {}, onOpenEntry = { _, _, _ -> }, onEditItem = {}, viewModel = viewModel)
            }
        }
    }

    private fun sheetDisplayed() =
        runCatching { composeRule.onNodeWithTag(AddItemTags.SHEET_INPUT).assertIsDisplayed() }.isSuccess

    @Test
    fun 商品追加欄をタップすると_キーボードが出てからその上にシートが出る() {
        setContent()
        // 確かめたいのは表示の順番で、キーボードの起動の速さではない。起動したばかりの端末ではキーボードの
        // 初回の表示に数秒かかることがあるので、一度シートを開いてキーボードを出し、閉じてから測る
        composeRule.onNodeWithTag(AddItemTags.FIELD).performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) { imeBottom > 0 && sheetDisplayed() }
        Espresso.closeSoftKeyboard()
        Espresso.pressBack()
        composeRule.waitUntil(timeoutMillis = 5_000) { imeBottom == 0 && !sheetDisplayed() }

        composeRule.mainClock.autoAdvance = false

        composeRule.onNodeWithTag(AddItemTags.FIELD).performClick()

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

        // 画面が落ち着いたあと: シートは表示されたまま、入力欄にフォーカスがある
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(AddItemTags.SHEET_INPUT).assertIsDisplayed()
        composeRule.onNodeWithTag(AddItemTags.SHEET_INPUT).assertIsFocused()
        assertWithMessage("落ち着いたあともキーボードが出ている").that(imeBottom).isGreaterThan(0)
    }

    private companion object {
        const val MAX_WAIT_MILLIS = 5_000L
        // 画面の状態を読む間隔。短くしすぎるとメインスレッドを占有し、キーボードの表示が遅れる
        const val POLL_MILLIS = 100L
    }
}
