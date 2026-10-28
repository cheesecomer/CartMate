package com.cheesecomer.cartmate.data.firestore

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.cheesecomer.cartmate.domain.model.HouseholdId
import com.cheesecomer.cartmate.domain.model.Product
import com.cheesecomer.cartmate.domain.model.ProductId
import com.cheesecomer.cartmate.domain.product.ProductMerge
import com.cheesecomer.cartmate.domain.repository.ProductRepository
import com.cheesecomer.cartmate.domain.text.NameNormalizer
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 家族の商品（docs/design/data-model.md）と、名寄せの訂正。訂正はオフラインでも行え、書き込みの完了を待たない
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class FirestoreProductRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val session: FirestoreSession,
    private val db: FirebaseFirestore,
) : ProductRepository {

    /** 確定していない統合や解除と、そのあとに期待する統合先（docs/design/sync.md「商品の統合の競合」） */
    private val pending = context.getSharedPreferences(PENDING_PREFS, Context.MODE_PRIVATE)
    private val rejected = Channel<Unit>(Channel.BUFFERED)

    override fun observeProducts(): Flow<List<Product>> = session.scope.flatMapLatest { scope ->
        if (scope == null) flowOf(emptyList())
        else recoverListen(
            listen = { Paths.products(db, scope.householdId).snapshotFlow().map { it.documents.mapNotNull { doc -> doc.toProduct() } } },
            denied = emptyList(),
            awaitPendingWrites = { db.waitForPendingWrites().await() },
            onError = { Log.w(TAG, "listen failed", it) },
        )
    }

    /**
     * 1つのバッチで書くので、すべて反映されるか、何も反映されないかのどちらか。ルールが、古い統合関係を前提にした付け替えや、
     * 統合済みの商品への統合を拒否する。拒否されたら知らせる（Firestore が端末の表示をサーバーの状態に戻す）
     */
    override suspend fun applyMergeChanges(changes: List<ProductMerge.Change>) {
        if (changes.isEmpty()) return
        val scope = session.requireScope()
        val products = Paths.products(db, scope.householdId)
        val batch = db.batch()
        for (change in changes) {
            val ref = products.document(change.id.value)
            if (change is ProductMerge.Change.Create) batch.set(ref, newProduct(change.name, scope.uid, change.alwaysHidden))
            else batch.update(ref, fieldsOf(change, scope.uid))
        }
        // アプリを終了している間に拒否された場合に、次の起動で確かめられるよう、期待する統合先を残しておく
        val operation = UUID.randomUUID().toString()
        pending.edit { putString(operation, encode(scope.householdId, changes)) }
        batch.commit()
            .addOnSuccessListener { pending.edit { remove(operation) } }
            .addOnFailureListener { error ->
                Log.w(TAG, "applyMergeChanges failed", error)
                // ルールで拒否されたときだけ知らせる。それ以外（Firestore の終了など）は、次の起動で確かめる
                if (error.isPermissionDenied) {
                    pending.edit { remove(operation) }
                    rejected.trySend(Unit)
                }
            }
    }

    override fun observeRejectedMergeChanges(): Flow<Unit> = rejected.receiveAsFlow()

    override suspend fun verifyPendingMergeChanges() {
        val operations = pending.all.mapNotNull { (key, value) -> (value as? String)?.let { key to it } }
        if (operations.isEmpty()) return
        // 前回の書き込みのキューを送り終えてから、サーバーの状態と比べる
        db.waitForPendingWrites().await()
        for ((operation, encoded) in operations) {
            val (householdId, expected) = decode(encoded) ?: continue
            val applied = runCatching {
                expected.all { (id, into) ->
                    Paths.products(db, householdId).document(id).get(Source.SERVER).await().getString(F.MERGED_INTO) == into
                }
            }.getOrNull() ?: continue   // 確かめられなければ、次の起動でもう一度
            if (!applied) rejected.trySend(Unit)
            pending.edit { remove(operation) }
        }
    }

    override suspend fun setDictionaryOptOut(id: ProductId, optOut: Boolean) {
        val scope = session.requireScope()
        Paths.products(db, scope.householdId).document(id.value).update(F.DICTIONARY_OPT_OUT, optOut).logFailure("setDictionaryOptOut")
    }

    override suspend fun setSuggestionExcluded(id: ProductId, excluded: Boolean) {
        val scope = session.requireScope()
        // 常に非表示にした人と時刻を書き、戻すときは消す
        val ref = Paths.products(db, scope.householdId).document(id.value)
        val task = if (excluded) {
            ref.update(F.SUGGESTION_EXCLUDED, true, F.SUGGESTION_EXCLUDED_AT, FieldValue.serverTimestamp(), F.SUGGESTION_EXCLUDED_BY, scope.uid)
        } else {
            ref.update(F.SUGGESTION_EXCLUDED, false, F.SUGGESTION_EXCLUDED_AT, FieldValue.delete(), F.SUGGESTION_EXCLUDED_BY, FieldValue.delete())
        }
        task.logFailure("setSuggestionExcluded")
    }

    /** 名前の訂正で作る商品（FirestoreShoppingStore で項目と一緒に作る商品と同じ形） */
    private fun newProduct(name: String, uid: String, alwaysHidden: Boolean): Map<String, Any?> = mapOf(
        F.DISPLAY_NAME to NameNormalizer.displayName(name),
        F.NORMALIZED_NAME to NameNormalizer.normalizedName(name),
        F.SEARCH_KEY to NameNormalizer.searchKey(name),
        F.CREATED_BY to uid,
        F.CREATED_AT to FieldValue.serverTimestamp(),
        F.MERGED_INTO to null,
    ) + if (alwaysHidden) {
        // 訂正する前の商品の「常に非表示」を引き継ぐ
        mapOf(F.SUGGESTION_EXCLUDED to true, F.SUGGESTION_EXCLUDED_AT to FieldValue.serverTimestamp(), F.SUGGESTION_EXCLUDED_BY to uid)
    } else {
        emptyMap()
    }

    /**
     * 統合は最初に統合した人と時刻を書き、付け替えは書き換えない。解除は消す。どれも、最後に変えた人と時刻を書く。
     * 非表示は、非表示にした人と時刻を書き、表示で消す
     */
    private fun fieldsOf(change: ProductMerge.Change, uid: String): Map<String, Any?> {
        val updated = mapOf(F.MERGE_UPDATED_AT to FieldValue.serverTimestamp(), F.MERGE_UPDATED_BY to uid)
        return when (change) {
            is ProductMerge.Change.Hide -> mapOf(F.HIDDEN_AT to FieldValue.serverTimestamp(), F.HIDDEN_BY to uid)
            is ProductMerge.Change.Show -> mapOf(F.HIDDEN_AT to FieldValue.delete(), F.HIDDEN_BY to FieldValue.delete())
            is ProductMerge.Change.Create -> error("Create は set で書く")
            is ProductMerge.Change.Merge -> updated + mapOf(
                F.MERGED_INTO to change.into.value,
                F.MERGED_AT to FieldValue.serverTimestamp(),
                F.MERGED_BY to uid,
            )
            is ProductMerge.Change.Repoint -> updated + (F.MERGED_INTO to change.into.value)
            is ProductMerge.Change.Unmerge -> updated + mapOf(F.MERGED_INTO to null, F.MERGED_AT to null, F.MERGED_BY to null)
        }
    }

    /** 「家族の ID」の行に続けて、「商品の ID=期待する統合先」（解除なら空）を1行ずつ */
    /**
     * 「家族の ID」の行に続けて、「商品の ID=期待する統合先」（解除と作成なら空）を1行ずつ。
     * 非表示と表示は統合先を変えないので、確かめない
     */
    private fun encode(householdId: HouseholdId, changes: List<ProductMerge.Change>): String =
        (listOf(householdId.value) + changes.mapNotNull { change ->
            val into = when (change) {
                is ProductMerge.Change.Merge -> change.into.value
                is ProductMerge.Change.Repoint -> change.into.value
                is ProductMerge.Change.Unmerge, is ProductMerge.Change.Create -> ""
                is ProductMerge.Change.Hide, is ProductMerge.Change.Show -> return@mapNotNull null
            }
            "${change.id.value}=$into"
        }).joinToString("\n")

    private fun decode(encoded: String): Pair<HouseholdId, Map<String, String?>>? {
        val lines = encoded.lines()
        val householdId = lines.firstOrNull()?.takeIf { it.isNotEmpty() } ?: return null
        val expected = lines.drop(1).mapNotNull { line ->
            val (id, into) = line.split("=", limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
            id to into.ifEmpty { null }
        }.toMap()
        return HouseholdId(householdId) to expected
    }

    private fun Task<*>.logFailure(operation: String) {
        addOnFailureListener { Log.w(TAG, "$operation failed", it) }
    }

    private companion object {
        const val TAG = "FirestoreProductRepository"
        const val PENDING_PREFS = "product_merge_pending"
    }
}
