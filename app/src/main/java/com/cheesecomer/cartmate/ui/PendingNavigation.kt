package com.cheesecomer.cartmate.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/** 画面の切り替えをまたいで渡す遷移の予約 */
@Singleton
class PendingNavigation @Inject constructor() {
    private val openFamily = AtomicBoolean(false)

    /**
     * 家族を作成すると、所属が変わって家族の作成・参加の画面が先に閉じることがあるため、
     * 作成の前に予約しておき、リスト一覧の側で家族画面を開く（docs/requirements.md §3.5「家族の作成」）
     */
    fun requestFamilyScreen() = openFamily.set(true)

    fun cancelFamilyScreen() = openFamily.set(false)

    /** 予約があれば true を返し、予約を消す */
    fun consumeFamilyScreen(): Boolean = openFamily.getAndSet(false)

    private val _joinCode = MutableStateFlow<String?>(null)

    /**
     * 招待リンクから開いた招待コード。ログインしていなければ、ログインのあとで参加の確認を開く
     * （docs/design/ui.md「最初に表示する画面」）
     */
    val joinCode: StateFlow<String?> = _joinCode.asStateFlow()

    fun requestJoin(code: String) {
        _joinCode.value = code
    }

    /** 予約された招待コードを返し、予約を消す */
    fun consumeJoin(): String? = _joinCode.getAndUpdateToNull()

    private fun MutableStateFlow<String?>.getAndUpdateToNull(): String? {
        while (true) {
            val current = value
            if (compareAndSet(current, null)) return current
        }
    }
}
