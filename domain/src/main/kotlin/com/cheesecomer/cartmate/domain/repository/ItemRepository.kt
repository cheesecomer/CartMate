package com.cheesecomer.cartmate.domain.repository

import com.cheesecomer.cartmate.domain.model.Item
import com.cheesecomer.cartmate.domain.model.ItemId
import com.cheesecomer.cartmate.domain.model.ListId
import com.cheesecomer.cartmate.domain.model.NewItem
import com.cheesecomer.cartmate.domain.model.UserId
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface ItemRepository {
    /** 家族のすべてのリストの未購入項目 */
    fun observeOpenItems(): Flow<List<Item>>

    /**
     * [listId] の未購入の項目と、[completedSince] 以降にチェックされた項目（並び順）。
     * チェック済みの項目も元の位置に表示するため、両方を1つの一覧として返す
     */
    fun observeListItems(listId: ListId, completedSince: Instant): Flow<List<Item>>

    suspend fun addItem(newItem: NewItem): ItemId

    /** 未購入の項目の商品名・数量・単位を変更する。チェック済みの項目は変更できない */
    suspend fun updateItem(id: ItemId, update: ItemUpdate): UpdateItemResult

    /** 未購入の項目だけを削除できる */
    suspend fun deleteItem(id: ItemId): DeleteItemResult

    /** [id] を [previous] と [next] の間に動かす。null は先頭・末尾を表す */
    suspend fun moveItem(id: ItemId, previous: ItemId?, next: ItemId?)

    /** 削除の取り消し。同じ ID・同じ内容・同じ並び順で作り直す */
    suspend fun restoreItem(item: Item)

    /** チェックと購入の記録を1つの操作として行う（docs/design/sync.md） */
    suspend fun completeItem(id: ItemId, checkedAt: Instant): CompleteResult

    /** チェックの取り消しと購入の無効化を1つの操作として行う */
    suspend fun reopenItem(id: ItemId): ReopenResult
}

sealed interface CompleteResult {
    data object Completed : CompleteResult
    /** 他の端末が先にチェックしていた。何も書き込まない */
    data class AlreadyCompleted(val by: UserId, val at: Instant) : CompleteResult
    /** 通信エラーなど。あとで再試行する */
    data class Retry(val cause: Throwable) : CompleteResult
    /** 再試行しても成功しないもの（項目が削除された、ルールで拒否された など） */
    data class Rejected(val cause: Throwable?) : CompleteResult
}

sealed interface ReopenResult {
    data object Reopened : ReopenResult
    /** 他の端末が先にチェックを外していた */
    data object AlreadyOpen : ReopenResult
    data class Retry(val cause: Throwable) : ReopenResult
    data class Rejected(val cause: Throwable?) : ReopenResult
}

data class ItemUpdate(val inputName: String, val quantity: Double?, val unit: String?)

sealed interface UpdateItemResult {
    data object Updated : UpdateItemResult
    /** すでにチェック済みのため変更しない */
    data object AlreadyCompleted : UpdateItemResult
    data object NotFound : UpdateItemResult
}

sealed interface DeleteItemResult {
    data object Deleted : DeleteItemResult
    /** すでにチェック済みのため削除しない */
    data object AlreadyCompleted : DeleteItemResult
    data object NotFound : DeleteItemResult
}
