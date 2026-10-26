package com.cheesecomer.cartmate.data.firestore

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Snapshot Listener を Flow にする。エラーは Flow の例外として流す */
fun DocumentReference.snapshotFlow(): Flow<DocumentSnapshot> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        if (error != null) close(error) else if (snapshot != null) trySend(snapshot)
    }
    awaitClose { registration.remove() }
}

fun Query.snapshotFlow(): Flow<QuerySnapshot> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        if (error != null) close(error) else if (snapshot != null) trySend(snapshot)
    }
    awaitClose { registration.remove() }
}

/** ルールで読み取りを拒否された（家族から外された場合など） */
val Throwable.isPermissionDenied: Boolean
    get() = this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.PERMISSION_DENIED
