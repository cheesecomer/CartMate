package com.cheesecomer.cartmate.ui.itementry

import com.cheesecomer.cartmate.domain.model.ListId
import com.cheesecomer.cartmate.domain.model.Visibility
import javax.inject.Inject
import javax.inject.Singleton

/**
 * リストごとに、商品詳細入力画面で直前に選んだ公開範囲（docs/requirements.md §3.4）。
 * アプリを起動している間だけ覚えておき、起動し直すと共有に戻る
 */
@Singleton
class LastVisibilityStore @Inject constructor() {
    private val remembered = mutableMapOf<ListId, Visibility>()

    @Synchronized
    fun get(listId: ListId): Visibility? = remembered[listId]

    @Synchronized
    fun set(listId: ListId, visibility: Visibility) {
        remembered[listId] = visibility
    }
}
