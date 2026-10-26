package com.cheesecomer.cartmate.data.firestore

import android.content.Context
import androidx.core.content.edit
import com.cheesecomer.cartmate.domain.family.MergeOrder
import com.cheesecomer.cartmate.domain.model.HouseholdId
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 自分しかいない家族から、ほかの家族への合流（docs/design/security.md「ほかの家族への合流」）。
 * 商品・リスト・項目を、同じ ID のまま合流先に写す（購入の記録はフェーズ3）。写したドキュメントには importedFrom を付け、
 * ルールはコピー元と同じ内容であることを確かめて書き込みを認める。
 *
 * 所属（users/{uid}.householdId）は、写し終えてから最後に切り替える。途中で止まった場合に備えて、
 * どの家族からどの家族へ合流しているかを端末に記録し、次に [resume] したときに続きから写す
 */
@Singleton
class FamilyMerger @Inject constructor(
    @ApplicationContext context: Context,
    private val db: FirebaseFirestore,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    data class Pending(val uid: String, val from: HouseholdId, val to: HouseholdId)

    fun pending(uid: String): Pending? {
        val from = prefs.getString(KEY_FROM, null) ?: return null
        val to = prefs.getString(KEY_TO, null) ?: return null
        return Pending(uid, HouseholdId(from), HouseholdId(to)).takeIf { prefs.getString(KEY_UID, null) == uid }
    }

    /** 合流先に参加したあとで呼ぶ。合流を記録してから写し始める */
    fun start(pending: Pending) {
        prefs.edit(commit = true) {
            putString(KEY_UID, pending.uid)
            putString(KEY_FROM, pending.from.value)
            putString(KEY_TO, pending.to.value)
        }
    }

    /** 写して、所属を切り替え、元の家族を抜ける。もう一度呼んでも、写したものは飛ばして続きから進める */
    suspend fun resume(pending: Pending, onProgress: (done: Int, total: Int) -> Unit) {
        val (uid, from, to) = pending
        val products = Paths.products(db, from).get(Source.SERVER).await().documents
        val lists = visible(Paths.lists(db, from), uid)
        val items = visible(Paths.items(db, from), uid)
        val total = products.size + lists.size + items.size
        var done = 0
        fun advance(count: Int) {
            done += count
            onProgress(done, total)
        }
        onProgress(0, total)

        // 商品は決定的 ID なので、合流先に同じ商品があれば写さない（同じ商品として扱う。docs/requirements.md §4.2）
        copy(products, Paths.products(db, to), from) { _, data -> data }.also(::advance)

        // リストは合流先の末尾に、元の並び順のまま加える。同じ名前のリストがあってもまとめない
        val targetMax = Paths.lists(db, to).orderBy(F.SORT_KEY, Query.Direction.DESCENDING).limit(1)
            .get(Source.SERVER).await().documents.firstOrNull()?.getDouble(F.SORT_KEY)
        val sortKeys = MergeOrder.appended(lists.associate { it.id to (it.getDouble(F.SORT_KEY) ?: 0.0) }, targetMax)
        copy(lists, Paths.lists(db, to), from) { id, data -> data + (F.SORT_KEY to sortKeys.getValue(id)) }.also(::advance)

        copy(items, Paths.items(db, to), from) { _, data -> data }.also(::advance)

        // 所属を切り替えて、元の家族を抜ける（自分しかいないので、オーナーの引き継ぎはない）
        db.batch()
            .set(Paths.user(db, uid), mapOf(F.HOUSEHOLD_ID to to.value, F.UPDATED_AT to FieldValue.serverTimestamp()))
            .delete(Paths.member(db, from, uid))
            .commit().await()
        prefs.edit(commit = true) { clear() }
    }

    /** 自分に見えるドキュメント（共有と自分のプライベート）。コピー元の家族は自分しかいないので、ほぼすべて */
    private suspend fun visible(collection: CollectionReference, uid: String): List<DocumentSnapshot> =
        collection.whereEqualTo(F.VISIBILITY, V.SHARED).get(Source.SERVER).await().documents +
            collection.whereEqualTo(F.VISIBILITY, V.PRIVATE).whereEqualTo(F.CREATED_BY, uid).get(Source.SERVER).await().documents

    /**
     * [documents] を [target] に同じ ID で写し、写した件数を返す。合流先にすでにあるものは飛ばす。
     * ルールの get は1回の書き込みで20件までなので、少しずつ書く
     */
    private suspend fun copy(
        documents: List<DocumentSnapshot>,
        target: CollectionReference,
        from: HouseholdId,
        transform: (id: String, data: Map<String, Any?>) -> Map<String, Any?>,
    ): Int {
        for (chunk in documents.chunked(BATCH_SIZE)) {
            val batch = db.batch()
            var writes = 0
            for (document in chunk) {
                val ref = target.document(document.id)
                if (ref.get(Source.SERVER).await().exists()) continue
                val data = document.data ?: continue
                batch.set(ref, transform(document.id, data) + (F.IMPORTED_FROM to from.value))
                writes++
            }
            if (writes > 0) batch.commit().await()
        }
        return documents.size
    }

    private companion object {
        const val PREFS = "family_merge"
        const val KEY_UID = "uid"
        const val KEY_FROM = "from"
        const val KEY_TO = "to"
        /** 2つの家族のメンバー確認（2件）と、コピー元の読み取り（1件ずつ）が20件に収まるように */
        const val BATCH_SIZE = 15
    }
}
