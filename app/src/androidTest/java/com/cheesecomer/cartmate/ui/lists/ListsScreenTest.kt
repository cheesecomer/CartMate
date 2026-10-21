package com.cheesecomer.cartmate.ui.lists

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cheesecomer.cartmate.R
import com.cheesecomer.cartmate.ui.theme.CartMateTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ListsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun タイトルが表示される() {
        composeRule.setContent {
            CartMateTheme { ListsScreen() }
        }

        val title = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.lists_title)
        composeRule.onNodeWithText(title).assertIsDisplayed()
    }
}
