package com.cheesecomer.cartmate.screenshots

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cheesecomer.cartmate.FirebaseEmulator
import com.cheesecomer.cartmate.MainActivity
import com.cheesecomer.cartmate.ui.listdetail.AddItemTags
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.ceil

/**
 * アプリを実際に操作して、画面のスクリーンショットを撮る（docs/testing.md「スクリーンショット」）。
 * Firebase のエミュレータにテストデータ（tools/testdata/seed.py --showcase）を入れ、エミュレータにつなぐデバッグ版で動かす。
 * tools/screenshots/take.sh から `-e screenshots true` を付けて実行する。通常のテストの実行では飛ばす
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotTour {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var scenario: ActivityScenario<MainActivity>
    private val outputDir by lazy {
        File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    @Before
    fun requireEmulator() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("screenshots") == "true")
        assumeTrue(FirebaseEmulator.enabled)
        FirebaseAuth.getInstance().signOut()
        // ストアや README に載せるので、ログイン画面にエミュレータ用のボタンを出さない。ログインは直接する
        FirebaseEmulator.testUserSignInVisible = false
    }

    @After
    fun restoreTestUserSignIn() {
        FirebaseEmulator.testUserSignInVisible = true
    }

    @Test
    fun 全画面を撮る() {
        scenario = ActivityScenario.launch(MainActivity::class.java)

        waitFor(hasText("Google でログイン"))
        shoot("01-login")

        runBlocking { FirebaseEmulator.signInTestUser() }
        // アプリのデータを消した直後は、Firestore のエミュレータから最初のデータが届くまで時間がかかることがある
        waitFor(hasText("誕生日の準備"), timeout = FIRST_LOAD_TIMEOUT)
        shoot("02-lists")

        click(hasText("リスト作成"))
        waitFor(hasText("新しいリストを作成"))
        shootSheet("03-create-list-sheet")
        backUntilGone(hasText("新しいリストを作成"))

        click(hasText("スーパー"))
        waitFor(hasText("豚こま切れ肉"))
        shoot("04-list-detail", full = true)

        // キーボードなしの方は、ストアの掲載画像で時期が近い商品とよく買う商品を1画面に見せるのに使う
        click(hasTestTag(AddItemTags.FIELD))
        waitFor(hasText("回購入", substring = true))
        shootSheet("05-suggestions")

        // キーボードを出し直してから入力する
        click(hasTestTag(AddItemTags.SHEET_INPUT))
        composeRule.onNode(hasTestTag(AddItemTags.SHEET_INPUT)).performTextInput("茶")
        waitFor(hasText("数量を設定して追加"))
        shootSheet("06-suggestions-typing")

        click(hasText("緑茶"))
        waitFor(hasText("商品の詳細入力"))
        shoot("07-item-entry", full = true)
        click(hasContentDescription("閉じる"))

        // 似た名前の商品がある新しい名前では、「『ヨーグルト』と同じ商品として扱う」を出す
        waitFor(hasTestTag(AddItemTags.SHEET_INPUT))
        composeRule.onNode(hasTestTag(AddItemTags.SHEET_INPUT)).performTextReplacement("プレーンヨーグルト")
        click(hasText("数量を設定して追加"))
        waitFor(hasText("「ヨーグルト」と同じ商品として扱う"))
        shoot("07-item-entry-merge", full = true)
        // カードを押すと、チェックが入る
        click(hasText("「ヨーグルト」と同じ商品として扱う"))
        // チェックの状態はカードに、文字はその子にあるので、まとめた要素で探す
        waitFor(hasText("「ヨーグルト」と同じ商品として扱う") and isOn(), useUnmergedTree = false)
        shoot("07-item-entry-merge-selected", full = true)
        click(hasContentDescription("閉じる"))
        backUntilGone(hasTestTag(AddItemTags.SHEET_INPUT))

        click(hasContentDescription("豚こま切れ肉のメニュー"))
        click(hasText("編集"))
        waitFor(hasText("商品の編集"))
        shoot("08-item-edit", full = true)
        backUntilGone(hasText("商品の編集"))

        backUntilGone(hasTestTag(AddItemTags.FIELD))
        click(hasText("誕生日の準備"))
        waitFor(hasText("クラッカー"))
        shoot("09-list-detail-private", full = true)
        backUntilGone(hasTestTag(AddItemTags.FIELD))

        click(hasContentDescription("家族"))
        waitFor(hasText("山田 花子"))
        shoot("10-family", full = true)

        scrollAndClick(hasText("家族から抜ける"))
        waitFor(hasText("家族から抜けますか？"))
        shoot("11-family-leave-dialog")
        click(hasText("キャンセル"))

        scrollAndClick(hasText("商品名の管理"))
        waitFor(hasText("登録商品", substring = true))
        shoot("12-product-names", full = true)

        // 一覧は遅延して並べるので、画面の外の商品は、スクロールするまで見つからない
        composeRule.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText("牛乳"))
        click(hasText("牛乳"))
        waitFor(hasText("購入履歴を見る"))
        shoot("13-product-detail", full = true)

        scrollAndClick(hasText("購入履歴を見る"))
        waitFor(hasText("購入記録一覧"))
        shoot("14-purchase-history", full = true)
        backUntilGone(hasText("購入記録一覧"))

        scrollAndClick(hasText("商品をまとめる"))
        waitFor(hasText("残す商品"))
        shoot("15-merge-products", full = true)
    }

    /** 見つからなければ、そのときの画面を failed.png に残す（画面の変更で操作の手順が合わなくなったとき、原因を見るため） */
    private fun waitFor(matcher: SemanticsMatcher, timeout: Long = TIMEOUT, useUnmergedTree: Boolean = true) {
        runCatching {
            composeRule.waitUntil(timeout) { composeRule.onAllNodes(matcher, useUnmergedTree).fetchSemanticsNodes().isNotEmpty() }
        }.onFailure {
            shoot(FAILED)
            throw AssertionError("見つからない: ${matcher.description}", it)
        }
    }

    private fun click(matcher: SemanticsMatcher) {
        waitFor(matcher)
        composeRule.onAllNodes(matcher, useUnmergedTree = true).onFirst().performClick()
    }

    private fun scrollAndClick(matcher: SemanticsMatcher) {
        waitFor(matcher)
        composeRule.onAllNodes(matcher, useUnmergedTree = true).onFirst().performScrollTo().performClick()
    }

    /**
     * 戻るキーを押して、[matcher] の要素が消えるまで待つ（キーボードが出ていると、1回目はキーボードだけが閉じる）。
     * シートが閉じるアニメーションの途中で押し直すと1画面戻りすぎるので、押すたびに消えるのを少し待つ
     */
    private fun backUntilGone(matcher: SemanticsMatcher) {
        // 前の画面に戻る途中で押すと、もう1画面戻ってしまうので、表示されてから押す
        waitFor(matcher)
        repeat(3) {
            Espresso.pressBackUnconditionally()
            val gone = runCatching {
                composeRule.waitUntil(BACK_TIMEOUT) {
                    composeRule.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
                }
            }.isSuccess
            if (gone) return
        }
        error("戻っても消えない: ${matcher.description}")
    }

    /** 画面が落ち着くのを待つ（キーボードやシートのアニメーションは Compose の待機の対象外） */
    private fun settle() {
        composeRule.waitForIdle()
        SystemClock.sleep(SETTLE_MILLIS)
        composeRule.waitForIdle()
    }

    /**
     * シートを、キーボードを出したまま（`-keyboard`）と閉じたあと（`-no-keyboard`）の2枚撮る。
     * キーボードが出きってから使う人と同じく戻るキーで閉じる（シートは出たまま）
     */
    private fun shootSheet(name: String) {
        waitForKeyboard(visible = true)
        shoot("$name-keyboard")
        Espresso.pressBackUnconditionally()
        waitForKeyboard(visible = false)
        shoot("$name-no-keyboard")
    }

    /**
     * ソフトキーボードが出る（消える）まで待つ。シートはキーボードが出るまで画面の下に隠して配置しておくので、
     * シートの文字が見つかっても、まだ見えているとは限らない
     */
    private fun waitForKeyboard(visible: Boolean) {
        runCatching {
            composeRule.waitUntil(TIMEOUT) { keyboardVisible() == visible }
        }.onFailure {
            shoot(FAILED)
            throw AssertionError("キーボードが${if (visible) "出ない" else "消えない"}", it)
        }
    }

    private fun keyboardVisible(): Boolean {
        var visible = false
        scenario.onActivity { activity ->
            visible = ViewCompat.getRootWindowInsets(activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
        return visible
    }

    /**
     * 画面が落ち着くのを待ってから、画面全体を撮る。[full] なら、スクロールしないと見えない部分があるとき、
     * 画面を縦に伸ばして全体が収まるようにした1枚（`-full`）も撮る
     */
    private fun shoot(name: String, full: Boolean = false) {
        capture(name)
        if (full) captureFull("$name-full")
    }

    /** スクロールできる量だけ画面を縦に伸ばして撮り、元の大きさに戻す（伸ばすと画面は作り直されるが、表示していた画面のまま） */
    private fun captureFull(name: String) {
        var overflow = scrollOverflow()
        if (overflow <= 0) return
        var height = SCREEN_HEIGHT
        // 遅延して並べる一覧は、スクロールできる量が見積もりなので、伸ばしたあとも残っていれば足す
        repeat(MAX_RESIZES) {
            if (overflow > 0) {
                height += overflow
                resizeScreen(height)
                overflow = scrollOverflow()
            }
        }
        capture(name)
        resizeScreen(SCREEN_HEIGHT)
    }

    /** 縦にスクロールできる要素のうち、いちばん大きいスクロールの残り（px） */
    private fun scrollOverflow(): Int {
        composeRule.waitForIdle()
        return composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .fetchSemanticsNodes()
            .maxOfOrNull { node ->
                val range = node.config[SemanticsProperties.VerticalScrollAxisRange]
                ceil(range.maxValue() - range.value()).toInt()
            } ?: 0
    }

    private fun resizeScreen(height: Int) {
        val output = instrumentation.uiAutomation.executeShellCommand("wm size ${SCREEN_WIDTH}x$height")
        ParcelFileDescriptor.AutoCloseInputStream(output).use { it.readBytes() }
        composeRule.waitUntil(TIMEOUT) {
            var current = 0
            scenario.onActivity { current = it.window.decorView.height }
            current == height
        }
        settle()
    }

    /** 画面が落ち着くのを待ってから、画面全体を撮る */
    private fun capture(name: String) {
        settle()
        if (name != FAILED) {
            check(composeRule.onAllNodes(hasText("オフライン表示中", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isEmpty()) {
                "オフラインの表示で撮ろうとした: $name（tools/screenshots/take.sh で端末をオンラインにする）"
            }
        }
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot()) { "スクリーンショットを撮れない: $name" }
        File(outputDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {
        const val TIMEOUT = 15_000L
        const val FIRST_LOAD_TIMEOUT = 60_000L
        const val SETTLE_MILLIS = 1_000L
        const val BACK_TIMEOUT = 2_000L
        const val FAILED = "failed"

        /** tools/screenshots/take.sh が設定する画面の大きさ */
        const val SCREEN_WIDTH = 1080
        const val SCREEN_HEIGHT = 1920
        const val MAX_RESIZES = 3
    }
}
