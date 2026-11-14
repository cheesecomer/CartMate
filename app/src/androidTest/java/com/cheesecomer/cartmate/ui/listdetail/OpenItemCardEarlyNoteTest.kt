package com.cheesecomer.cartmate.ui.listdetail

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cheesecomer.cartmate.domain.model.Item
import com.cheesecomer.cartmate.domain.model.ItemId
import com.cheesecomer.cartmate.domain.model.ItemStatus
import com.cheesecomer.cartmate.domain.model.ListId
import com.cheesecomer.cartmate.domain.model.UserId
import com.cheesecomer.cartmate.domain.model.Visibility
import com.cheesecomer.cartmate.domain.suggest.EarlyPurchaseNotice
import com.cheesecomer.cartmate.ui.theme.CartMateTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

/** 早すぎる再購入の項目のカードに、備考の下へ前回の購入と周期を出す（docs/requirements.md §4.5） */
@RunWith(AndroidJUnit4::class)
class OpenItemCardEarlyNoteTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun item(name: String, memo: String? = null) = Item(
        id = ItemId(name),
        listId = ListId("list"),
        inputName = name,
        sortKey = 1.0,
        quantity = 1.0,
        unit = "本",
        memo = memo,
        visibility = Visibility.SHARED,
        status = ItemStatus.OPEN,
        createdAt = Instant.EPOCH,
        createdBy = UserId("taro"),
        completedAt = null,
        completedBy = null,
    )

    @Test
    fun 早すぎる再購入の項目だけ_備考と並べて前回の購入を出す() {
        composeRule.setContent {
            CartMateTheme {
                Column {
                    OpenItemCard(
                        item = item("牛乳", memo = "低脂肪"),
                        showLock = false,
                        onEdit = {},
                        onCheck = {},
                        onDelete = {},
                        early = EarlyPurchaseNotice(LocalDate.of(2026, 10, 18), typicalIntervalDays = 14),
                    )
                    OpenItemCard(
                        item = item("玉ねぎ"),
                        showLock = false,
                        onEdit = {},
                        onCheck = {},
                        onDelete = {},
                        early = EarlyPurchaseNotice(LocalDate.of(2026, 10, 28), typicalIntervalDays = null),
                    )
                    OpenItemCard(item = item("卵"), showLock = false, onEdit = {}, onCheck = {}, onDelete = {})
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("低脂肪").assertIsDisplayed()
        composeRule.onNodeWithText("前回 10/18 に購入・いつもは約14日ごと").assertIsDisplayed()
        composeRule.onNodeWithText("前回 10/28 に購入").assertIsDisplayed()
        composeRule.onNodeWithText("卵").assertIsDisplayed()
        // 早すぎない項目（卵）には出さない
        composeRule.onAllNodes(hasText("前回", substring = true)).assertCountEquals(2)
    }
}
