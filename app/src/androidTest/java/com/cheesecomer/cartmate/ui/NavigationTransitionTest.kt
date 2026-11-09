package com.cheesecomer.cartmate.ui

import com.cheesecomer.cartmate.ui.hints.InMemoryHintStore
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.cheesecomer.cartmate.domain.model.Item
import com.cheesecomer.cartmate.domain.repository.ItemRepository
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant

/**
 * 画面の遷移中のタップ（フィードバック #015・#017）。遷移のアニメーションの間は、離れようとしている画面もまだ表示されているので、
 * そこへのタップで項目がチェックされたり、もう一度戻ってリスト一覧まで消えたりしないこと。
 * あわせて、リストを開く速さ（フィードバック #021）。アプリと同じ [ScreenNavHost] と [screen] で行き先を作る
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class NavigationTransitionTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val clock = Clock.systemUTC()
    private val store = InMemoryStore(clock) { UserId("taro") }
    private val suggestions = SuggestionSource(store, store, store, clock, { flowOf(NormalizationDictionary.EMPTY) }, Dispatchers.Default)
    private val lastVisibility = LastVisibilityStore()

    /** false の間は、リスト詳細の項目の購読が届かない（端末のキャッシュの読み込みに時間がかかっている） */
    private val listItemsArrived = MutableStateFlow(true)
    private val items = object : ItemRepository by store {
        override fun observeListItems(listId: ListId, completedSince: Instant): Flow<List<Item>> =
            listItemsArrived.filter { it }.take(1).flatMapLatest { store.observeListItems(listId, completedSince) }
    }

    /** リスト一覧の「スーパー」のカードの位置 */
    private var listCard = Offset.Zero

    private fun setUp(): ListId {
        val listId = showLists()
        listCard = centerOf(composeRule.onNodeWithText("スーパー").fetchSemanticsNode().boundsInRoot)
        // リスト詳細を開き、遷移が終わるまで待つ
        composeRule.onNodeWithText("スーパー").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithText("牛乳").assertIsDisplayed() }.isSuccess
        }
        composeRule.waitForIdle()
        return listId
    }

    /** 牛乳のあるリスト「スーパー」を作り、リスト一覧を表示する */
    private fun showLists(): ListId {
        val listId = runBlocking { store.createList(NewList("スーパー", null, Visibility.SHARED)) }
        runBlocking { store.addItem(NewItem(listId, "牛乳", 1.0, "本", null, Visibility.SHARED)) }
        composeRule.setContent {
            val navController = rememberNavController()
            CartMateTheme {
                ScreenNavHost(navController, startDestination = ListsRoute) {
                    screen<ListsRoute> {
                        val viewModel = viewModel {
                            ListsViewModel(store, store, store, clock, FixedAccountRepository(null), { flowOf(true) }, SuggestionSource(store, store, store, clock, { flowOf(NormalizationDictionary.EMPTY) }, Dispatchers.Default), InMemoryHintStore(), { flowOf(emptyList()) })
                        }
                        ListsScreen(onOpenList = { navController.navigate(ListDetailRoute(it.id.value, it.name)) }, viewModel = viewModel)
                    }
                    screen<ListDetailRoute> {
                        val viewModel = viewModel {
                            ListDetailViewModel(
                                createSavedStateHandle(), store, items, lastVisibility, clock,
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

    /** 戻った直後のタップが効かず、開くのが遅い（フィードバック #021） */
    @Test
    fun リスト詳細から戻って0_4秒後にリストをタップすると_リスト詳細が開く() {
        setUp()
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithContentDescription("戻る").performClick()
        composeRule.mainClock.advanceTimeBy(TAP_AFTER_BACK_MILLIS)
        composeRule.onRoot().performTouchInput { click(listCard) }
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        // 画面が落ち着いたあと: リスト詳細が開いている
        val opened = runCatching { composeRule.onNodeWithText("牛乳").assertIsDisplayed() }.isSuccess
        assertWithMessage("戻った直後のタップが無視され、リスト詳細が開かなかった").that(opened).isTrue()
    }

    /**
     * 開いた直後に白紙になる、チェック済みが遅れて現れる（フィードバック #021）。リスト一覧で読み込み済みのリスト名と、
     * 未購入・チェック済みの項目を、すぐに出す
     */
    @Test
    fun リストを開くと_項目の購読が届く前から_リスト名と未購入とチェック済みの項目が表示され_届いたあとも変わらない() {
        val listId = showLists()
        val bread = runBlocking { store.addItem(NewItem(listId, "パン", 1.0, "個", null, Visibility.SHARED)) }
        runBlocking { store.completeItem(bread.id, checkedAt = clock.instant()) }
        listItemsArrived.value = false

        composeRule.onNodeWithText("スーパー").performClick()
        composeRule.waitForIdle()

        val shown = runCatching {
            composeRule.onNodeWithText("スーパー").assertIsDisplayed()
            composeRule.onNodeWithText("牛乳").assertIsDisplayed()
            composeRule.onNodeWithText("パン").assertIsDisplayed()
        }.isSuccess
        assertWithMessage("項目の購読が届くまで、リスト名と未購入・チェック済みの項目が表示されなかった").that(shown).isTrue()

        listItemsArrived.value = true
        composeRule.waitForIdle()

        // 画面が落ち着いたあと: 同じ表示のまま
        composeRule.onNodeWithText("スーパー").assertIsDisplayed()
        composeRule.onNodeWithText("牛乳").assertIsDisplayed()
        composeRule.onNodeWithText("パン").assertIsDisplayed()
    }

    private companion object {
        /** 遷移のアニメーション（0.3秒）の途中 */
        const val TRANSITION_MIDWAY_MILLIS = 200L
        /** 遷移のアニメーション（0.3秒）が終わった少しあと */
        const val TAP_AFTER_BACK_MILLIS = 400L
    }
}
