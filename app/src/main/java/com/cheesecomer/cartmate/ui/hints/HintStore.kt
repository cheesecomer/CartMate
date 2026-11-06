package com.cheesecomer.cartmate.ui.hints

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 発見しにくい機能の、一度だけ出す案内（フィードバック #014）。見たかどうかを端末に覚えておき、二度と出さない。
 * アカウントを切り替えても出し直さない
 */
interface HintStore {
    suspend fun isShown(hint: Hint): Boolean

    suspend fun markShown(hint: Hint)
}

enum class Hint(val key: String) {
    /** リスト一覧のアバターから、家族とアカウントの設定を開けること */
    FAMILY_ENTRY("family_entry"),
}

@Singleton
class SharedPreferencesHintStore @Inject constructor(@ApplicationContext private val context: Context) : HintStore {
    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    override suspend fun isShown(hint: Hint): Boolean = withContext(Dispatchers.IO) { prefs.getBoolean(hint.key, false) }

    override suspend fun markShown(hint: Hint) = withContext(Dispatchers.IO) { prefs.edit { putBoolean(hint.key, true) } }

    companion object {
        /** スクリーンショットの撮影で、案内を見たことにしておくときにも使う */
        const val PREFS_NAME = "hints"
    }
}

/** テスト用。端末に保存しない */
class InMemoryHintStore(vararg shown: Hint) : HintStore {
    private val shown = shown.toMutableSet()

    override suspend fun isShown(hint: Hint): Boolean = hint in shown

    override suspend fun markShown(hint: Hint) {
        shown += hint
    }
}
