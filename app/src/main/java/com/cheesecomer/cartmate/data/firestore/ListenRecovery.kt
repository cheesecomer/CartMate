package com.cheesecomer.cartmate.data.firestore

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.retryWhen
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * 購読のエラーの扱い。[listen] は購読（呼ぶたびに新しい購読を作る）。Snapshot Listener はエラーで止まるので、購読し直す。
 * - 読み取りを拒否されたら、自分の書き込みがサーバーに届くのを待って1回だけ購読し直し、それでも拒否されれば [denied] を流す
 *   （家族を作成・参加した直後は書き込みが届く前に拒否されることがある。届いたあとも拒否されるのは、家族から外された場合）
 * - それ以外のエラー（インデックスの作成中、通信の問題など）は、間隔を空けて購読し直す。その間は何も流さず、直前の表示を保つ
 *   （空の一覧を流すと、リストが消えたように見えるため）
 */
fun <T> recoverListen(
    listen: () -> Flow<T>,
    denied: T,
    awaitPendingWrites: suspend () -> Unit,
    isDenied: (Throwable) -> Boolean = { it.isPermissionDenied },
    onError: (Throwable) -> Unit = {},
    /** 拒否以外のエラーで購読し直している間に流す値。null なら何も流さず、直前の値を保つ */
    whileRetrying: T? = null,
): Flow<T> {
    var retriedAfterDenied = false
    return flow { emitAll(listen()) }
        .retryWhen { cause, attempt ->
            onError(cause)
            if (isDenied(cause)) {
                if (retriedAfterDenied) return@retryWhen false
                retriedAfterDenied = true
                awaitPendingWrites()
            } else {
                if (attempt == 0L && whileRetrying != null) emit(whileRetrying)
                delay(retryDelay(attempt))
            }
            true
        }
        .catch { if (isDenied(it)) emit(denied) else throw it }
}

/** 購読し直すまでの間隔。2秒から倍にしていき、30秒で止める */
private fun retryDelay(attempt: Long): Duration = min(2L shl min(attempt, 4L).toInt(), 30L).seconds

/**
 * 複数の購読（[listens]）の結果を1つの一覧にまとめる。購読は1本ずつやり直す（[recoverListen]）。
 * 1本が失敗し続けても（インデックスの作成中など）、ほかの購読の結果は出るよう、失敗した購読はやり直している間は空として扱う
 */
fun <T> combineListens(
    listens: List<() -> Flow<List<T>>>,
    awaitPendingWrites: suspend () -> Unit,
    isDenied: (Throwable) -> Boolean = { it.isPermissionDenied },
    onError: (Throwable) -> Unit = {},
): Flow<List<T>> = combine(
    listens.map { listen ->
        recoverListen(listen, denied = emptyList(), awaitPendingWrites, isDenied, onError, whileRetrying = emptyList())
    },
) { results -> results.flatMap { it } }
