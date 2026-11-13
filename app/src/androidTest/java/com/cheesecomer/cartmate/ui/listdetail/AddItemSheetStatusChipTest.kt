package com.cheesecomer.cartmate.ui.listdetail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cheesecomer.cartmate.domain.input.Suggestion
import com.cheesecomer.cartmate.domain.model.ProductId
import com.cheesecomer.cartmate.ui.theme.CartMateTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 未入力のサジェストの候補に、買い時の状態と「よく買う」のチップを出す（docs/design/screens/product-often-suggestions-sheet） */
@RunWith(AndroidJUnit4::class)
class AddItemSheetStatusChipTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun row(name: String, note: SuggestionNote) =
        SuggestionRow(Suggestion(name, lastQuantity = null, lastUnit = null, productId = ProductId(name)), note)

    @Test
    fun 時期が近い商品には状態を_よく買う商品にはよく買うを出し_補足も残す() {
        composeRule.setContent {
            CartMateTheme {
                AddItemSheetContent(
                    value = TextFieldValue(""),
                    onValueChange = {},
                    onEvent = {},
                    suggestions = SheetSuggestions.Items(
                        listOf(
                            row("麦茶", SuggestionNote.Due(intervalDays = 9, daysSinceLast = 10)),
                            row("食パン", SuggestionNote.Due(intervalDays = 4, daysSinceLast = 4)),
                            row("牛乳", SuggestionNote.Due(intervalDays = 7, daysSinceLast = 6)),
                            row("ヨーグルト", SuggestionNote.Frequent(purchaseDays = 4, daysSinceLast = 8)),
                        ),
                    ),
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("1日過ぎ").assertIsDisplayed()
        composeRule.onNodeWithText("今日が目安").assertIsDisplayed()
        composeRule.onNodeWithText("あと1日").assertIsDisplayed()
        composeRule.onNodeWithText("よく買う").assertIsDisplayed()
        composeRule.onNodeWithText("約9日ごと・最後は10日前").assertIsDisplayed()
        composeRule.onNodeWithText("4回購入・最後は8日前").assertIsDisplayed()
    }

    @Test
    fun 入力中の補完の候補にはチップを出さない() {
        composeRule.setContent {
            CartMateTheme {
                AddItemSheetContent(
                    value = TextFieldValue("ぎゅう"),
                    onValueChange = {},
                    onEvent = {},
                    suggestions = SheetSuggestions.Items(
                        listOf(row("牛乳", SuggestionNote.Completion(alreadyAdded = false, early = null))),
                    ),
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("牛乳").assertIsDisplayed()
        composeRule.onNodeWithText("よく買う").assertDoesNotExist()
        composeRule.onNodeWithText("日過ぎ", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("あと", substring = true).assertDoesNotExist()
    }
}
