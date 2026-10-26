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
    const val JOINED_AT = "joinedAt"
    const val JOINED_VIA = "joinedVia"
}
