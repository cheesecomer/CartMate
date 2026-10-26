package com.cheesecomer.cartmate.ui.itementry

import com.cheesecomer.cartmate.domain.model.ListId
import com.cheesecomer.cartmate.domain.model.Visibility
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * リストごとに、直前に選んだ公開範囲（docs/requirements.md §3.4）。商品詳細入力画面でも、サジェストのシートでも選べる。
 * アプリを起動している間だけ覚えておき、起動し直すと共有に戻る
 */
@Singleton
class LastVisibilityStore @Inject constructor() {
    private val remembered = MutableStateFlow<Map<ListId, Visibility>>(emptyMap())

    fun get(listId: ListId): Visibility? = remembered.value[listId]

    /** [listId] で直前に選んだ公開範囲。まだ選んでいなければ null */
    fun observe(listId: ListId): Flow<Visibility?> = remembered.map { it[listId] }.distinctUntilChanged()

    fun set(listId: ListId, visibility: Visibility) {
        remembered.update { it + (listId to visibility) }
    }
}
