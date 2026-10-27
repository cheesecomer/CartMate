package com.cheesecomer.cartmate.data.firestore

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.cheesecomer.cartmate.domain.model.HouseholdId
import com.cheesecomer.cartmate.domain.model.Product
import com.cheesecomer.cartmate.domain.model.ProductId
import com.cheesecomer.cartmate.domain.product.ProductMerge
import com.cheesecomer.cartmate.domain.repository.ProductRepository
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
            batch.update(products.document(change.id.value), fieldsOf(change, scope.uid))
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

    /** 統合は最初に統合した人と時刻を書き、付け替えは書き換えない。解除は消す。どれも、最後に変えた人と時刻を書く */
    private fun fieldsOf(change: ProductMerge.Change, uid: String): Map<String, Any?> {
        val updated = mapOf(F.MERGE_UPDATED_AT to FieldValue.serverTimestamp(), F.MERGE_UPDATED_BY to uid)
        return when (change) {
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
    private fun encode(householdId: HouseholdId, changes: List<ProductMerge.Change>): String =
        (listOf(householdId.value) + changes.map { change ->
            val into = when (change) {
                is ProductMerge.Change.Merge -> change.into.value
                is ProductMerge.Change.Repoint -> change.into.value
                is ProductMerge.Change.Unmerge -> ""
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
