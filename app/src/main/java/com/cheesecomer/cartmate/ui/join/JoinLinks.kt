package com.cheesecomer.cartmate.ui.join

import android.net.Uri
import com.cheesecomer.cartmate.domain.family.InviteCode

/** 招待リンク（docs/design/security.md「招待リンク（App Links）」） */
object JoinLinks {
    const val HOST = "cartmate.cheesecomer.com"
    private const val PATH_PREFIX = "/join/"

    fun url(code: InviteCode): String = "https://$HOST$PATH_PREFIX${code.value}"

    /** 招待リンクから招待コードを取り出す。招待リンクでなければ null */
    fun codeOf(uri: Uri): String? {
        if (uri.scheme != "https" || uri.host != HOST) return null
        val path = uri.path ?: return null
        if (!path.startsWith(PATH_PREFIX)) return null
        return path.removePrefix(PATH_PREFIX).trim('/').takeIf { it.isNotEmpty() }
    }
}
