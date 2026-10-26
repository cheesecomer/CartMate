package com.cheesecomer.cartmate.data.firestore

import android.util.Log
import com.cheesecomer.cartmate.domain.model.Item
import com.cheesecomer.cartmate.domain.model.ItemId
import com.cheesecomer.cartmate.domain.model.ItemStatus
import com.cheesecomer.cartmate.domain.model.ListId
import com.cheesecomer.cartmate.domain.model.ListStatus
import com.cheesecomer.cartmate.domain.model.NewItem
import com.cheesecomer.cartmate.domain.model.NewList
import com.cheesecomer.cartmate.domain.model.Purchase
import com.cheesecomer.cartmate.domain.model.ShoppingList
import com.cheesecomer.cartmate.domain.model.UserId
import com.cheesecomer.cartmate.domain.order.SortKeys
import com.cheesecomer.cartmate.domain.repository.CompleteResult
import com.cheesecomer.cartmate.domain.repository.DeleteItemResult
import com.cheesecomer.cartmate.domain.repository.DeleteListResult
import com.cheesecomer.cartmate.domain.repository.ItemRepository
import com.cheesecomer.cartmate.domain.repository.ItemUpdate
import com.cheesecomer.cartmate.domain.repository.PurchaseRepository
import com.cheesecomer.cartmate.domain.repository.ReopenResult
import com.cheesecomer.cartmate.domain.repository.ShoppingListRepository
import com.cheesecomer.cartmate.domain.repository.UpdateItemResult
import com.cheesecomer.cartmate.domain.repository.UpdateListResult
import com.cheesecomer.cartmate.domain.text.NameNormalizer
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Filter
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.WriteBatch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * リスト・項目・購入の記録の Firestore 実装（docs/design/data-model.md、docs/design/sync.md）。
 * 公開範囲で分けた2本の購読（共有と自分のプライベート）をまとめる。ルールはフィルタにならないため、
 * 他のメンバーのプライベートなドキュメントを含むクエリは拒否される。
 *
 * オフラインでもできる操作（リストの作成、項目の追加・削除・編集、並べ替え）は、書き込みの完了を待たない。
 * Firestore がオフラインの書き込みを端末に保存し、接続が戻ると送る。チェックと Undo はトランザクションで行うため、
 * オンラインのときだけ成功する（保留チェックはフェーズ3）
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class FirestoreShoppingStore @Inject constructor(
    private val session: FirestoreSession,
    private val db: FirebaseFirestore,
) : ShoppingListRepository, ItemRepository, PurchaseRepository {

    /** 共有と自分のプライベートの2本の購読を1つにまとめる */
    private fun <T> observeVisible(
        shared: (FirestoreSession.Scope) -> Query,
        private: (FirestoreSession.Scope) -> Query,
        map: (DocumentSnapshot) -> T?,
    ): Flow<List<T>> = session.scope.flatMapLatest { scope ->
        if (scope == null) flowOf(emptyList())
        // 家族から外されると読めなくなる（空にする）。表示する画面は CartMateApp が切り替える
        else recoverListen(
            listen = {
                combine(shared(scope).snapshotFlow(), private(scope).snapshotFlow()) { a, b ->
                    (a.documents + b.documents).mapNotNull(map)
                }
            },
            denied = emptyList(),
            awaitPendingWrites = { db.waitForPendingWrites().await() },
            onError = { Log.w(TAG, "listen failed", it) },
        )
    }

    /** 端末のキャッシュにある、自分に見えるドキュメント（共有と自分のプライベート） */
    private suspend fun cachedVisible(shared: Query, private: Query): List<DocumentSnapshot> =
        shared.get(Source.CACHE).await().documents + private.get(Source.CACHE).await().documents

    private fun Query.visibleShared() = whereEqualTo(F.VISIBILITY, V.SHARED)

    private fun Query.visiblePrivate(uid: String, owner: String = F.CREATED_BY) =
        whereEqualTo(F.VISIBILITY, V.PRIVATE).whereEqualTo(owner, uid)

    // ShoppingListRepository

    override fun observeLists(): Flow<List<ShoppingList>> = observeVisible(
        shared = { Paths.lists(db, it.householdId).visibleShared() },
        private = { Paths.lists(db, it.householdId).visiblePrivate(it.uid) },
        map = { it.toShoppingList() },
    ).mapSorted { it.sortKey }

    private suspend fun cachedLists(scope: FirestoreSession.Scope): List<ShoppingList> {
        val lists = Paths.lists(db, scope.householdId)
        return cachedVisible(lists.visibleShared(), lists.visiblePrivate(scope.uid)).mapNotNull { it.toShoppingList() }
    }

    override suspend fun createList(newList: NewList): ListId {
        val scope = session.requireScope()
        val ref = Paths.lists(db, scope.householdId).document()
        ref.set(mapOf(
            F.NAME to newList.name,
            F.MEMO to newList.memo,
            F.VISIBILITY to newList.visibility.toField(),
            // 末尾に追加する
            F.SORT_KEY to SortKeys.after(cachedLists(scope).maxOfOrNull { it.sortKey }),
            F.CREATED_BY to scope.uid,
            F.CREATED_AT to FieldValue.serverTimestamp(),
            F.STATUS to V.ACTIVE,
        )).logFailure("createList")
        return ListId(ref.id)
    }

    override suspend fun updateList(id: ListId, name: String, memo: String?): UpdateListResult {
        val scope = session.requireScope()
        val list = cachedLists(scope).find { it.id == id }
        if (list == null || list.status == ListStatus.DELETED) return UpdateListResult.NotFound
        // 購入の記録の listNameSnapshot は書き換えない
        Paths.lists(db, scope.householdId).document(id.value).update(F.NAME, name, F.MEMO, memo).logFailure("updateList")
        return UpdateListResult.Updated
    }

    override suspend fun moveList(id: ListId, previous: ListId?, next: ListId?) {
        val scope = session.requireScope()
        val lists = cachedLists(scope).associateBy { it.id }
        if (id !in lists) return
        val ordered = lists.values.sortedBy { it.sortKey }.map { it.id }
        val keys = SortKeys.move(ordered, { lists.getValue(it).sortKey }, id, previous, next).keysFor(id)
        db.batch().apply {
            keys.forEach { (listId, key) -> update(Paths.lists(db, scope.householdId).document(listId.value), F.SORT_KEY, key) }
        }.commit().logFailure("moveList")
    }

    /** オンラインのときだけ行える（docs/requirements.md §3.3）。未購入は自分に見える項目でサーバーに問い合わせて数える */
    override suspend fun deleteList(id: ListId): DeleteListResult {
        val scope = session.requireScope()
        val ref = Paths.lists(db, scope.householdId).document(id.value)
        val list = ref.get(Source.SERVER).await().toShoppingList()
        if (list == null || list.status == ListStatus.DELETED) return DeleteListResult.NotFound
        val open = Paths.items(db, scope.householdId).whereEqualTo(F.LIST_ID, id.value).whereEqualTo(F.STATUS, V.OPEN)
        val hasOpen = !open.visibleShared().limit(1).get(Source.SERVER).await().isEmpty ||
            !open.visiblePrivate(scope.uid).limit(1).get(Source.SERVER).await().isEmpty
        if (hasOpen) return DeleteListResult.NotEmpty
        ref.update(F.STATUS, V.DELETED, F.DELETED_AT, FieldValue.serverTimestamp(), F.DELETED_BY, scope.uid).await()
        return DeleteListResult.Deleted
    }

    // ItemRepository

    override fun observeOpenItems(): Flow<List<Item>> = observeVisible(
        shared = { Paths.items(db, it.householdId).whereEqualTo(F.STATUS, V.OPEN).visibleShared() },
        private = { Paths.items(db, it.householdId).whereEqualTo(F.STATUS, V.OPEN).visiblePrivate(it.uid) },
        map = { it.toItem() },
    ).mapSorted { it.createdAt }

    override fun observeListItems(listId: ListId, completedSince: Instant): Flow<List<Item>> {
        // 未購入か、completedSince 以降にチェックされた項目（OR クエリ）
        val recent = Filter.or(
            Filter.equalTo(F.STATUS, V.OPEN),
            Filter.greaterThanOrEqualTo(F.COMPLETED_AT, completedSince.toTimestamp()),
        )
        return observeVisible(
            shared = { Paths.items(db, it.householdId).whereEqualTo(F.LIST_ID, listId.value).visibleShared().where(recent) },
            private = { Paths.items(db, it.householdId).whereEqualTo(F.LIST_ID, listId.value).visiblePrivate(it.uid).where(recent) },
            map = { it.toItem() },
        ).mapSorted { it.sortKey }
    }

    private suspend fun cachedItems(scope: FirestoreSession.Scope, listId: ListId): List<Item> {
        val items = Paths.items(db, scope.householdId).whereEqualTo(F.LIST_ID, listId.value)
        return cachedVisible(items.visibleShared(), items.visiblePrivate(scope.uid)).mapNotNull { it.toItem() }
    }

    private suspend fun cachedItem(scope: FirestoreSession.Scope, id: ItemId): Item? = runCatching {
        Paths.items(db, scope.householdId).document(id.value).get(Source.CACHE).await().toItem()
    }.getOrNull()

    override suspend fun addItem(newItem: NewItem): ItemId {
        val scope = session.requireScope()
        val ref = Paths.items(db, scope.householdId).document()
        val sortKey = SortKeys.after(cachedItems(scope, newItem.listId).maxOfOrNull { it.sortKey })
        db.batch().apply {
            val productId = putProduct(scope, newItem.inputName)
            set(ref, mapOf(
                F.LIST_ID to newItem.listId.value,
                F.PRODUCT_ID to productId,
                F.INPUT_NAME to newItem.inputName,
                // 末尾に追加する
                F.SORT_KEY to sortKey,
                F.QUANTITY to newItem.quantity,
                F.UNIT to newItem.unit,
                F.MEMO to newItem.memo,
                F.VISIBILITY to newItem.visibility.toField(),
                F.STATUS to V.OPEN,
                F.CREATED_BY to scope.uid,
                F.CREATED_AT to FieldValue.serverTimestamp(),
                F.COMPLETED_AT to null,
                F.COMPLETED_BY to null,
            ))
        }.commit().logFailure("addItem")
        return ItemId(ref.id)
    }

    /**
     * 商品（決定的ID）を書き込みに加え、その ID を返す。端末にない商品だけを作る。
     * 統合の扱い（mergedInto）はフェーズ3で加える
     */
    private suspend fun WriteBatch.putProduct(scope: FirestoreSession.Scope, inputName: String): String {
        val productId = NameNormalizer.productId(inputName)
        val ref = Paths.products(db, scope.householdId).document(productId)
        val cached = runCatching { ref.get(Source.CACHE).await().exists() }.getOrDefault(false)
        if (!cached) {
            set(ref, mapOf(
                F.DISPLAY_NAME to NameNormalizer.displayName(inputName),
                F.NORMALIZED_NAME to NameNormalizer.normalizedName(inputName),
                F.SEARCH_KEY to NameNormalizer.searchKey(inputName),
                F.CREATED_BY to scope.uid,
                F.CREATED_AT to FieldValue.serverTimestamp(),
                F.MERGED_INTO to null,
            ))
        }
        return productId
    }

    override suspend fun updateItem(id: ItemId, update: ItemUpdate): UpdateItemResult {
        val scope = session.requireScope()
        val item = cachedItem(scope, id) ?: return UpdateItemResult.NotFound
        if (item.status == ItemStatus.COMPLETED) return UpdateItemResult.AlreadyCompleted
        // productId は変えない（ルールで変えられない。商品名を変えたときの扱いはフェーズ3で決める）。
        // 送る前に他の端末でチェックされていた場合は、ルールで拒否される
        Paths.items(db, scope.householdId).document(id.value).update(
            F.INPUT_NAME, update.inputName,
            F.QUANTITY, update.quantity,
            F.UNIT, update.unit,
            F.MEMO, update.memo,
        ).logFailure("updateItem")
        return UpdateItemResult.Updated
    }

    override suspend fun deleteItem(id: ItemId): DeleteItemResult {
        val scope = session.requireScope()
        val item = cachedItem(scope, id) ?: return DeleteItemResult.NotFound
        if (item.status == ItemStatus.COMPLETED) return DeleteItemResult.AlreadyCompleted
        // 送る前に他の端末でチェックされていた場合は、ルールで拒否される（チェック済みの項目は削除されない）
        Paths.items(db, scope.householdId).document(id.value).delete().logFailure("deleteItem")
        return DeleteItemResult.Deleted
    }

    override suspend fun moveItem(id: ItemId, previous: ItemId?, next: ItemId?) {
        val scope = session.requireScope()
        val listId = cachedItem(scope, id)?.listId ?: return
        val items = cachedItems(scope, listId).associateBy { it.id }
        val ordered = items.values.sortedBy { it.sortKey }.map { it.id }
        val keys = SortKeys.move(ordered, { items.getValue(it).sortKey }, id, previous, next).keysFor(id)
        db.batch().apply {
            keys.forEach { (itemId, key) -> update(Paths.items(db, scope.householdId).document(itemId.value), F.SORT_KEY, key) }
        }.commit().logFailure("moveItem")
    }

    override suspend fun restoreItem(item: Item) {
        val scope = session.requireScope()
        db.batch().apply {
            val productId = putProduct(scope, item.inputName)
            // 同じ ID・同じ並び順で作り直す。ルールは作った人が自分であることを求めるので、
            // ほかのメンバーの項目を戻すと、作った人は自分になる
            set(Paths.items(db, scope.householdId).document(item.id.value), mapOf(
                F.LIST_ID to item.listId.value,
                F.PRODUCT_ID to productId,
                F.INPUT_NAME to item.inputName,
                F.SORT_KEY to item.sortKey,
                F.QUANTITY to item.quantity,
                F.UNIT to item.unit,
                F.MEMO to item.memo,
                F.VISIBILITY to item.visibility.toField(),
                F.STATUS to V.OPEN,
                F.CREATED_BY to scope.uid,
                F.CREATED_AT to FieldValue.serverTimestamp(),
                F.COMPLETED_AT to null,
                F.COMPLETED_BY to null,
            ))
        }.commit().logFailure("restoreItem")
    }

    /** チェックと購入の記録を1つのトランザクションで書く。ルールが、両方がそろっている書き込みだけを認める */
    override suspend fun completeItem(id: ItemId, checkedAt: Instant): CompleteResult {
        val scope = session.requireScope()
        val itemRef = Paths.items(db, scope.householdId).document(id.value)
        val purchaseRef = Paths.purchases(db, scope.householdId).document(id.value)
        return try {
            db.runTransaction { tx ->
                val snapshot = tx.get(itemRef)
                val item = snapshot.toItem() ?: return@runTransaction CompleteResult.Rejected(null)
                if (item.status == ItemStatus.COMPLETED) {
                    return@runTransaction CompleteResult.AlreadyCompleted(item.completedBy!!, item.completedAt!!)
                }
                val listName = tx.get(Paths.lists(db, scope.householdId).document(item.listId.value)).getString(F.NAME).orEmpty()
                tx.update(itemRef, F.STATUS, V.COMPLETED, F.COMPLETED_AT, FieldValue.serverTimestamp(), F.COMPLETED_BY, scope.uid)
                tx.set(purchaseRef, mapOf(
                    F.ITEM_ID to id.value,
                    F.LIST_ID to item.listId.value,
                    F.LIST_NAME_SNAPSHOT to listName,
                    F.PRODUCT_ID to snapshot.getString(F.PRODUCT_ID),
                    F.INPUT_NAME to item.inputName,
                    F.QUANTITY to item.quantity,
                    F.UNIT to item.unit,
                    F.MEMO to item.memo,
                    F.VISIBILITY to item.visibility.toField(),
                    F.CHECKED_AT to checkedAt.toTimestamp(),
                    F.PURCHASED_AT to FieldValue.serverTimestamp(),
                    F.PURCHASED_BY to scope.uid,
                    F.ACTIVE to true,
                    F.REVOKED_AT to null,
                    F.REVOKED_BY to null,
                ))
                CompleteResult.Completed
            }.await()
        } catch (e: Exception) {
            if (e.isRejected) CompleteResult.Rejected(e) else CompleteResult.Retry(e)
        }
    }

    /** チェックの取り消しと購入の無効化を1つのトランザクションで書く */
    override suspend fun reopenItem(id: ItemId): ReopenResult {
        val scope = session.requireScope()
        val itemRef = Paths.items(db, scope.householdId).document(id.value)
        val purchaseRef = Paths.purchases(db, scope.householdId).document(id.value)
        return try {
            db.runTransaction { tx ->
                val item = tx.get(itemRef).toItem() ?: return@runTransaction ReopenResult.Rejected(null)
                if (item.status == ItemStatus.OPEN) return@runTransaction ReopenResult.AlreadyOpen
                tx.update(itemRef, F.STATUS, V.OPEN, F.COMPLETED_AT, null, F.COMPLETED_BY, null)
                tx.update(purchaseRef, F.ACTIVE, false, F.REVOKED_AT, FieldValue.serverTimestamp(), F.REVOKED_BY, scope.uid)
                ReopenResult.Reopened
            }.await()
        } catch (e: Exception) {
            if (e.isRejected) ReopenResult.Rejected(e) else ReopenResult.Retry(e)
        }
    }

    // PurchaseRepository

    override fun observePurchases(since: Instant): Flow<List<Purchase>> = observeVisible(
        shared = { Paths.purchases(db, it.householdId).visibleShared().whereGreaterThanOrEqualTo(F.PURCHASED_AT, since.toTimestamp()) },
        private = {
            Paths.purchases(db, it.householdId).visiblePrivate(it.uid, owner = F.PURCHASED_BY)
                .whereGreaterThanOrEqualTo(F.PURCHASED_AT, since.toTimestamp())
        },
        map = { it.toPurchase() },
    ).mapSorted { it.purchasedAt }

    private fun <T, K : Comparable<K>> Flow<List<T>>.mapSorted(key: (T) -> K): Flow<List<T>> = map { it.sortedBy(key) }

    /** 動かした1件だけを書き込むか、全件を振り直す */
    private fun <K> SortKeys.Move<K>.keysFor(moved: K): Map<K, Double> = when (this) {
        is SortKeys.Move.Single -> mapOf(moved to key)
        is SortKeys.Move.Rebalance -> keys
    }

    /** 再試行しても成功しないもの（ルールで拒否された、ドキュメントがない） */
    private val Exception.isRejected: Boolean
        get() = this is FirebaseFirestoreException &&
            (code == FirebaseFirestoreException.Code.PERMISSION_DENIED || code == FirebaseFirestoreException.Code.NOT_FOUND)

    /** 完了を待たない書き込み。ルールで拒否された場合などはログに残す */
    private fun Task<*>.logFailure(operation: String) {
        addOnFailureListener { Log.w(TAG, "$operation failed", it) }
    }

    private companion object {
        const val TAG = "FirestoreShoppingStore"
    }
}
