package com.cheesecomer.cartmate.data.firestore

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cheesecomer.cartmate.domain.model.HouseholdId
import com.cheesecomer.cartmate.domain.model.NewItem
import com.cheesecomer.cartmate.domain.model.NewList
import com.cheesecomer.cartmate.domain.model.Visibility
import com.cheesecomer.cartmate.domain.repository.JoinResult
import com.cheesecomer.cartmate.testing.EmulatorTest
import com.google.common.truth.Truth.assertThat
import com.google.firebase.firestore.Source
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/** 自分しかいない家族から、ほかの家族への合流（docs/design/security.md「ほかの家族への合流」） */
@EmulatorTest
@RunWith(AndroidJUnit4::class)
class FamilyMergeTest {

    private val clients = mutableListOf<EmulatorClient>()

    @Before
    fun onlyWhenRequested() {
        EmulatorClient.assumeRequested()
    }

    @After
    fun tearDown(): Unit = runBlocking {
        clients.forEach { runCatching { it.close() } }
    }

    private lateinit var taro: EmulatorClient
    private lateinit var hanako: EmulatorClient
    private var to = HouseholdId("")
    private var from = HouseholdId("")

    /** taro の家族（合流先）と、hanako が自分しかいない家族（合流元） */
    private suspend fun families() {
        taro = EmulatorClient.signIn("taro").also(clients::add)
        hanako = EmulatorClient.signIn("hanako").also(clients::add)
        to = taro.households.createHousehold(taro.account)
        from = hanako.households.createHousehold(hanako.account)
        taro.awaitHousehold(to)
        hanako.awaitHousehold(from)
    }

    @Test
    fun 合流するとリストと項目が合流先のリストの末尾に元の並び順で移る(): Unit = runBlocking {
        families()
        val taroList = taro.store.createList(NewList("ドラッグストア", memo = null, Visibility.SHARED))
        // ほかのメンバーのプライベートなリストがあっても合流できる
        taro.store.createList(NewList("自分用", memo = null, Visibility.PRIVATE))
        taro.db.waitForPendingWrites().await()
        val first = hanako.store.createList(NewList("スーパー", memo = null, Visibility.SHARED))
        val second = hanako.store.createList(NewList("ひみつ", memo = null, Visibility.PRIVATE))
        val item = hanako.store.addItem(NewItem(first, "牛乳", 1.0, "本", memo = null, Visibility.SHARED))
        hanako.db.waitForPendingWrites().await()

        val invite = taro.households.createInvite("太郎")
        assertThat(hanako.households.merge(invite, hanako.account, from) { _, _ -> }).isEqualTo(JoinResult.Joined)

        hanako.awaitHousehold(to)
        // 合流先で自分に見えるリスト（taro のプライベートなリストは見えない）の後ろに、元の並び順のまま加わる
        val lists = withTimeout(10_000) { hanako.store.observeLists().first { it.size == 3 } }
        assertThat(lists.map { it.id }).containsExactly(taroList, first, second).inOrder()
        val items = withTimeout(10_000) { taro.store.observeOpenItems().first { it.isNotEmpty() } }
        assertThat(items.map { it.id }).containsExactly(item)
    }

    @Test
    fun 合流すると取り消したものとプライベートなものを含めて購入の記録が同じ内容で写る(): Unit = runBlocking {
        families()

        // hanako は自分しかいない家族で買い物をしていた
        val listId = hanako.store.createList(NewList("スーパー", memo = null, Visibility.SHARED))
        fun item(name: String, visibility: Visibility) = NewItem(listId, name, 1.0, "個", memo = null, visibility)
        val shared = hanako.store.addItem(item("牛乳", Visibility.SHARED))
        val private = hanako.store.addItem(item("目薬", Visibility.PRIVATE))
        val revoked = hanako.store.addItem(item("卵", Visibility.SHARED))
        listOf(shared, private, revoked).forEach { hanako.store.completeItem(it, Instant.now()) }
        hanako.store.reopenItem(revoked)
        hanako.db.waitForPendingWrites().await()
        // 合流すると元の家族を抜けて読めなくなるので、先に読んでおく
        val sources = listOf(shared, private, revoked).associateWith {
            Paths.purchases(hanako.db, from).document(it.value).get(Source.SERVER).await().data!!
        }

        val invite = taro.households.createInvite("太郎")
        var progress = 0 to 0
        assertThat(hanako.households.merge(invite, hanako.account, from) { done, total -> progress = done to total })
            .isEqualTo(JoinResult.Joined)
        // 商品3件・リスト1件・項目3件・購入の記録3件
        assertThat(progress).isEqualTo(10 to 10)

        for ((id, source) in sources) {
            val copied = Paths.purchases(hanako.db, to).document(id.value).get(Source.SERVER).await()
            assertThat(copied.data).isEqualTo(source + (F.IMPORTED_FROM to from.value))
        }
        // 合流先のほかのメンバーには、共有の購入の記録だけが見える（取り消したものは active=false のまま）
        val visibleToTaro = Paths.purchases(taro.db, to).whereEqualTo(F.VISIBILITY, V.SHARED).get(Source.SERVER).await()
            .documents.associate { it.id to it.getBoolean(F.ACTIVE) }
        assertThat(visibleToTaro).containsExactly(shared.value, true, revoked.value, false)
    }
}
