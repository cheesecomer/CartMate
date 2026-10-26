package com.cheesecomer.cartmate.data.firestore

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class ListenRecoveryTest {

    private class Denied : RuntimeException("PERMISSION_DENIED")

    /** インデックスの作成中など、時間をおけば成功するエラー */
    private class FailedPrecondition : RuntimeException("FAILED_PRECONDITION: The query requires an index")

    private val isDenied: (Throwable) -> Boolean = { it is Denied }

    /** [failures] 回だけ [error] で失敗し、そのあとは [value] を流す購読 */
    private fun <T> failing(failures: Int, error: () -> Throwable, value: T): Pair<() -> Flow<T>, () -> Int> {
        var calls = 0
        val listen = {
            calls++
            if (calls <= failures) flow<T> { throw error() } else flowOf(value)
        }
        return listen to { calls }
    }

    @Test
    fun `インデックスの作成中のエラーでは落ちずに、購読し直して一覧を流す`() = runTest {
        // リストを開いたときに、Firestore がインデックスの作成中で FAILED_PRECONDITION を返した
        val (listen, calls) = failing(failures = 2, error = ::FailedPrecondition, value = listOf("牛乳"))

        recoverListen(listen, denied = emptyList(), awaitPendingWrites = {}, isDenied = isDenied).test {
            assertThat(awaitItem()).containsExactly("牛乳")
            awaitComplete()
        }
        assertThat(calls()).isEqualTo(3)
    }

    @Test
    fun `拒否以外のエラーで空の一覧を流さない（リストが消えたように見せない）`() = runTest {
        val (listen, _) = failing(failures = 1, error = ::FailedPrecondition, value = listOf("牛乳"))

        recoverListen(listen, denied = emptyList(), awaitPendingWrites = {}, isDenied = isDenied).test {
            assertThat(awaitItem()).isNotEmpty()
            awaitComplete()
        }
    }

    @Test
    fun `拒否されたら書き込みが届くのを待って1回だけ購読し直す`() = runTest {
        val (listen, calls) = failing(failures = 1, error = ::Denied, value = listOf("牛乳"))
        var awaited = false

        recoverListen(listen, denied = emptyList(), awaitPendingWrites = { awaited = true }, isDenied = isDenied).test {
            assertThat(awaitItem()).containsExactly("牛乳")
            awaitComplete()
        }
        assertThat(awaited).isTrue()
        assertThat(calls()).isEqualTo(2)
    }

    @Test
    fun `購読し直しても拒否されれば外されたとして denied を流す`() = runTest {
        val (listen, _) = failing(failures = Int.MAX_VALUE, error = ::Denied, value = listOf("牛乳"))

        recoverListen(listen, denied = emptyList(), awaitPendingWrites = {}, isDenied = isDenied).test {
            assertThat(awaitItem()).isEmpty()
            awaitComplete()
        }
    }
}
