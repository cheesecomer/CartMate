package com.cheesecomer.cartmate.data.firestore

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.retryWhen

/**
 * 自分の members があるか。[listen] は自分の members の購読（ドキュメントがあれば true）。
 * 外されると自分の members を読めなくなる（ルールの isMember）ので、読み取りを拒否されたら false にする。
 * ただし、家族を作成・参加した直後は、自分の書き込みがサーバーに届く前に購読が始まって拒否されることがあるため、
 * 書き込みが届くのを待って購読し直し、それでも拒否されたときだけ false にする
 */
fun memberPresence(
    listen: () -> Flow<Boolean>,
    awaitPendingWrites: suspend () -> Unit,
    isDenied: (Throwable) -> Boolean = { it.isPermissionDenied },
): Flow<Boolean> = flow { emitAll(listen()) }
    .retryAfterPendingWrites(awaitPendingWrites, isDenied)
    .catch { if (isDenied(it)) emit(false) else throw it }

/**
 * 読み取りを拒否されたら、自分の書き込みがサーバーに届くのを待って、1回だけ購読し直す。
 * 自分の書き込み（家族の作成・参加）の直後に、その家族の購読をサーバーが拒否するのを避ける
 */
fun <T> Flow<T>.retryAfterPendingWrites(
    awaitPendingWrites: suspend () -> Unit,
    isDenied: (Throwable) -> Boolean = { it.isPermissionDenied },
): Flow<T> = retryWhen { cause, attempt ->
    if (attempt == 0L && isDenied(cause)) {
        awaitPendingWrites()
        true
    } else {
        false
    }
}
