package com.cheesecomer.cartmate.data.firestore

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant

/**
 * リスト詳細の購読（recentListItems）が、オフラインでチェックした項目を残すか。
 * Firestore の端末の中での条件の判定に依存するので、実機の Firestore で確かめる。
 * アプリのキャッシュに触れないよう、別名の FirebaseApp を使い、ネットワークを切ったまま（サーバーには何も送らない）にする
 */
@RunWith(AndroidJUnit4::class)
class RecentListItemsOfflineTest {

    private lateinit var app: FirebaseApp
    private lateinit var db: FirebaseFirestore

    @Before
    fun setUp(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        app = FirebaseApp.initializeApp(context, FirebaseApp.getInstance().options, "recent-list-items-test")
        db = FirebaseFirestore.getInstance(app)
        db.disableNetwork().await()
        Unit
    }

    @After
    fun tearDown(): Unit = runBlocking {
        db.terminate().await()
        db.clearPersistence().await()
        app.delete()
    }

    @Test
    fun オフラインでチェックした項目は打ち消し線で表示できるよう購読の結果に残る(): Unit = runBlocking {
        val now = Instant.now()
        val items = db.collection("households/test/items")
        val itemRef = items.document("milk")
        // オフラインの書き込みは、端末の中にすぐ反映される（サーバーには送らないので、完了は待たない）
        itemRef.set(mapOf(
            F.LIST_ID to "super",
            F.INPUT_NAME to "牛乳",
            F.SORT_KEY to 1.0,
            F.VISIBILITY to V.SHARED,
            F.STATUS to V.OPEN,
            F.CREATED_BY to "taro",
            F.CREATED_AT to now.toTimestamp(),
            F.COMPLETED_AT to null,
            F.COMPLETED_BY to null,
        ))
        // アプリと同じクエリで購読し、結果をまとめる
        val queries = items.whereEqualTo(F.LIST_ID, "super").whereEqualTo(F.VISIBILITY, V.SHARED)
            .listItemQueries(since = now - Duration.ofHours(24))
        val listItems = combine(queries.map { it.snapshotFlow() }) { snapshots -> snapshots.flatMap { it.documents } }

        withTimeout(5_000) { listItems.first { it.has("milk", V.OPEN) } }

        // アプリと同じチェックの書き込み
        db.batch().markCompleted(itemRef, uid = "taro", checkedAt = now).commit()

        val afterCheck = withTimeout(5_000) { listItems.first { !it.has("milk", V.OPEN) } }
        assertThat(afterCheck.has("milk", V.COMPLETED)).isTrue()
    }

    private fun List<DocumentSnapshot>.has(id: String, status: String): Boolean =
        any { it.id == id && it.getString(F.STATUS) == status }
}
