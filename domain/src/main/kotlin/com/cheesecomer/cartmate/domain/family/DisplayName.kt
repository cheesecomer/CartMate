package com.cheesecomer.cartmate.domain.family

import com.cheesecomer.cartmate.domain.model.Member

/**
 * メンバーの表示名（docs/requirements.md §3.5「表示名と呼び名」）。
 * ①自分がその人に付けた呼び名 ②その人が設定した表示名 ③その人の Google アカウントの名前、の順に使う
 */
object DisplayName {
    /** 表示名と呼び名の上限 */
    const val MAX_LENGTH = 20

    /** [member] を見る人の画面に出す名前。[alias] はその人が付けた呼び名。抜けたメンバーは [member] が null */
    fun of(member: Member?, alias: String?): String? =
        listOf(alias, member?.nickname, member?.displayName).firstOrNull { !it.isNullOrBlank() }

    /** 入力した表示名や呼び名を、保存する値にする。前後の空白を除き、空なら解除（null） */
    fun normalize(input: String): String? = input.trim().takeIf { it.isNotEmpty() }

    /**
     * 自分の表示名として保存する値。Google アカウントの名前（[googleName]）と同じなら、表示名を設定しない（null）。
     * 変更のダイアログの初期値は今の名前なので、そのまま保存しても、Google アカウントの名前を表示名として保存しないように
     */
    fun nicknameToSave(input: String, googleName: String): String? = normalize(input)?.takeIf { it != googleName.trim() }

    /** 保存できる長さか */
    fun isValid(input: String): Boolean = input.trim().length <= MAX_LENGTH
}
