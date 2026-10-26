package com.cheesecomer.cartmate.domain.text

import java.security.MessageDigest
import java.text.Normalizer

/**
 * 商品名の正規化（docs/design/suggestions.md「商品名の正規化」）。
 * 機械的な正規化で一致する名前は、自動で同じ商品になる（docs/requirements.md §4.2）
 */
object NameNormalizer {

    /** 表示用。NFKC で全角・半角の差を吸収し、前後の空白を除き、連続した空白を1つにする */
    fun displayName(input: String): String =
        Normalizer.normalize(input, Normalizer.Form.NFKC).trim().replace(WHITESPACE, " ")

    /** 同一性の判定に使う。[displayName] の英字を小文字にする。決定的な productId の元になる */
    fun normalizedName(input: String): String = displayName(input).lowercase()

    /**
     * 補完と似た名前の検索だけに使う。[normalizedName] のカタカナをひらがなにし、空白と記号を除く。
     * 記号だけの名前で空になる場合は [normalizedName] を使う（商品のドキュメントは空にできないため）
     */
    fun searchKey(input: String): String {
        val normalized = normalizedName(input)
        val key = buildString {
            for (c in normalized) {
                when {
                    c in KATAKANA -> append(c - KATAKANA_TO_HIRAGANA)
                    c.isLetterOrDigit() -> append(c)
                }
            }
        }
        return key.ifEmpty { normalized }
    }

    /** 決定的な productId。`"n_"` と [normalizedName] の SHA-256 の先頭128ビット（hex） */
    fun productId(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(normalizedName(input).toByteArray(Charsets.UTF_8))
        return "n_" + digest.take(16).joinToString("") { "%02x".format(it) }
    }

    private val WHITESPACE = Regex("\\s+")

    /** ひらがなと対応があるカタカナ（ァ〜ヶ） */
    private val KATAKANA = 'ァ'..'ヶ'
    private const val KATAKANA_TO_HIRAGANA = 'ァ' - 'ぁ'
}
