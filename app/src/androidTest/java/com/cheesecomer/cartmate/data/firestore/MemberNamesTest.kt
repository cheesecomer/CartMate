package com.cheesecomer.cartmate.data.firestore

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cheesecomer.cartmate.domain.model.HouseholdId
import com.cheesecomer.cartmate.domain.model.UserId
import com.cheesecomer.cartmate.domain.repository.JoinResult
import com.cheesecomer.cartmate.testing.EmulatorTest
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 表示名と呼び名（docs/requirements.md §3.5「表示名と呼び名」） */
@EmulatorTest
@RunWith(AndroidJUnit4::class)
class MemberNamesTest {

    private val clients = mutableListOf<EmulatorClient>()

    @Before
    fun onlyWhenRequested() {
        EmulatorClient.assumeRequested()
    }

    @After
    fun tearDown(): Unit = runBlocking {
        clients.forEach { runCatching { it.close() } }
    }

    /** taro の家族に hanako が参加している */
    private suspend fun family(): Triple<EmulatorClient, EmulatorClient, HouseholdId> {
        val taro = EmulatorClient.signIn("taro").also(clients::add)
        val hanako = EmulatorClient.signIn("hanako").also(clients::add)
        val householdId = taro.households.createHousehold(taro.account)
        taro.awaitHousehold(householdId)
        val invite = taro.households.createInvite("太郎")
        assertThat(hanako.households.join(invite, hanako.account)).isEqualTo(JoinResult.Joined)
        hanako.awaitHousehold(householdId)
        return Triple(taro, hanako, householdId)
    }

    private suspend fun EmulatorClient.nameOf(userId: String, expected: String?): String? =
        households.observeNames().awaitFirst { it.of(UserId(userId)) == expected }.of(UserId(userId))

    @Test
    fun 表示名は家族全員に届き呼び名は付けた本人の画面にだけ出る(): Unit = runBlocking {
        val (taro, hanako, _) = family()

        hanako.households.setNickname("花子")
        hanako.db.waitForPendingWrites().await()
        assertThat(taro.nameOf(hanako.uid, "花子")).isEqualTo("花子")

        taro.households.setAlias(UserId(hanako.uid), "ママ")
        taro.db.waitForPendingWrites().await()
        assertThat(taro.nameOf(hanako.uid, "ママ")).isEqualTo("ママ")
        // hanako 自身の画面には、taro が付けた呼び名は出ない
        assertThat(hanako.nameOf(hanako.uid, "花子")).isEqualTo("花子")
        assertThat(hanako.households.observeAliases().first()).isEmpty()

        // 呼び名を解除すると表示名に、表示名も解除すると Google アカウントの名前に戻る
        taro.households.setAlias(UserId(hanako.uid), null)
        assertThat(taro.nameOf(hanako.uid, "花子")).isEqualTo("花子")
        hanako.households.setNickname(null)
        hanako.db.waitForPendingWrites().await()
        assertThat(taro.nameOf(hanako.uid, "hanako")).isEqualTo("hanako")
    }

    @Test
    fun ほかの家族に合流しても表示名と呼び名は残る(): Unit = runBlocking {
        val (taro, hanako, _) = family()
        // jiro は自分しかいない家族を作り、表示名と、taro への呼び名を設定してから、taro の家族に合流する
        val jiro = EmulatorClient.signIn("jiro").also(clients::add)
        val from = jiro.households.createHousehold(jiro.account)
        jiro.awaitHousehold(from)
        jiro.households.setNickname("じろう")
        jiro.households.setAlias(UserId(taro.uid), "兄さん")
        jiro.db.waitForPendingWrites().await()

        val invite = taro.households.createInvite("太郎")
        assertThat(jiro.households.merge(invite, jiro.account, from) { _, _ -> }).isEqualTo(JoinResult.Joined)

        assertThat(hanako.nameOf(jiro.uid, "じろう")).isEqualTo("じろう")
        assertThat(jiro.nameOf(taro.uid, "兄さん")).isEqualTo("兄さん")
    }

    private suspend fun <T> Flow<T>.awaitFirst(predicate: (T) -> Boolean): T = withTimeout(15_000) { first(predicate) }
}
