package com.cheesecomer.cartmate.domain.repository

import com.cheesecomer.cartmate.domain.model.ListId
import com.cheesecomer.cartmate.domain.model.ShoppingList
import kotlinx.coroutines.flow.Flow

interface ShoppingListRepository {
    /** 論理削除したリストも含めて返す。表示するかどうかは呼び出し側で判定する */
    fun observeLists(): Flow<List<ShoppingList>>

    suspend fun createList(name: String): ListId

    suspend fun deleteList(id: ListId): DeleteListResult
}

sealed interface DeleteListResult {
    data object Deleted : DeleteListResult
    data object NotEmpty : DeleteListResult
    data object NotFound : DeleteListResult
}
