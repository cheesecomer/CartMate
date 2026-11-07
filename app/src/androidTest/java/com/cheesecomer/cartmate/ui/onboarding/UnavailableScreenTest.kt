package com.cheesecomer.cartmate.ui.onboarding

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cheesecomer.cartmate.domain.model.Account
import com.cheesecomer.cartmate.domain.model.UserId
import com.cheesecomer.cartmate.ui.theme.CartMateTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** サーバーに読み取りを断られたときの画面（フィードバック #020） */
@RunWith(AndroidJUnit4::class)
class UnavailableScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun 接続できないことを伝え_家族の作成や参加は出さず_ログアウトできる() {
        var signedOut = 0
        composeRule.setContent {
            CartMateTheme {
                UnavailableContent(
                    account = Account(UserId("taro"), "山田 太郎", "taro@example.com", null),
                    onSignOut = { signedOut++ },
                )
            }
        }

        composeRule.onNodeWithText("サーバーに接続できません").assertIsDisplayed()
        composeRule.onNodeWithText("開き直してください", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("家族と共有する").assertDoesNotExist()
        composeRule.onNodeWithText("招待コードで参加する").assertDoesNotExist()

        composeRule.onNodeWithText("ログアウト").performClick()
        assertThat(signedOut).isEqualTo(1)
    }
}
