package com.cheesecomer.cartmate.data.firestore

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
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

    @Test
    fun `拒否され続ければ空にする（家族から外された）`() = runTest {
        val denied: () -> Flow<List<String>> = { flow { throw Denied() } }

        combineListens(listOf(denied, denied), awaitPendingWrites = {}, isDenied = isDenied).test {
            assertThat(awaitItem()).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
