package com.cheesecomer.cartmate.data.firestore

import android.util.Log
import com.cheesecomer.cartmate.domain.model.Product
import com.cheesecomer.cartmate.domain.model.ProductId
import com.cheesecomer.cartmate.domain.repository.ProductRepository
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 家族の商品（docs/design/data-model.md）と、名寄せの訂正。訂正はオフラインでも行え、書き込みの完了を待たない
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class FirestoreProductRepository @Inject constructor(
    private val session: FirestoreSession,
    private val db: FirebaseFirestore,
) : ProductRepository {

    override fun observeProducts(): Flow<List<Product>> = session.scope.flatMapLatest { scope ->
        if (scope == null) flowOf(emptyList())
        else recoverListen(
            listen = { Paths.products(db, scope.householdId).snapshotFlow().map { it.documents.mapNotNull { doc -> doc.toProduct() } } },
            denied = emptyList(),
            awaitPendingWrites = { db.waitForPendingWrites().await() },
            onError = { Log.w(TAG, "listen failed", it) },
        )
    }

    override suspend fun merge(from: ProductId, into: ProductId) {
        val scope = session.requireScope()
        Paths.products(db, scope.householdId).document(from.value).update(
            F.MERGED_INTO, into.value,
            F.MERGED_AT, FieldValue.serverTimestamp(),
            F.MERGED_BY, scope.uid,
        ).logFailure("merge")
    }

    override suspend fun unmerge(id: ProductId) {
        val scope = session.requireScope()
        Paths.products(db, scope.householdId).document(id.value).update(F.MERGED_INTO, null).logFailure("unmerge")
    }

    override suspend fun setDictionaryOptOut(id: ProductId, optOut: Boolean) {
        val scope = session.requireScope()
        Paths.products(db, scope.householdId).document(id.value).update(F.DICTIONARY_OPT_OUT, optOut).logFailure("setDictionaryOptOut")
    }

    private fun Task<*>.logFailure(operation: String) {
        addOnFailureListener { Log.w(TAG, "$operation failed", it) }
    }

    private companion object {
        const val TAG = "FirestoreProductRepository"
    }
}
