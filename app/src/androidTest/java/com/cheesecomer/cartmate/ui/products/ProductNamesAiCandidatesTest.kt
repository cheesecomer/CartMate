package com.cheesecomer.cartmate.ui.products

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cheesecomer.cartmate.data.memory.InMemoryStore
import com.cheesecomer.cartmate.domain.model.NewItem
import com.cheesecomer.cartmate.domain.model.NewList
import com.cheesecomer.cartmate.domain.model.UserId
import com.cheesecomer.cartmate.domain.model.Visibility
import com.cheesecomer.cartmate.domain.product.AiCandidates
import com.cheesecomer.cartmate.domain.product.JudgementKey
import com.cheesecomer.cartmate.domain.product.JudgementRequest
import com.cheesecomer.cartmate.domain.product.NameJudgement
import com.cheesecomer.cartmate.domain.product.NormalizationDictionary
import com.cheesecomer.cartmate.domain.repository.AiDismissalRepository
import com.cheesecomer.cartmate.domain.repository.NameJudgementRepository
import com.cheesecomer.cartmate.ui.suggestions.JudgedAiCandidateSource
import com.cheesecomer.cartmate.ui.theme.CartMateTheme
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant

/** 商品名の管理の「AI の候補」（docs/design/suggestions.md「候補にする条件」） */
@RunWith(AndroidJUnit4::class)
class ProductNamesAiCandidatesTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val clock = Clock.systemUTC()
    private val store = InMemoryStore(clock) { UserId("taro") }

    private val judgements = object : NameJudgementRepository {
        val all = MutableStateFlow<List<NameJudgement>>(emptyList())
        override fun observeJudgements(): Flow<List<NameJudgement>> = all
        override suspend fun requestJudgement(request: JudgementRequest, dictionaryVersion: String) = Unit
    }

    private val dismissals = object : AiDismissalRepository {
        val dismissed = MutableStateFlow<Set<AiCandidates.Pair>>(emptySet())
        override fun observeDismissed(): Flow<Set<AiCandidates.Pair>> = dismissed
        override suspend fun dismiss(pair: AiCandidates.Pair) = dismissed.update { it + pair }
    }

    /** 「胡麻ドレ」と「セサミドレ」を共有の項目で購入し、どちらも AI の品目を「ごまドレッシング」と判定した状態で開く */
    private fun setContent() {
        runBlocking {
            val listId = store.createList(NewList("スーパー", memo = null, Visibility.SHARED))
            for (name in listOf("胡麻ドレ", "セサミドレ")) {
                val id = store.addItem(NewItem(listId, name, null, null, memo = null, Visibility.SHARED)).id
                store.completeItem(id, Instant.now())
                Thread.sleep(5)
                val product = store.observeProducts().first().single { it.displayName == name }
                judgements.all.update {
                    it + NameJudgement(
                        key = JudgementKey.of(product.normalizedName, emptyMap()),
                        normalizedName = product.normalizedName,
                        qualifiers = emptyMap(),
                        status = NameJudgement.Status.DONE,
                        judgeVersion = NameJudgement.CURRENT_VERSION,
                        requestedAt = Instant.EPOCH,
                        item = "ごまドレッシング",
                        confidence = 0.9,
                        reason = "「$name」はごまドレッシングの略称",
                    )
                }
            }
        }
        val dictionary = { flowOf(NormalizationDictionary.EMPTY) }
        val catalog = ProductCatalog(store, store, store, clock, dictionary)
        val source = JudgedAiCandidateSource(store, store, store, dictionary, judgements, dismissals, clock)
        val viewModel = ProductNamesViewModel(catalog, source) { flowOf(emptyList()) }
        composeRule.setContent {
            CartMateTheme { ProductNamesScreen(onBack = {}, onOpen = {}, viewModel = viewModel) }
        }
    }

    private fun waitForText(text: String) = composeRule.waitUntil(timeoutMillis = 5_000) {
        runCatching { composeRule.onNodeWithText(text).assertIsDisplayed() }.isSuccess
    }

    @Test
    fun AI_の候補を_理由とともに一覧の上に出す() {
        setContent()
        waitForText("「セサミドレ」を「胡麻ドレ」にまとめる")

        composeRule.onNodeWithText("AI の候補（1件）").assertIsDisplayed()
        composeRule.onNodeWithText("AI：「胡麻ドレ」はごまドレッシングの略称").assertIsDisplayed()
        composeRule.onNodeWithText("AI：「セサミドレ」はごまドレッシングの略称").assertIsDisplayed()
        composeRule.onNodeWithText("登録商品 2件").assertIsDisplayed()
    }

    @Test
    fun まとめるを押すと_候補が消え_落ち着いたあとも一覧で1件にまとまっている() {
        setContent()
        waitForText("「セサミドレ」を「胡麻ドレ」にまとめる")

        composeRule.onNode(hasText("まとめる") and hasClickAction()).performClick()

        waitForText("登録商品 1件")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("AI の候補（1件）").assertDoesNotExist()
        composeRule.onNodeWithText("1件をまとめています").assertIsDisplayed()
        val products = runBlocking { store.observeProducts().first() }.associateBy { it.displayName }
        assertThat(products.getValue("セサミドレ").mergedInto).isEqualTo(products.getValue("胡麻ドレ").id)
    }

    @Test
    fun この候補を出さないを押すと_候補が消え_落ち着いたあとも出ない_まとめない() {
        setContent()
        waitForText("「セサミドレ」を「胡麻ドレ」にまとめる")

        composeRule.onNodeWithText("この候補を出さない").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasText("AI の候補（1件）")).fetchSemanticsNodes().isEmpty()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("「セサミドレ」を「胡麻ドレ」にまとめる").assertDoesNotExist()
        composeRule.onNodeWithText("登録商品 2件").assertIsDisplayed()
        assertThat(runBlocking { store.observeProducts().first() }.all { it.mergedInto == null }).isTrue()
    }
}
