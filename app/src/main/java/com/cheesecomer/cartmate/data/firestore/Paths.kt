package com.cheesecomer.cartmate.data.firestore

import com.cheesecomer.cartmate.domain.family.InviteCode
import com.cheesecomer.cartmate.domain.model.HouseholdId
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore

/** Firestore のパス（docs/design/data-model.md「コレクション」） */
object Paths {
    fun user(db: FirebaseFirestore, uid: String): DocumentReference = db.collection("users").document(uid)

    fun invites(db: FirebaseFirestore): CollectionReference = db.collection("invites")

    fun invite(db: FirebaseFirestore, code: InviteCode): DocumentReference = invites(db).document(code.value)

    fun households(db: FirebaseFirestore): CollectionReference = db.collection("households")

    fun household(db: FirebaseFirestore, id: HouseholdId): DocumentReference = households(db).document(id.value)

    fun members(db: FirebaseFirestore, id: HouseholdId): CollectionReference = household(db, id).collection("members")

    fun member(db: FirebaseFirestore, id: HouseholdId, uid: String): DocumentReference = members(db, id).document(uid)

    fun lists(db: FirebaseFirestore, id: HouseholdId): CollectionReference = household(db, id).collection("lists")

    fun items(db: FirebaseFirestore, id: HouseholdId): CollectionReference = household(db, id).collection("items")

    fun products(db: FirebaseFirestore, id: HouseholdId): CollectionReference = household(db, id).collection("products")

    fun purchases(db: FirebaseFirestore, id: HouseholdId): CollectionReference = household(db, id).collection("purchases")
}

/** フィールド名 */
object F {
    const val HOUSEHOLD_ID = "householdId"
    const val UPDATED_AT = "updatedAt"
    const val NAME = "name"
    const val CREATED_BY = "createdBy"
    const val CREATED_AT = "createdAt"
    const val INVITER_NAME = "inviterName"
    const val EXPIRES_AT = "expiresAt"
    const val MAX_USES = "maxUses"
    const val USED_COUNT = "usedCount"
    const val ROLE = "role"
    const val DISPLAY_NAME = "displayName"
    const val PHOTO_URL = "photoUrl"
    const val NICKNAME = "nickname"
    const val MEMBER_ALIASES = "memberAliases"
    const val JOINED_AT = "joinedAt"
    const val JOINED_VIA = "joinedVia"
    const val MEMO = "memo"
    const val VISIBILITY = "visibility"
    const val SORT_KEY = "sortKey"
    const val STATUS = "status"
    const val DELETED_AT = "deletedAt"
    const val DELETED_BY = "deletedBy"
    const val LIST_ID = "listId"
    const val PRODUCT_ID = "productId"
    const val INPUT_NAME = "inputName"
    const val QUANTITY = "quantity"
    const val UNIT = "unit"
    const val COMPLETED_AT = "completedAt"
    const val COMPLETED_BY = "completedBy"
    const val NORMALIZED_NAME = "normalizedName"
    const val SEARCH_KEY = "searchKey"
    const val MERGED_INTO = "mergedInto"
    const val ITEM_ID = "itemId"
    const val LIST_NAME_SNAPSHOT = "listNameSnapshot"
    const val CHECKED_AT = "checkedAt"
    const val PURCHASED_AT = "purchasedAt"
    const val PURCHASED_BY = "purchasedBy"
    const val ACTIVE = "active"
    const val REVOKED_AT = "revokedAt"
    const val REVOKED_BY = "revokedBy"
    const val IMPORTED_FROM = "importedFrom"
    const val CLEARED_AT = "clearedAt"
    const val MERGED_AT = "mergedAt"
    const val MERGED_BY = "mergedBy"
    const val MERGE_UPDATED_AT = "mergeUpdatedAt"
    const val MERGE_UPDATED_BY = "mergeUpdatedBy"
    const val HIDDEN_AT = "hiddenAt"
    const val HIDDEN_BY = "hiddenBy"
    const val DICTIONARY_OPT_OUT = "dictionaryOptOut"
}

/** フィールドの値 */
object V {
    const val SHARED = "shared"
    const val PRIVATE = "private"
    const val ACTIVE = "active"
    const val DELETED = "deleted"
    const val OPEN = "open"
    const val COMPLETED = "completed"
}
