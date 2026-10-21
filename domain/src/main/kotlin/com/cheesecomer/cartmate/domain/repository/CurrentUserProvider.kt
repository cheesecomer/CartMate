package com.cheesecomer.cartmate.domain.repository

import com.cheesecomer.cartmate.domain.model.UserId

/** ログイン中のユーザー。フェーズ2で Firebase Auth の実装に差し替える */
fun interface CurrentUserProvider {
    fun currentUserId(): UserId
}
