package com.cheesecomer.cartmate.testing

import com.cheesecomer.cartmate.domain.model.ListId
import com.cheesecomer.cartmate.domain.model.NewItem
import com.cheesecomer.cartmate.domain.model.NewList
import com.cheesecomer.cartmate.domain.model.Visibility
import com.cheesecomer.cartmate.domain.repository.ShoppingListRepository

/** 備考なしの共有のリストを作る（公開範囲などを指定したいときだけ引数を渡す） */
suspend fun ShoppingListRepository.createList(
    name: String,
    visibility: Visibility = Visibility.SHARED,
    memo: String? = null,
): ListId = createList(NewList(name, memo, visibility))

fun newItem(
    listId: ListId,
    name: String,
    quantity: Double? = null,
    unit: String? = null,
    memo: String? = null,
    visibility: Visibility = Visibility.SHARED,
) = NewItem(listId, name, quantity, unit, memo, visibility)
