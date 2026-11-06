package com.cheesecomer.cartmate.domain.repository

import com.cheesecomer.cartmate.domain.model.ListId
import com.cheesecomer.cartmate.domain.model.NewList
import com.cheesecomer.cartmate.domain.model.ShoppingList
import kotlinx.coroutines.flow.Flow

interface ShoppingListRepository {
    /**
     * 論理削除したリストも含めて、並び順で返す。表示するかどうかは呼び出し側で判定する。
     * 他のメンバーのプライベートなリストは含まない
     */
    fun observeLists(): Flow<List<ShoppingList>>

    suspend fun createList(newList: NewList): ListId

    /** 削除していないリストの名前と備考を変更する。購入の記録に残したリスト名は書き換えない。公開範囲は変えられない */
    suspend fun updateList(id: ListId, name: String, memo: String?): UpdateListResult

    /** [id] を [previous] と [next] の間に動かす。null は先頭・末尾を表す */
    suspend fun moveList(id: ListId, previous: ListId?, next: ListId?)

    suspend fun deleteList(id: ListId): DeleteListResult

    /**
     * 前回の買い物のチェック済みを片付ける（docs/requirements.md §3.2）。今までのチェック済みを、リスト詳細と進捗に出さなくする。
     * 買い忘れか聞いて、次の買い物と答えたときに使う
     */
    suspend fun clearRound(id: ListId)
}

sealed interface UpdateListResult {
    data object Updated : UpdateListResult
    data object NotFound : UpdateListResult
}

sealed interface DeleteListResult {
    data object Deleted : DeleteListResult
    data object NotEmpty : DeleteListResult
    data object NotFound : DeleteListResult
}
