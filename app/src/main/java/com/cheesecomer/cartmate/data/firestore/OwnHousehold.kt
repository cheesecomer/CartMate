package com.cheesecomer.cartmate.data.firestore

import com.cheesecomer.cartmate.domain.model.HouseholdId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** 自分の users から読んだ所属。[denied] はサーバーに読み取りを断られた */
data class OwnHousehold(val householdId: HouseholdId?, val denied: Boolean = false)

/**
 * 自分の users（[listen]）から所属を読む。自分の users は本人なら必ず読めるので、断られたのは家族がないからではなく、
 * 端末やアプリを確認できない（App Check のデバッグ用トークンが未登録など）ため。家族がないとは流さず、断られたと流す
 * （家族の作成・参加の画面に進めても、書き込みも断られて先へ進めない。フィードバック #020）。
 * 確認できるようになれば読めるので、[deniedRetry] ごとに読み直す
 */
fun ownHousehold(
    listen: () -> Flow<OwnHousehold>,
    awaitPendingWrites: suspend () -> Unit,
    isDenied: (Throwable) -> Boolean = { it.isPermissionDenied },
    onError: (Throwable) -> Unit = {},
    deniedRetry: Duration = DENIED_RETRY,
): Flow<OwnHousehold> = flow {
    // recoverListen は、断られて denied を流したときだけ終わる
    while (true) {
        emitAll(recoverListen(listen, denied = OwnHousehold(null, denied = true), awaitPendingWrites, isDenied, onError))
        delay(deniedRetry)
    }
}

private val DENIED_RETRY = 10.seconds
