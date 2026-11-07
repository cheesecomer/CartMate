package com.cheesecomer.cartmate.data.firestore

import app.cash.turbine.test
import com.cheesecomer.cartmate.domain.model.HouseholdId
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** 自分の users の読み取りを断られたとき（入れ直したデバッグ版の App Check のトークンが未登録など。フィードバック #020） */
class OwnHouseholdTest {

    private class Denied : RuntimeException("PERMISSION_DENIED")

    private val isDenied: (Throwable) -> Boolean = { it is Denied }
    private val household = HouseholdId("h1")

    /** [deniedCalls] 回目までの購読は拒否され、そのあとは [household] を流し続ける購読 */
    private fun deniedFor(deniedCalls: Int): Pair<() -> Flow<OwnHousehold>, () -> Int> {
        var calls = 0
        val listen = {
            calls++
            if (calls <= deniedCalls) flow<OwnHousehold> { throw Denied() }
            else flow {
                emit(OwnHousehold(household))
                awaitCancellation()
            }
        }
        return listen to { calls }
    }

    @Test
    fun `断られたら家族がないとは流さず、断られたと流す`() = runTest {
        val (listen, _) = deniedFor(deniedCalls = Int.MAX_VALUE)

        ownHousehold(listen, awaitPendingWrites = {}, isDenied = isDenied).test {
            assertThat(awaitItem()).isEqualTo(OwnHousehold(null, denied = true))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `断られたあとも読み直し、読めるようになったら所属を流す`() = runTest {
        // 書き込みを待って1回読み直しても断られる（2回）。トークンを登録すると、そのあとは読める
        val (listen, calls) = deniedFor(deniedCalls = 2)

        ownHousehold(listen, awaitPendingWrites = {}, isDenied = isDenied).test {
            assertThat(awaitItem()).isEqualTo(OwnHousehold(null, denied = true))
            assertThat(awaitItem()).isEqualTo(OwnHousehold(household))
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(calls()).isEqualTo(3)
    }

    @Test
    fun `書き込みが届く前の1回だけの拒否なら、断られたとは流さない`() = runTest {
        val (listen, _) = deniedFor(deniedCalls = 1)

        ownHousehold(listen, awaitPendingWrites = {}, isDenied = isDenied).test {
            assertThat(awaitItem()).isEqualTo(OwnHousehold(household))
            cancelAndIgnoreRemainingEvents()
        }
    }
}
