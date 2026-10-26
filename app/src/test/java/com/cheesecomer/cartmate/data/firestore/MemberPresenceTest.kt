package com.cheesecomer.cartmate.data.firestore

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class MemberPresenceTest {

    private class Denied : RuntimeException("PERMISSION_DENIED")

    private val isDenied: (Throwable) -> Boolean = { it is Denied }

    /** 1回目の購読は拒否され、2回目以降は [afterRetry] を流す購読 */
    private fun deniedOnce(afterRetry: Boolean): Pair<() -> Flow<Boolean>, () -> Int> {
        var calls = 0
        val listen = {
            calls++
            if (calls == 1) flow<Boolean> { throw Denied() } else flowOf(afterRetry)
        }
        return listen to { calls }
    }

    @Test
    fun `家族を作成した直後に拒否されても、自分の書き込みが届いたあとでメンバーと分かれば外されたとしない`() = runTest {
        // 家族を作成した直後は、自分の members の書き込みがサーバーに届く前に購読が始まり、ルールで拒否される
        val (listen, calls) = deniedOnce(afterRetry = true)
        var awaited = false

        memberPresence(listen, awaitPendingWrites = { awaited = true }, isDenied = isDenied).test {
            assertThat(awaitItem()).isTrue()
            awaitComplete()
        }
        assertThat(awaited).isTrue()
        assertThat(calls()).isEqualTo(2)
    }

    @Test
    fun `自分の書き込みが届いたあとも拒否されれば外された`() = runTest {
        var calls = 0
        val listen = {
            calls++
            flow<Boolean> { throw Denied() }
        }

        memberPresence(listen, awaitPendingWrites = {}, isDenied = isDenied).test {
            assertThat(awaitItem()).isFalse()
            awaitComplete()
        }
        assertThat(calls).isEqualTo(2)
    }

    @Test
    fun `拒否以外のエラーでは外されたとせず、購読し直す`() = runTest {
        var calls = 0
        val listen = {
            calls++
            if (calls == 1) flow<Boolean> { throw IllegalStateException("network") } else flowOf(true)
        }

        memberPresence(listen, awaitPendingWrites = {}, isDenied = isDenied).test {
            assertThat(awaitItem()).isTrue()
            awaitComplete()
        }
        assertThat(calls).isEqualTo(2)
    }
}
