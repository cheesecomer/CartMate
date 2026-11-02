package com.cheesecomer.cartmate.ui.listdetail

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cheesecomer.cartmate.data.memory.InMemoryStore
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.delay
import com.cheesecomer.cartmate.domain.repository.ItemRepository
import com.cheesecomer.cartmate.domain.model.ListId
import com.cheesecomer.cartmate.data.firestore.combineListens
import com.cheesecomer.cartmate.domain.model.Item
import com.cheesecomer.cartmate.domain.model.ItemStatus
import com.cheesecomer.cartmate.domain.model.NewItem
import com.cheesecomer.cartmate.domain.model.NewList
import com.cheesecomer.cartmate.domain.model.UserId
import com.cheesecomer.cartmate.domain.model.Visibility
import com.cheesecomer.cartmate.domain.product.NormalizationDictionary
import com.cheesecomer.cartmate.ui.itementry.LastVisibilityStore
import com.cheesecomer.cartmate.ui.suggestions.SuggestionSource
import com.cheesecomer.cartmate.ui.theme.CartMateTheme
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant

/** 買い物中に、一覧をスクロールしながら項目を続けてチェックする（フィードバック #011・#012） */
@RunWith(AndroidJUnit4::class)
class ListDetailCheckingTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val clock = Clock.systemUTC()
    private val store = InMemoryStore(clock) { UserId("taro") }
    private val listId = runBlocking { store.createList(NewList("スーパー", null, Visibility.SHARED)) }

    /**
     * Firestore の項目の読み方を真似たリポジトリ。未購入とチェック済みを別々のクエリで購読して [combineListens] でまとめる。
     * チェックすると、項目はまず未購入のクエリから消え、少し遅れてチェック済みのクエリに現れる（別々の通知で届く）
     */
    private val firestoreLikeItems = object : ItemRepository by store {
        override fun observeListItems(listId: ListId, completedSince: Instant): Flow<List<Item>> {
            val all = store.observeListItems(listId, completedSince)
            val open = { all.map { items -> items.filter { it.status == ItemStatus.OPEN } } }
            val completed = { all.map { items -> items.filter { it.status == ItemStatus.COMPLETED } }.onEach { delay(QUERY_GAP_MILLIS) } }
            return combineListens(listOf(open, completed), awaitPendingWrites = {})
                .map { items -> items.distinctBy { it.id }.sortedBy { it.sortKey } }
        }
    }

    private fun setContent(itemCount: Int, itemRepository: ItemRepository = store) {
        runBlocking {
            (1..itemCount).forEach { store.addItem(NewItem(listId, "商品$it", 1.0, "個", null, Visibility.SHARED)) }
        }
        val viewModel = ListDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("listId" to listId.value)),
            listRepository = store,
            itemRepository = itemRepository,
            lastVisibility = LastVisibilityStore(),
            clock = clock,
            currentUser = { UserId("taro") },
            memberDirectory = { flowOf(emptyList()) },
            suggestionSource = SuggestionSource(store, store, store, clock, { flowOf(NormalizationDictionary.EMPTY) }, Dispatchers.Default),
        )
        composeRule.runOnUiThread {
            composeRule.activity.enableEdgeToEdge()
            composeRule.activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        composeRule.setContent {
            CartMateTheme {
                ListDetailScreen(onBack = {}, onOpenEntry = { _, _, _ -> }, onEditItem = {}, onOpenProduct = {}, viewModel = viewModel)
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithText("商品1").assertIsDisplayed() }.isSuccess
        }
    }

    private fun scrollTo(name: String) {
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(name))
        composeRule.waitForIdle()
    }

    /** 指で一番下までスクロールする（最後の項目は、商品追加欄の上に来る）。指は一覧の中央から動かす（下端は商品追加欄に当たる） */
    private fun scrollToEnd() {
        scrollTo("商品20")
        repeat(3) { composeRule.onNode(hasScrollToNodeAction()).performTouchInput { swipeUp(startY = centerY, endY = top) } }
        composeRule.waitForIdle()
    }

    private fun topOf(name: String): Float = composeRule.onNodeWithText(name).fetchSemanticsNode().boundsInRoot.top

    private fun statusOf(name: String) =
        runBlocking { store.observeListItems(listId, Instant.EPOCH).first() }.single { it.inputName == name }.status

    /** チェックの Snackbar やカードの切り替えが落ち着くまで待つ */
    private fun settle() {
        composeRule.waitForIdle()
        Thread.sleep(SETTLE_MILLIS)
        composeRule.waitForIdle()
    }

    @Test
    fun スクロールした途中で続けてチェックしても_一覧の表示位置は動かない() {
        setContent(itemCount = 20)
        // 商品12 が一覧の一番上（トップバーのすぐ下）に来る
        scrollTo("商品12")
        val before = topOf("商品12")

        composeRule.onNodeWithText("商品13").performClick()
        settle()
        assertWithMessage("商品13").that(statusOf("商品13")).isEqualTo(ItemStatus.COMPLETED)
        assertWithMessage("1件目のチェックのあと、商品12の位置").that(topOf("商品12")).isWithin(1f).of(before)

        composeRule.onNodeWithText("商品14").performClick()
        settle()
        assertWithMessage("商品14").that(statusOf("商品14")).isEqualTo(ItemStatus.COMPLETED)
        assertWithMessage("続けて2件目をチェックしたあと、商品12の位置").that(topOf("商品12")).isWithin(1f).of(before)
    }

    /** フィードバック #011 の再現条件。スクロールが必要な件数で、最終行まで表示しているときに、任意の項目をタップする */
    @Test
    fun 最終行まで表示しているときに項目をタップしても_先頭行の表示位置は動かない() {
        setContent(itemCount = 20)
        scrollToEnd()
        val first = "商品13"
        val before = topOf(first)

        composeRule.onNodeWithText("商品16").performClick()
        settle()
        assertWithMessage("商品16").that(statusOf("商品16")).isEqualTo(ItemStatus.COMPLETED)
        assertWithMessage("1件目のチェックのあと、%s の位置", first).that(topOf(first)).isWithin(1f).of(before)

        composeRule.onNodeWithText("商品18").performClick()
        settle()
        assertWithMessage("商品18").that(statusOf("商品18")).isEqualTo(ItemStatus.COMPLETED)
        assertWithMessage("2件目のチェックのあと、%s の位置", first).that(topOf(first)).isWithin(1f).of(before)
    }

    /**
     * #011 を Firestore の読み方で再現する。最終行まで表示しているときに、チェックした項目が一瞬だけ一覧から消えると、
     * 一覧が縮んで下端に合わせて戻り、項目が戻ってきてもそのままずれる
     */
    @Test
    fun 未購入とチェック済みの通知が別々に届いても_最終行まで表示しているときに項目をタップして先頭行が動かない() {
        setContent(itemCount = 20, itemRepository = firestoreLikeItems)
        scrollToEnd()
        val first = "商品13"
        val before = topOf(first)

        composeRule.onNodeWithText("商品16").performClick()
        settle()
        assertWithMessage("商品16").that(statusOf("商品16")).isEqualTo(ItemStatus.COMPLETED)
        assertWithMessage("チェックのあと、%s の位置", first).that(topOf(first)).isWithin(1f).of(before)
    }

    /** 一覧は一番上に見えている項目を基準に位置を保つので、その項目の高さがチェックで変わると、下の項目がずれる */
    @Test
    fun 一番上に見えている項目をチェックしても_その下の項目の表示位置は動かない() {
        setContent(itemCount = 20)
        scrollTo("商品12")
        val before = topOf("商品13")

        composeRule.onNodeWithText("商品12").performClick()
        settle()
        assertWithMessage("商品12").that(statusOf("商品12")).isEqualTo(ItemStatus.COMPLETED)
        assertWithMessage("チェックのあと、商品13の位置").that(topOf("商品13")).isWithin(1f).of(before)
    }

    /** Firestore では、チェックした項目が一瞬だけ一覧から消えた状態が流れ、表示の基準の項目が変わってしまう */
    @Test
    fun 未購入とチェック済みの通知が別々に届いても_一番上の項目をチェックして表示位置が動かない() {
        setContent(itemCount = 20, itemRepository = firestoreLikeItems)
        scrollTo("商品12")
        val before = topOf("商品13")

        composeRule.onNodeWithText("商品12").performClick()
        settle()
        assertWithMessage("商品12").that(statusOf("商品12")).isEqualTo(ItemStatus.COMPLETED)
        assertWithMessage("チェックのあと、商品13の位置").that(topOf("商品13")).isWithin(1f).of(before)

        // 続けてすぐ下の項目をチェックしても動かない
        composeRule.onNodeWithText("商品13").performClick()
        settle()
        assertWithMessage("商品13").that(statusOf("商品13")).isEqualTo(ItemStatus.COMPLETED)
        assertWithMessage("2件目のチェックのあと、商品13の位置").that(topOf("商品13")).isWithin(1f).of(before)
    }

    @Test
    fun チェックの通知が出ている間も_一番下の項目をタップしてチェックできる() {
        setContent(itemCount = 20)
        scrollToEnd()

        composeRule.onNodeWithText("商品19").performClick()
        // 通知の「元に戻す」が出ている（チェックしたカードにも「元に戻す」のボタンがあるので、2つ以上）
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("元に戻す").fetchSemanticsNodes().size >= 2
        }

        // 通知は商品追加欄の範囲に出て、一覧のカードにかぶらない
        val bar = composeRule.onNodeWithTag(AddItemTags.BAR).fetchSemanticsNode().boundsInRoot
        val notice = composeRule.onNodeWithText("商品19 をチェックしました", substring = true).fetchSemanticsNode().boundsInRoot
        assertWithMessage("通知の上端（%s）が商品追加欄の上端（%s）より上にある", notice.top, bar.top).that(notice.top).isAtLeast(bar.top)

        composeRule.onNodeWithText("商品20").performClick()
        settle()
        assertWithMessage("通知が出ている間に、一番下の項目をタップしてもチェックされない")
            .that(statusOf("商品20")).isEqualTo(ItemStatus.COMPLETED)
    }

    private companion object {
        const val SETTLE_MILLIS = 1_000L

        /** 未購入のクエリとチェック済みのクエリの通知の間隔。1フレーム（約16ms）より長く、途中の状態が画面に出る */
        const val QUERY_GAP_MILLIS = 30L
    }
}
