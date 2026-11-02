package com.cheesecomer.cartmate.data.firestore

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class CombineListensTest {

    private class Denied : RuntimeException("PERMISSION_DENIED")

    /** インデックスの作成中など、時間をおけば成功するエラー */
    private class FailedPrecondition : RuntimeException("FAILED_PRECONDITION: That index is currently building")

    private val isDenied: (Throwable) -> Boolean = { it is Denied }

    @Test
    fun `1本の購読が失敗し続けても、ほかの購読の結果を出す`() = runTest {
        // リスト詳細で、直近にチェックした項目のクエリだけがインデックスの作成中で失敗し続けた
        val open: () -> Flow<List<String>> = { flowOf(listOf("牛乳", "卵")) }
        val recentlyChecked: () -> Flow<List<String>> = { flow { throw FailedPrecondition() } }

        combineListens(listOf(open, recentlyChecked), awaitPendingWrites = {}, isDenied = isDenied).test {
            assertThat(awaitItem()).containsExactly("牛乳", "卵")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `失敗していた購読が成功したら、その結果も加える`() = runTest {
        var calls = 0
        val open: () -> Flow<List<String>> = { flowOf(listOf("牛乳")) }
        val recentlyChecked: () -> Flow<List<String>> = {
            calls++
            if (calls == 1) flow { throw FailedPrecondition() } else flowOf(listOf("パン"))
        }

        combineListens(listOf(open, recentlyChecked), awaitPendingWrites = {}, isDenied = isDenied).test {
            assertThat(awaitItem()).containsExactly("牛乳")
            assertThat(awaitItem()).containsExactly("牛乳", "パン")
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** フィードバック #011。チェックした項目が、未購入の購読から消えてからチェック済みの購読に現れる */
    @Test
    fun `1つの変更が購読ごとに別々に届いても、途中の項目がない一覧は流さない`() = runTest {
        val open = MutableStateFlow(listOf("牛乳", "卵"))
        val checked = MutableStateFlow(emptyList<String>())

        combineListens(listOf({ open }, { checked }), awaitPendingWrites = {}, isDenied = isDenied).test {
            assertThat(awaitItem()).containsExactly("牛乳", "卵")

            open.value = listOf("卵")
            advanceTimeBy(5)
            checked.value = listOf("牛乳")

            assertThat(awaitItem()).containsExactly("卵", "牛乳")
            expectNoEvents()
        }
    }

    @Test
    fun `拒否され続ければ空にする（家族から外された）`() = runTest {
        val denied: () -> Flow<List<String>> = { flow { throw Denied() } }

        combineListens(listOf(denied, denied), awaitPendingWrites = {}, isDenied = isDenied).test {
            assertThat(awaitItem()).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
