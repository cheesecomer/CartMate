package com.cheesecomer.cartmate.ui.listdetail

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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cheesecomer.cartmate.data.memory.InMemoryStore
import com.cheesecomer.cartmate.domain.list.ShoppingRound
import com.cheesecomer.cartmate.domain.model.NewItem
import com.cheesecomer.cartmate.domain.model.NewList
import com.cheesecomer.cartmate.domain.model.UserId
import com.cheesecomer.cartmate.domain.model.Visibility
import com.cheesecomer.cartmate.domain.product.NormalizationDictionary
import com.cheesecomer.cartmate.testing.KeyboardTimingTest
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

/** 買い物を終えてすぐ、買い忘れを足す（フィードバック #013。docs/requirements.md §3.2「前回の買い物の片付け」） */
@RunWith(AndroidJUnit4::class)
class ListDetailRoundTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val clock = Clock.systemUTC()
    private val store = InMemoryStore(clock) { UserId("taro") }
    private val listId = runBlocking { store.createList(NewList("スーパー", null, Visibility.SHARED)) }

    /** 画面に届いているソフトキーボードの高さ（px） */
    @Volatile
    private var imeBottom = 0

    /**
     * 「牛乳」をチェックし終えた（買い終わった）リストを開き、シートで「卵」を Enter で追加する。
     * [keepKeyboard] が false なら、キーボードを閉じてから追加する。キーボードの表示はエミュレータでは遅れることがあり、
     * ダイアログが出たあとも閉じきらずに戻るキーを受けてしまうため、通常のテストはキーボードのタイミングに依存させない
     */
    private fun addAfterShopping(keepKeyboard: Boolean = false) {
        runBlocking {
            val milk = store.addItem(NewItem(listId, "牛乳", 1.0, "本", null, Visibility.SHARED)).id
            store.completeItem(milk, clock.instant())
        }
        val viewModel = ListDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("listId" to listId.value)),
            listRepository = store,
            itemRepository = store,
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
            val bottom = WindowInsets.ime.getBottom(LocalDensity.current)
            SideEffect { imeBottom = bottom }
            CartMateTheme {
                ListDetailScreen(onBack = {}, onOpenEntry = { _, _, _ -> }, onEditItem = {}, viewModel = viewModel)
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { displayed("牛乳") }

        composeRule.onNodeWithTag(AddItemTags.FIELD).performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) { (!keepKeyboard || imeBottom > 0) && displayedTag(AddItemTags.SHEET_INPUT) }
        composeRule.onNodeWithTag(AddItemTags.SHEET_INPUT).performTextInput("卵")
        if (!keepKeyboard) {
            Espresso.closeSoftKeyboard()
            composeRule.waitUntil(timeoutMillis = 5_000) { imeBottom == 0 }
        }
        composeRule.onNodeWithTag(AddItemTags.SHEET_INPUT).performImeAction()
        composeRule.waitUntil(timeoutMillis = 5_000) { displayed(DIALOG_TITLE) }
    }

    private fun displayed(text: String) = runCatching { composeRule.onNodeWithText(text).assertIsDisplayed() }.isSuccess

    private fun displayedTag(tag: String) = runCatching { composeRule.onNodeWithTag(tag).assertIsDisplayed() }.isSuccess

    /** 一覧に表示する項目の名前（片付けたチェック済みを除く） */
    private fun shownNames(): List<String> = runBlocking {
        val clearedAt = store.observeLists().first().single { it.id == listId }.clearedAt
        ShoppingRound.visible(store.observeListItems(listId, Instant.EPOCH).first(), Instant.EPOCH, clearedAt).map { it.inputName }
    }

    @Test
    fun 買い忘れと答えると_チェック済みを残したまま_続けて追加できる() {
        addAfterShopping()

        composeRule.onNodeWithText("買い忘れ").performClick()

        // 落ち着いたあと: ダイアログは閉じ、今買った物と追加した物が並ぶ。シートは開いたまま
        composeRule.waitUntil(timeoutMillis = 5_000) { !displayed(DIALOG_TITLE) }
        composeRule.waitForIdle()
        assertWithMessage("チェック済みの牛乳が残る").that(shownNames()).containsExactly("牛乳", "卵").inOrder()
        composeRule.onNodeWithTag(AddItemTags.SHEET_INPUT).assertIsDisplayed()

        // 続けて足しても、未購入が残っているので聞かない
        composeRule.onNodeWithTag(AddItemTags.SHEET_INPUT).performTextInput("パン")
        composeRule.onNodeWithTag(AddItemTags.SHEET_INPUT).performImeAction()
        composeRule.waitUntil(timeoutMillis = 5_000) { shownNames().contains("パン") }
        composeRule.waitForIdle()
        assertWithMessage("2件目以降の追加では聞かない").that(displayed(DIALOG_TITLE)).isFalse()
        assertWithMessage("チェック済みの牛乳が残る").that(shownNames()).containsExactly("牛乳", "卵", "パン").inOrder()
    }

    @Test
    fun 次の買い物と答えると_チェック済みを片付ける() {
        addAfterShopping()

        composeRule.onNodeWithText("次の買い物").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { !displayed(DIALOG_TITLE) }
        composeRule.waitForIdle()
        assertWithMessage("前回のチェック済みは片付ける").that(shownNames()).containsExactly("卵")
        // シートを閉じたあとの一覧にも、片付けた牛乳は出ない
        Espresso.pressBack()
        composeRule.waitUntil(timeoutMillis = 5_000) { !displayedTag(AddItemTags.SHEET_INPUT) }
        composeRule.waitForIdle()
        assertWithMessage("片付けた牛乳を一覧に出さない").that(displayed("牛乳")).isFalse()
        composeRule.onNodeWithText("卵").assertIsDisplayed()
    }

    @Test
    fun 答えずに閉じると_片付けない() {
        addAfterShopping()

        // ダイアログのウィンドウにフォーカスが来るのを待ってから戻るキーを押す。Espresso.pressBack は画面のウィンドウの
        // フォーカスを待つので、ダイアログが出ている間は使えない。待たずにキーを送ると、画面のほう（シート）に届くことがある
        Espresso.onView(isRoot()).inRoot(isDialog()).perform(ViewActions.pressBack())

        composeRule.waitUntil(timeoutMillis = 5_000) { !displayed(DIALOG_TITLE) }
        composeRule.waitForIdle()
        assertWithMessage("閉じたら片付けない").that(shownNames()).containsExactly("牛乳", "卵").inOrder()
        composeRule.onNodeWithTag(AddItemTags.SHEET_INPUT).assertIsDisplayed()
    }

    /** 連続入力の途中で聞いても、答えたあとはキーボードとシートの入力欄に戻って、続けて入力できる */
    @KeyboardTimingTest
    @Test
    fun 答えたあとは_キーボードが出たままシートの入力欄にフォーカスが戻る() {
        addAfterShopping(keepKeyboard = true)

        composeRule.onNodeWithText("買い忘れ").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { !displayed(DIALOG_TITLE) && imeBottom > 0 }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(AddItemTags.SHEET_INPUT).assertIsFocused()
        assertWithMessage("落ち着いたあともキーボードが出ている").that(imeBottom).isGreaterThan(0)
    }

    private companion object {
        const val DIALOG_TITLE = "買い忘れですか？"
    }
}
