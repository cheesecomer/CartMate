package com.cheesecomer.cartmate.data.memory

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
import com.cheesecomer.cartmate.domain.model.Visibility
import com.cheesecomer.cartmate.domain.order.SortKeys
import com.cheesecomer.cartmate.domain.repository.CompleteResult
import com.cheesecomer.cartmate.domain.repository.CurrentUserProvider
import com.cheesecomer.cartmate.domain.repository.DeleteItemResult
import com.cheesecomer.cartmate.domain.repository.DeleteListResult
import com.cheesecomer.cartmate.domain.repository.ItemRepository
import com.cheesecomer.cartmate.domain.repository.ItemUpdate
import com.cheesecomer.cartmate.domain.repository.PurchaseRepository
import com.cheesecomer.cartmate.domain.repository.ReopenResult
import com.cheesecomer.cartmate.domain.repository.ShoppingListRepository
import com.cheesecomer.cartmate.domain.repository.UpdateItemResult
import com.cheesecomer.cartmate.domain.repository.UpdateListResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import com.cheesecomer.cartmate.domain.list.ShoppingRound
import com.cheesecomer.cartmate.domain.repository.RejectedCheck
import kotlinx.coroutines.flow.emptyFlow
import com.cheesecomer.cartmate.domain.model.ProductId
import com.cheesecomer.cartmate.domain.product.ProductMerge
import com.cheesecomer.cartmate.domain.text.NameNormalizer
import com.cheesecomer.cartmate.domain.model.Product
import com.cheesecomer.cartmate.domain.repository.ProductRepository

/**
 * テスト用のインメモリ実装。プロセスが終了するとデータは消える。
 * チェックと Undo の意味論は Firestore 実装と同じにする（docs/design/sync.md）。
 * 他のメンバーのプライベートなリスト・項目・購入は、Firestore の購読と同じく読み取りの結果に含めない。
 */
