package com.cheesecomer.cartmate.data.firestore

/**
 * ドキュメントがあるか。[exists] はスナップショットにドキュメントがあるか、[fromCache] は端末のキャッシュから読んだ結果か。
 * キャッシュにないだけ（サーバーの結果がまだ届いていない）なら、まだ分からないとして null を返す。
 * ログインした直後は、自分の users がまだキャッシュになく、サーバーの結果より先に「ない」が届くため、
 * それを「家族に参加していない」と判定すると、家族の作成・参加の画面が一瞬見えてしまう
 */
fun documentPresence(exists: Boolean, fromCache: Boolean): Boolean? = if (!exists && fromCache) null else exists
