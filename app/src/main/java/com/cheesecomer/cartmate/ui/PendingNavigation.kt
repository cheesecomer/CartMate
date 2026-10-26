package com.cheesecomer.cartmate.ui

import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 画面の切り替えをまたいで渡す遷移の予約。家族を作成すると、所属が変わって家族の作成・参加の画面が先に閉じることがあるため、
 * 作成の前に予約しておき、リスト一覧の側で家族画面を開く（docs/requirements.md §3.5「家族の作成」）
 */
@Singleton
class PendingNavigation @Inject constructor() {
    private val openFamily = AtomicBoolean(false)

    fun requestFamilyScreen() = openFamily.set(true)

    fun cancelFamilyScreen() = openFamily.set(false)

    /** 予約があれば true を返し、予約を消す */
    fun consumeFamilyScreen(): Boolean = openFamily.getAndSet(false)
}
