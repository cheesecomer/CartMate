package com.cheesecomer.cartmate.screenshots

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
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
        ActivityScenario.launch(MainActivity::class.java)

        waitFor(hasText("Google でログイン"))
        shoot("01-login")

        runBlocking { FirebaseEmulator.signInTestUser() }
        waitFor(hasText("誕生日の準備"))
        shoot("02-lists")

        click(hasText("リスト作成"))
        waitFor(hasText("新しいリストを作成"))
        shoot("03-create-list-sheet")
        backUntilGone(hasText("新しいリストを作成"))

        click(hasText("スーパー"))
        waitFor(hasText("豚こま切れ肉"))
        shoot("04-list-detail")

        click(hasTestTag(AddItemTags.FIELD))
        waitFor(hasTestTag(AddItemTags.SUGGESTION))
        shoot("05-suggestions")

        composeRule.onNode(hasTestTag(AddItemTags.SHEET_INPUT)).performTextInput("茶")
        waitFor(hasText("数量を設定して追加"))
        shoot("06-suggestions-typing")

        click(hasText("緑茶"))
        waitFor(hasText("商品の詳細入力"))
        shoot("07-item-entry")
        click(hasContentDescription("閉じる"))
        backUntilGone(hasTestTag(AddItemTags.SHEET_INPUT))

        click(hasContentDescription("豚こま切れ肉を編集"))
        waitFor(hasText("商品の編集"))
        shoot("08-item-edit")
        backUntilGone(hasText("商品の編集"))

        backUntilGone(hasTestTag(AddItemTags.FIELD))
        click(hasText("誕生日の準備"))
        waitFor(hasText("クラッカー"))
        shoot("09-list-detail-private")
        backUntilGone(hasTestTag(AddItemTags.FIELD))

        click(hasContentDescription("家族"))
        waitFor(hasText("山田 花子"))
        shoot("10-family")

        scrollAndClick(hasText("家族から抜ける"))
        waitFor(hasText("家族から抜けますか？"))
        shoot("11-family-leave-dialog")
        click(hasText("キャンセル"))

        scrollAndClick(hasText("商品名の管理"))
        waitFor(hasText("登録商品", substring = true))
        shoot("12-product-names")

        // 一覧は遅延して並べるので、画面の外の商品は、スクロールするまで見つからない
        composeRule.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText("牛乳"))
        click(hasText("牛乳"))
        waitFor(hasText("購入履歴を見る"))
        shoot("13-product-detail")

        scrollAndClick(hasText("購入履歴を見る"))
        waitFor(hasText("購入記録一覧"))
        shoot("14-purchase-history")
        backUntilGone(hasText("購入記録一覧"))

        scrollAndClick(hasText("商品をまとめる"))
        waitFor(hasText("残す商品"))
        shoot("15-merge-products")
    }

    /** 見つからなければ、そのときの画面を failed.png に残す（画面の変更で操作の手順が合わなくなったとき、原因を見るため） */
    private fun waitFor(matcher: SemanticsMatcher) {
        runCatching {
            composeRule.waitUntil(TIMEOUT) { composeRule.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
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

    /** 画面が落ち着くのを待ってから（キーボードやシートのアニメーションは Compose の待機の対象外）、画面全体を撮る */
    private fun shoot(name: String) {
        composeRule.waitForIdle()
        SystemClock.sleep(SETTLE_MILLIS)
        composeRule.waitForIdle()
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
        const val SETTLE_MILLIS = 1_000L
        const val BACK_TIMEOUT = 2_000L
        const val FAILED = "failed"
    }
}
