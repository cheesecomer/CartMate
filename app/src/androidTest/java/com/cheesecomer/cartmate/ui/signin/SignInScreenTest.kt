package com.cheesecomer.cartmate.ui.signin

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cheesecomer.cartmate.testing.RecordingUriHandler
import com.cheesecomer.cartmate.ui.theme.CartMateTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** ログイン画面のプライバシーポリシー（docs/requirements.md §3.5「プライバシーポリシー」） */
@RunWith(AndroidJUnit4::class)
class SignInScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun プライバシーポリシーを押すと_そのページを開く() {
        val uriHandler = RecordingUriHandler()
        composeRule.setContent {
            CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                CartMateTheme { SignInContent(SignInUiState(), onSignIn = {}) }
            }
        }

        composeRule.onNodeWithText("プライバシーポリシー").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertThat(uriHandler.opened).containsExactly("https://cartmate.cheesecomer.com/privacy")
    }
}
