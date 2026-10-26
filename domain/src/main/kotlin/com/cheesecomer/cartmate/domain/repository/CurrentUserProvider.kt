package com.cheesecomer.cartmate.domain.repository

import com.cheesecomer.cartmate.domain.model.UserId

/** ログイン中のユーザー（Firebase Auth） */
fun interface CurrentUserProvider {
    fun currentUserId(): UserId
}
