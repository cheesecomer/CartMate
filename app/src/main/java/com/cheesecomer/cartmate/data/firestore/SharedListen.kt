package com.cheesecomer.cartmate.data.firestore

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn

/**
 * 購読している画面が1つでもある間は、購読を1本にまとめ、最新の結果をアプリ内に持っておく。あとから購読した画面には、
 * 端末のキャッシュを読み直さずにすぐ渡す（リスト一覧から開いたリスト詳細が、白紙にならない。フィードバック #021）。
 * どの画面も購読していなくなったら止めて、持っていた結果も捨てる（古い結果を渡さない）
 */
fun <T> Flow<T>.shareWhileObserved(scope: CoroutineScope): Flow<T> =
    shareIn(scope, SharingStarted.WhileSubscribed(replayExpirationMillis = 0), replay = 1)
