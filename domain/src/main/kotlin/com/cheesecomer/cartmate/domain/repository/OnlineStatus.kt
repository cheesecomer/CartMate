package com.cheesecomer.cartmate.domain.repository

import kotlinx.coroutines.flow.Flow

/** オンラインかどうか（docs/requirements.md §3.3） */
fun interface OnlineStatus {
    fun observeOnline(): Flow<Boolean>
}
