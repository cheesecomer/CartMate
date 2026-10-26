package com.cheesecomer.cartmate.data.firestore

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/** [uid] のユーザーについての値 */
data class UserValue<T>(val uid: String?, val value: T)

/**
 * [values] のうち、今ログインしているユーザー（[uid]）の値。ほかのユーザーの値は流さない。
 * [values] は前の値を持っている共有の購読なので、ログインやアカウントの切り替えの直後に、
 * 前のユーザー（ログインしていない状態を含む）の値が先に届く。それを使うと、家族に参加済みでも
 * 家族の作成・参加の画面が一瞬見えてしまう
 */
fun <T> currentUserValue(uid: Flow<String?>, values: Flow<UserValue<T>>): Flow<T> =
    combine(uid, values) { uid, latest -> uid to latest }
        .filter { (uid, latest) -> latest.uid == uid }
        .map { (_, latest) -> latest.value }
        .distinctUntilChanged()
