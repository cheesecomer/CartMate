package com.cheesecomer.cartmate.domain.repository

import com.cheesecomer.cartmate.domain.model.ListId
import com.cheesecomer.cartmate.domain.model.ShoppingList
import kotlinx.coroutines.flow.Flow

interface ShoppingListRepository {
    /** 論理削除したリストも含めて返す。表示するかどうかは呼び出し側で判定する */
    fun observeLists(): Flow<List<ShoppingList>>

    suspend fun createList(name: String): ListId

    /** 削除していないリストの名前を変更する。購入の記録に残したリスト名は書き換えない */
    suspend fun renameList(id: ListId, name: String): RenameListResult

    suspend fun deleteList(id: ListId): DeleteListResult
}

sealed interface RenameListResult {
    data object Renamed : RenameListResult
    data object NotFound : RenameListResult
}

sealed interface DeleteListResult {
    data object Deleted : DeleteListResult
    data object NotEmpty : DeleteListResult
    data object NotFound : DeleteListResult
}
