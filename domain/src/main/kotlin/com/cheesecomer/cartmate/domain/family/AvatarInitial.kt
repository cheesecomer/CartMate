package com.cheesecomer.cartmate.domain.family

/** 写真がないアバターに表示する頭文字（docs/requirements.md §3.5「アバター」） */
object AvatarInitial {

    /** [name] の最初の1文字。英字は大文字にする。名前が空なら null */
    fun of(name: String): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        val first = trimmed.codePointAt(0)
        return String(Character.toChars(Character.toUpperCase(first)))
    }
}
