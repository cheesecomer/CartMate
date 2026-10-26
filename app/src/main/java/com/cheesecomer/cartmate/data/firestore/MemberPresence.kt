package com.cheesecomer.cartmate.data.firestore

import kotlinx.coroutines.flow.Flow

/**
 * 自分の members があるか。[listen] は自分の members の購読（ドキュメントがあれば true）。
 * 外されると自分の members を読めなくなる（ルールの isMember）ので、読み取りを拒否されたら false にする。
 * ただし、家族を作成・参加した直後は、自分の書き込みがサーバーに届く前に購読が始まって拒否されることがあるため、
 * 書き込みが届くのを待って購読し直し、それでも拒否されたときだけ false にする（[recoverListen]）
 */
fun memberPresence(
    listen: () -> Flow<Boolean>,
    awaitPendingWrites: suspend () -> Unit,
    isDenied: (Throwable) -> Boolean = { it.isPermissionDenied },
    onError: (Throwable) -> Unit = {},
): Flow<Boolean> = recoverListen(listen, denied = false, awaitPendingWrites, isDenied, onError)
