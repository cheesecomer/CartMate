package com.cheesecomer.cartmate.data.firestore

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.WriteBatch
import java.time.Instant

/**
 * リスト詳細に表示する項目のクエリ。未購入の項目と、[since] 以降にチェックされた項目の2つに分ける（結果は呼び出し側でまとめる）。
 * - チェックした時刻は、サーバーの時刻の completedAt ではなく、端末の時刻の checkedAt で比べる。
 *   completedAt はサーバーに届くまで端末の中では値がないため、オフラインでチェックした項目が結果から消えてしまう
 * - 1つの OR クエリにしないのは、範囲の条件を含むクエリが、そのフィールドがないドキュメント（checkedAt のない未購入の項目）を
 *   結果から除くため
 */
fun Query.listItemQueries(since: Instant): List<Query> = listOf(
    whereEqualTo(F.STATUS, V.OPEN),
    whereEqualTo(F.STATUS, V.COMPLETED).whereGreaterThanOrEqualTo(F.CHECKED_AT, since.toTimestamp()),
)

/** 項目をチェック済みにする書き込み（購入の記録は別に書く。docs/design/sync.md「チェック」） */
fun WriteBatch.markCompleted(itemRef: DocumentReference, uid: String, checkedAt: Instant): WriteBatch = update(
    itemRef,
    F.STATUS, V.COMPLETED,
    F.COMPLETED_AT, FieldValue.serverTimestamp(),
    F.COMPLETED_BY, uid,
    F.CHECKED_AT, checkedAt.toTimestamp(),
)

/** チェックを外す書き込み（購入の記録の無効化は別に書く。docs/design/sync.md「Undo」） */
fun WriteBatch.markOpen(itemRef: DocumentReference): WriteBatch =
    update(itemRef, F.STATUS, V.OPEN, F.COMPLETED_AT, null, F.COMPLETED_BY, null, F.CHECKED_AT, null)
