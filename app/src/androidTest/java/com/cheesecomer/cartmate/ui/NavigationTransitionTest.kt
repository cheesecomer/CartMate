package com.cheesecomer.cartmate.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cheesecomer.cartmate.data.memory.FixedAccountRepository
import com.cheesecomer.cartmate.data.memory.InMemoryStore
import com.cheesecomer.cartmate.domain.model.ItemStatus
import com.cheesecomer.cartmate.domain.model.ListId
import com.cheesecomer.cartmate.domain.model.NewItem
import com.cheesecomer.cartmate.domain.model.NewList
import com.cheesecomer.cartmate.domain.model.UserId
import com.cheesecomer.cartmate.domain.model.Visibility
import com.cheesecomer.cartmate.domain.product.NormalizationDictionary
import com.cheesecomer.cartmate.ui.itementry.LastVisibilityStore
import com.cheesecomer.cartmate.ui.listdetail.ListDetailRoute
import com.cheesecomer.cartmate.ui.listdetail.ListDetailScreen
import com.cheesecomer.cartmate.ui.listdetail.ListDetailViewModel
import com.cheesecomer.cartmate.ui.lists.ListsScreen
import com.cheesecomer.cartmate.ui.lists.ListsViewModel
import com.cheesecomer.cartmate.ui.suggestions.SuggestionSource
import com.cheesecomer.cartmate.ui.theme.CartMateTheme
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant

/**
 * 画面の遷移中のタップ（フィードバック #015・#017）。遷移のアニメーションの間は、離れようとしている画面もまだ表示されているので、
 * そこへのタップで項目がチェックされたり、もう一度戻ってリスト一覧まで消えたりしないこと。アプリと同じ [screen] で行き先を作る
 */
@RunWith(AndroidJUnit4::class)
class NavigationTransitionTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val clock = Clock.systemUTC()
    private val store = InMemoryStore(clock) { UserId("taro") }
    private val suggestions = SuggestionSource(store, store, store, clock, { flowOf(NormalizationDictionary.EMPTY) }, Dispatchers.Default)
    private val lastVisibility = LastVisibilityStore()

    private fun setUp(): ListId {
        val listId = runBlocking { store.createList(NewList("スーパー", null, Visibility.SHARED)) }
        runBlocking { store.addItem(NewItem(listId, "牛乳", 1.0, "本", null, Visibility.SHARED)) }
        composeRule.setContent {
            val navController = rememberNavController()
            CartMateTheme {
                NavHost(navController, startDestination = ListsRoute) {
                    screen<ListsRoute> {
                        val viewModel = viewModel {
                            ListsViewModel(store, store, store, clock, FixedAccountRepository(null), { flowOf(true) }, SuggestionSource(store, store, store, clock, { flowOf(NormalizationDictionary.EMPTY) }, Dispatchers.Default))
                        }
                        ListsScreen(onOpenList = { navController.navigate(ListDetailRoute(it.value)) }, viewModel = viewModel)
                    }
                    screen<ListDetailRoute> {
                        val viewModel = viewModel {
                            ListDetailViewModel(
                                createSavedStateHandle(), store, store, lastVisibility, clock,
                                { UserId("taro") }, { flowOf(emptyList()) }, suggestions,
                            )
                        }
                        ListDetailScreen(
                            onBack = { navController.popBackStack() },
                            onOpenEntry = { _, _, _ -> },
                            onEditItem = {},
                            entryResults = flowOf(),
                            viewModel = viewModel,
                        )
                    }
                }
            }
        }
        // リスト詳細を開き、遷移が終わるまで待つ
        composeRule.onNodeWithText("スーパー").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithText("牛乳").assertIsDisplayed() }.isSuccess
        }
        composeRule.waitForIdle()
        return listId
    }

    private fun centerOf(bounds: androidx.compose.ui.geometry.Rect) = Offset(bounds.center.x, bounds.center.y)

    /** 戻るボタンを押し、遷移のアニメーションの途中で [position] をタップする */
    private fun backThenTapDuringTransition(position: Offset) {
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithContentDescription("戻る").performClick()
        composeRule.mainClock.advanceTimeBy(TRANSITION_MIDWAY_MILLIS)
        composeRule.onRoot().performTouchInput { click(position) }
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
    }

    @Test
    fun リスト詳細から戻った直後に一覧をタップしても_項目はチェックされない() {
        val listId = setUp()
        val item = centerOf(composeRule.onNodeWithText("牛乳").fetchSemanticsNode().boundsInRoot)

        backThenTapDuringTransition(item)

        // 画面が落ち着いたあと: 牛乳は未購入のまま
        val items = runBlocking { store.observeListItems(listId, Instant.EPOCH).first() }
        assertWithMessage("戻る途中のタップで、離れたリスト詳細の項目がチェックされた")
            .that(items.single().status).isEqualTo(ItemStatus.OPEN)
    }

    @Test
    fun リスト詳細から戻った直後に一覧のタイトルの位置をタップしても_白紙にならず一覧のまま() {
        setUp()
        // リスト一覧のタイトル「CartMate」と同じ左上にある、リスト詳細の戻るボタンの位置
        val back = centerOf(composeRule.onNodeWithContentDescription("戻る").fetchSemanticsNode().boundsInRoot)

        backThenTapDuringTransition(back)

        // 画面が落ち着いたあと: リスト一覧が表示されている
        val listsShown = runCatching { composeRule.onNodeWithText("CartMate").assertIsDisplayed() }.isSuccess
        assertWithMessage("戻る途中のタップで、もう一度戻ってリスト一覧が消えた").that(listsShown).isTrue()
        composeRule.onNodeWithText("スーパー").assertIsDisplayed()
    }

    private companion object {
        /** 遷移のアニメーション（700ms）の途中 */
        const val TRANSITION_MIDWAY_MILLIS = 200L
    }
}
