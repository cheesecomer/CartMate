package com.cheesecomer.cartmate.data.firestore

import com.cheesecomer.cartmate.domain.model.Item
import com.cheesecomer.cartmate.domain.model.ItemId
import com.cheesecomer.cartmate.domain.model.ItemStatus
import com.cheesecomer.cartmate.domain.model.ListId
import com.cheesecomer.cartmate.domain.model.ListStatus
import com.cheesecomer.cartmate.domain.model.Product
import com.cheesecomer.cartmate.domain.model.ProductId
import com.cheesecomer.cartmate.domain.model.Purchase
import com.cheesecomer.cartmate.domain.model.ShoppingList
import com.cheesecomer.cartmate.domain.model.UserId
import com.cheesecomer.cartmate.domain.model.Visibility
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import java.time.Instant
import java.util.Date

fun Visibility.toField(): String = if (this == Visibility.SHARED) V.SHARED else V.PRIVATE

private fun String?.toVisibility(): Visibility = if (this == V.PRIVATE) Visibility.PRIVATE else Visibility.SHARED

fun Instant.toTimestamp(): Timestamp = Timestamp(Date.from(this))

/** サーバーの時刻が確定する前（自分の書き込みの直後）は、見積もりの時刻を使う */
private fun DocumentSnapshot.instant(field: String): Instant? =
    getTimestamp(field, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.toInstant()

fun DocumentSnapshot.toShoppingList(): ShoppingList? = ShoppingList(
    id = ListId(id),
    name = getString(F.NAME) ?: return null,
    memo = getString(F.MEMO),
    visibility = getString(F.VISIBILITY).toVisibility(),
    sortKey = getDouble(F.SORT_KEY) ?: return null,
    createdAt = instant(F.CREATED_AT) ?: return null,
    createdBy = UserId(getString(F.CREATED_BY) ?: return null),
    status = if (getString(F.STATUS) == V.DELETED) ListStatus.DELETED else ListStatus.ACTIVE,
    clearedAt = instant(F.CLEARED_AT),
)

fun DocumentSnapshot.toItem(): Item? = Item(
    id = ItemId(id),
    listId = ListId(getString(F.LIST_ID) ?: return null),
    inputName = getString(F.INPUT_NAME) ?: return null,
    sortKey = getDouble(F.SORT_KEY) ?: return null,
    quantity = getDouble(F.QUANTITY),
    unit = getString(F.UNIT),
    memo = getString(F.MEMO),
    visibility = getString(F.VISIBILITY).toVisibility(),
    status = if (getString(F.STATUS) == V.COMPLETED) ItemStatus.COMPLETED else ItemStatus.OPEN,
    createdAt = instant(F.CREATED_AT) ?: return null,
    createdBy = UserId(getString(F.CREATED_BY) ?: return null),
    completedAt = instant(F.COMPLETED_AT),
    completedBy = getString(F.COMPLETED_BY)?.let(::UserId),
    productId = getString(F.PRODUCT_ID)?.let(::ProductId),
)

fun DocumentSnapshot.toProduct(): Product? = Product(
    id = ProductId(id),
    displayName = getString(F.DISPLAY_NAME) ?: return null,
    normalizedName = getString(F.NORMALIZED_NAME) ?: return null,
    searchKey = getString(F.SEARCH_KEY) ?: return null,
    createdAt = instant(F.CREATED_AT) ?: return null,
    mergedInto = getString(F.MERGED_INTO)?.let(::ProductId),
    dictionaryOptOut = getBoolean(F.DICTIONARY_OPT_OUT) ?: false,
)

fun DocumentSnapshot.toPurchase(): Purchase? = Purchase(
    itemId = ItemId(id),
    listId = ListId(getString(F.LIST_ID) ?: return null),
    listNameSnapshot = getString(F.LIST_NAME_SNAPSHOT).orEmpty(),
    inputName = getString(F.INPUT_NAME) ?: return null,
    quantity = getDouble(F.QUANTITY),
    unit = getString(F.UNIT),
    memo = getString(F.MEMO),
    visibility = getString(F.VISIBILITY).toVisibility(),
    checkedAt = instant(F.CHECKED_AT) ?: return null,
    purchasedAt = instant(F.PURCHASED_AT) ?: return null,
    purchasedBy = UserId(getString(F.PURCHASED_BY) ?: return null),
    active = getBoolean(F.ACTIVE) ?: return null,
    revokedAt = instant(F.REVOKED_AT),
    revokedBy = getString(F.REVOKED_BY)?.let(::UserId),
    productId = getString(F.PRODUCT_ID)?.let(::ProductId),
)
