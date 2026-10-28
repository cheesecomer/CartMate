package com.cheesecomer.cartmate.ui.family

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cheesecomer.cartmate.domain.model.Account
import com.cheesecomer.cartmate.domain.model.Member
import com.cheesecomer.cartmate.domain.model.MemberRole
import com.cheesecomer.cartmate.domain.model.UserId
import com.cheesecomer.cartmate.testing.RecordingUriHandler
import com.cheesecomer.cartmate.ui.theme.CartMateTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/** 家族画面の表示名の変更とプライバシーポリシー（docs/requirements.md §3.5「表示名と呼び名」「プライバシーポリシー」） */
@RunWith(AndroidJUnit4::class)
class FamilyScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val hanako = UserId("hanako")
    private val account = Account(hanako, "山田 花子", "hanako@example.com", photoUrl = null)
    private val uriHandler = RecordingUriHandler()

    private fun setContent(nickname: String?) {
        val me = Member(hanako, MemberRole.OWNER, "山田 花子", photoUrl = null, Instant.EPOCH, nickname = nickname)
        val taro = Member(UserId("taro"), MemberRole.MEMBER, "山田 太郎", photoUrl = null, Instant.EPOCH)
        composeRule.setContent {
            CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                CartMateTheme {
                    FamilyContent(
                        state = FamilyUiState(account = account, members = listOf(me, taro)),
                        snackbar = SnackbarHostState(),
                        onBack = {},
                        onInvite = {},
                        onRevoke = {},
                        onRemove = {},
                        onJoinWithCode = {},
                        onOpenProductMerge = {},
                        onLeave = {},
                        onSignOut = {},
                        onDeleteAccount = {},
                    )
                }
            }
        }
    }

    /** ダイアログの入力欄に [text] が入っている */
    private fun assertNameField(text: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNode(hasSetTextAction()).assertIsDisplayed() }.isSuccess
        }
        composeRule.waitForIdle()
        composeRule.onNode(hasSetTextAction() and hasText(text)).assertIsDisplayed()
    }

    @Test
    fun 表示名を設定していなければ_変更のダイアログの初期値はGoogleアカウントの名前() {
        setContent(nickname = null)

        composeRule.onNodeWithText("表示名を変更").performClick()

        assertNameField("山田 花子")
        // 表示名を設定していないので、解除はない
        composeRule.onNodeWithText("解除").assertDoesNotExist()
    }

    @Test
    fun 表示名を設定していれば_変更のダイアログの初期値はその表示名() {
        setContent(nickname = "花子")

        composeRule.onNodeWithText("表示名を変更").performClick()

        assertNameField("花子")
        composeRule.onNodeWithText("解除").assertIsDisplayed()
    }

    @Test
    fun プライバシーポリシーを押すと_そのページを開く() {
        setContent(nickname = null)

        composeRule.onNodeWithText("プライバシーポリシー").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertThat(uriHandler.opened).containsExactly("https://cartmate.cheesecomer.com/privacy")
    }
}