@Singleton
class InMemoryStore @Inject constructor(
    private val clock: Clock,
    private val currentUser: CurrentUserProvider,
) : ShoppingListRepository, ItemRepository, PurchaseRepository, ProductRepository {

    private data class Data(
        val lists: Map<ListId, ShoppingList> = emptyMap(),
        val items: Map<ItemId, Item> = emptyMap(),
        val purchases: Map<ItemId, Purchase> = emptyMap(),
        val products: Map<ProductId, Product> = emptyMap(),
    )

    private val data = MutableStateFlow(Data())

    /** 状態を読んで書き換え、結果を返す。Firestore のトランザクションに相当する */
    private inline fun <R> transact(block: (Data) -> Pair<Data, R>): R {
        while (true) {
            val current = data.value
            val (next, result) = block(current)
            if (data.compareAndSet(current, next)) return result
        }
    }

    /** 公開範囲の判定。[owner] はリスト・項目なら作った人、購入ならチェックした人 */
    private fun visible(visibility: Visibility, owner: UserId): Boolean =
        visibility == Visibility.SHARED || owner == currentUser.currentUserId()

    private val ShoppingList.visible get() = visible(visibility, createdBy)
    private val Item.visible get() = visible(visibility, createdBy)
    private val Purchase.visible get() = visible(visibility, purchasedBy)

    // ShoppingListRepository

    override fun observeLists(): Flow<List<ShoppingList>> =
        data.map { d -> d.lists.values.filter { it.visible }.sortedBy(ShoppingList::sortKey) }.distinctUntilChanged()

    override suspend fun createList(newList: NewList): ListId {
        val id = ListId(newId())
        val now = clock.instant()
        val me = currentUser.currentUserId()
        data.update { d ->
            val list = ShoppingList(
                id = id,
                name = newList.name,
                memo = newList.memo,
                visibility = newList.visibility,
                // 末尾に追加する
                sortKey = SortKeys.after(d.lists.values.maxOfOrNull(ShoppingList::sortKey)),
                createdAt = now,
                createdBy = me,
                status = ListStatus.ACTIVE,
            )
            d.copy(lists = d.lists + (id to list))
        }
        return id
    }

    override suspend fun moveList(id: ListId, previous: ListId?, next: ListId?) {
        data.update { d ->
            if (id !in d.lists) return@update d
            val ordered = d.lists.values.sortedBy(ShoppingList::sortKey).map { it.id }
            val keys = when (val move = SortKeys.move(ordered, { d.lists.getValue(it).sortKey }, id, previous, next)) {
                is SortKeys.Move.Single -> mapOf(id to move.key)
                is SortKeys.Move.Rebalance -> move.keys
            }
            d.copy(lists = d.lists + keys.mapValues { (listId, key) -> d.lists.getValue(listId).copy(sortKey = key) })
        }
    }

    override suspend fun updateList(id: ListId, name: String, memo: String?): UpdateListResult = transact { d ->
        val list = d.lists[id]
        if (list == null || list.status == ListStatus.DELETED || !list.visible) {
            d to UpdateListResult.NotFound
        } else {
            d.copy(lists = d.lists + (id to list.copy(name = name, memo = memo))) to UpdateListResult.Updated
        }
    }

    override suspend fun deleteList(id: ListId): DeleteListResult = transact { d ->
        val list = d.lists[id]
        when {
            list == null || list.status == ListStatus.DELETED || !list.visible -> d to DeleteListResult.NotFound
            // 未購入の件数は自分に見える項目で数える。他のメンバーのプライベートな項目が残っていても削除できる
            d.items.values.any { it.listId == id && it.status == ItemStatus.OPEN && it.visible } ->
                d to DeleteListResult.NotEmpty
            else -> d.copy(lists = d.lists + (id to list.copy(status = ListStatus.DELETED))) to DeleteListResult.Deleted
        }
    }

    // ItemRepository

    override fun observeOpenItems(): Flow<List<Item>> =
        data.map { d -> d.items.values.filter { it.status == ItemStatus.OPEN && it.visible }.sortedBy(Item::createdAt) }
            .distinctUntilChanged()

    override fun observeListItems(listId: ListId, completedSince: Instant): Flow<List<Item>> =
        data.map { d ->
            d.items.values
                .filter { it.listId == listId && it.visible }
                .filter { it.status == ItemStatus.OPEN || it.completedAt!! >= completedSince }
                .sortedBy(Item::sortKey)
        }.distinctUntilChanged()

    override suspend fun addItem(newItem: NewItem): ItemId {
        val id = ItemId(newId())
        val now = clock.instant()
        val me = currentUser.currentUserId()
        data.update { d ->
            val item = Item(
                id = id,
                listId = newItem.listId,
                inputName = newItem.inputName,
                // 末尾に追加する
                sortKey = SortKeys.after(d.items.values.filter { it.listId == newItem.listId }.maxOfOrNull(Item::sortKey)),
                quantity = newItem.quantity,
                unit = newItem.unit,
                memo = newItem.memo,
                visibility = newItem.visibility,
                status = ItemStatus.OPEN,
                createdAt = now,
                createdBy = me,
                completedAt = null,
                completedBy = null,
                productId = ProductId(NameNormalizer.productId(newItem.inputName)),
            )
            // 買い終わったリストに追加したら、前回の買い物のチェック済みを片付ける
            val list = d.lists[newItem.listId]
            val shown = ShoppingRound.visible(
                d.items.values.filter { it.listId == newItem.listId && it.visible },
                windowStart = now - ShoppingRound.COMPLETED_WINDOW,
                clearedAt = list?.clearedAt,
            )
            val lists = if (list != null && ShoppingRound.clearsOnAdd(shown)) d.lists + (list.id to list.copy(clearedAt = now)) else d.lists
            // 商品（決定的ID）。新しい商品なら作り、表記ゆれのチップを選んでいれば最初から統合する
            val productId = item.productId!!
            val products = if (productId in d.products) d.products else d.products + (productId to Product(
                id = productId,
                displayName = NameNormalizer.displayName(newItem.inputName),
                normalizedName = NameNormalizer.normalizedName(newItem.inputName),
                searchKey = NameNormalizer.searchKey(newItem.inputName),
                createdAt = now,
                mergedInto = newItem.mergeInto,
            ))
            d.copy(items = d.items + (id to item), lists = lists, products = products)
        }
        return id
    }

    override suspend fun moveItem(id: ItemId, previous: ItemId?, next: ItemId?) {
        data.update { d ->
            val listId = d.items[id]?.listId ?: return@update d
            val ordered = d.items.values.filter { it.listId == listId }.sortedBy(Item::sortKey).map { it.id }
            val keys = when (val move = SortKeys.move(ordered, { d.items.getValue(it).sortKey }, id, previous, next)) {
                is SortKeys.Move.Single -> mapOf(id to move.key)
                is SortKeys.Move.Rebalance -> move.keys
            }
            d.copy(items = d.items + keys.mapValues { (itemId, key) -> d.items.getValue(itemId).copy(sortKey = key) })
        }
    }

    override suspend fun updateItem(id: ItemId, update: ItemUpdate): UpdateItemResult = transact { d ->
        val item = d.items[id]
        when {
            item == null -> d to UpdateItemResult.NotFound
            item.status == ItemStatus.COMPLETED -> d to UpdateItemResult.AlreadyCompleted
            else -> {
                val updated = item.copy(
                    inputName = update.inputName,
                    quantity = update.quantity,
                    unit = update.unit,
                    memo = update.memo,
                )
                d.copy(items = d.items + (id to updated)) to UpdateItemResult.Updated
            }
        }
    }

    override suspend fun deleteItem(id: ItemId): DeleteItemResult = transact { d ->
        val item = d.items[id]
        when {
            item == null -> d to DeleteItemResult.NotFound
            item.status == ItemStatus.COMPLETED -> d to DeleteItemResult.AlreadyCompleted
            else -> d.copy(items = d.items - id) to DeleteItemResult.Deleted
        }
    }

    override suspend fun restoreItem(item: Item) {
        // 並び順の値はそのまま残し、削除する前の位置に戻す
        val restored = item.copy(
            status = ItemStatus.OPEN,
            completedAt = null,
            completedBy = null,
        )
        data.update { d -> if (item.id in d.items) d else d.copy(items = d.items + (item.id to restored)) }
    }

    override suspend fun completeItem(id: ItemId, checkedAt: Instant): CompleteResult {
        val now = clock.instant()
        val me = currentUser.currentUserId()
        return transact { d ->
            val item = d.items[id] ?: return@transact d to CompleteResult.Rejected(null)
            if (item.status == ItemStatus.COMPLETED) {
                return@transact d to CompleteResult.AlreadyCompleted(item.completedBy!!, item.completedAt!!)
            }
            val completed = item.copy(status = ItemStatus.COMPLETED, completedAt = now, completedBy = me)
            val purchase = Purchase(
                itemId = item.id,
                listId = item.listId,
                listNameSnapshot = d.lists[item.listId]?.name.orEmpty(),
                inputName = item.inputName,
                quantity = item.quantity,
                unit = item.unit,
                memo = item.memo,
                visibility = item.visibility,
                checkedAt = checkedAt,
                purchasedAt = now,
                purchasedBy = me,
                active = true,
                revokedAt = null,
                revokedBy = null,
                productId = item.productId,
            )
            d.copy(items = d.items + (id to completed), purchases = d.purchases + (id to purchase)) to
                CompleteResult.Completed
        }
    }

    override suspend fun reopenItem(id: ItemId): ReopenResult {
        val now = clock.instant()
        val me = currentUser.currentUserId()
        return transact { d ->
            val item = d.items[id] ?: return@transact d to ReopenResult.Rejected(null)
            if (item.status == ItemStatus.OPEN) return@transact d to ReopenResult.AlreadyOpen
            val reopened = item.copy(status = ItemStatus.OPEN, completedAt = null, completedBy = null)
            val purchases = d.purchases[id]
                ?.let { d.purchases + (id to it.copy(active = false, revokedAt = now, revokedBy = me)) }
                ?: d.purchases
            d.copy(items = d.items + (id to reopened), purchases = purchases) to ReopenResult.Reopened
        }
    }

    // ProductRepository

    override fun observeProducts(): Flow<List<Product>> = data.map { it.products.values.toList() }.distinctUntilChanged()

    override suspend fun applyMergeChanges(changes: List<ProductMerge.Change>) {
        for (change in changes) {
            when (change) {
                is ProductMerge.Change.Merge -> updateProduct(change.id) { it.copy(mergedInto = change.into) }
                is ProductMerge.Change.Repoint -> updateProduct(change.id) { it.copy(mergedInto = change.into) }
                is ProductMerge.Change.Unmerge -> updateProduct(change.id) { it.copy(mergedInto = null) }
                is ProductMerge.Change.Hide -> updateProduct(change.id) { it.copy(hiddenAt = clock.instant(), hiddenBy = currentUser.currentUserId()) }
                is ProductMerge.Change.Show -> updateProduct(change.id) { it.copy(hiddenAt = null, hiddenBy = null) }
                is ProductMerge.Change.Create -> data.update { d ->
                    if (change.id in d.products) d
                    else d.copy(products = d.products + (change.id to Product(
                        id = change.id,
                        displayName = NameNormalizer.displayName(change.name),
                        normalizedName = NameNormalizer.normalizedName(change.name),
                        searchKey = NameNormalizer.searchKey(change.name),
                        createdAt = clock.instant(),
                        suggestionExcluded = change.alwaysHidden,
                        suggestionExcludedBy = currentUser.currentUserId().takeIf { change.alwaysHidden },
                    )))
                }
            }
        }
    }

    /** インメモリでは書き込みが拒否されることはない */
    override fun observeRejectedMergeChanges(): Flow<Unit> = emptyFlow()

    override suspend fun verifyPendingMergeChanges() = Unit

    override suspend fun setDictionaryOptOut(id: ProductId, optOut: Boolean) = updateProduct(id) { it.copy(dictionaryOptOut = optOut) }

    private fun updateProduct(id: ProductId, change: (Product) -> Product) {
        data.update { d -> d.products[id]?.let { d.copy(products = d.products + (id to change(it))) } ?: d }
    }

    /** インメモリでは書き込みが拒否されることはない */
    override fun observeRejectedChecks(): Flow<RejectedCheck> = emptyFlow()

    // PurchaseRepository

    override fun observePurchases(since: Instant): Flow<List<Purchase>> =
        data.map { d ->
            d.purchases.values.filter { it.purchasedAt >= since && it.visible }.sortedBy(Purchase::purchasedAt)
        }
            .distinctUntilChanged()

    override suspend fun setExcludedFromPrediction(id: ItemId, excluded: Boolean) {
        val me = currentUser.currentUserId()
        val now = clock.instant()
        data.update { d ->
            val purchase = d.purchases[id]?.takeIf { it.visible } ?: return@update d
            d.copy(purchases = d.purchases + (id to purchase.copy(
                excludedFromPrediction = excluded,
                excludedAt = now.takeIf { excluded },
                excludedBy = me.takeIf { excluded },
            )))
        }
    }

    override suspend fun setSuggestionExcluded(id: ProductId, excluded: Boolean) = updateProduct(id) {
        it.copy(suggestionExcluded = excluded, suggestionExcludedBy = currentUser.currentUserId().takeIf { excluded })
    }

    private fun newId(): String = UUID.randomUUID().toString()
}
