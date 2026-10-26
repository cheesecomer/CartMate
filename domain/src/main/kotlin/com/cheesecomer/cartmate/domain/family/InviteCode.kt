package com.cheesecomer.cartmate.domain.family

import java.util.random.RandomGenerator

/**
 * 招待コード（docs/design/security.md「招待コード」）。Crockford Base32 の10文字で、
 * 読み間違えやすい I・L・O・U を含まない。[value] は区切りのない大文字で、Firestore の invites のドキュメントIDになる
 */
@JvmInline
value class InviteCode private constructor(val value: String) {

    /** 表示用。「7KQ2-M9XP-HA」のように 4・4・2 文字で区切る */
    fun formatted(): String = "${value.substring(0, 4)}-${value.substring(4, 8)}-${value.substring(8)}"

    companion object {
        const val LENGTH = 10
        const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

        /** 新しいコード。[random] には暗号論的に安全な乱数（SecureRandom）を渡す */
        fun generate(random: RandomGenerator): InviteCode =
            InviteCode(String(CharArray(LENGTH) { ALPHABET[random.nextInt(ALPHABET.length)] }))

        /**
         * 入力されたコード。区切り（ハイフン・空白）と大文字・小文字を問わない。
         * Crockford Base32 の決まりに沿って、I と L は 1、O は 0 として読む。10文字にならなければ null
         */
        fun parse(input: String): InviteCode? = codeChars(input).takeIf { it.length == LENGTH }?.let(::InviteCode)

        /**
         * 入力途中の文字列を、コードに使える文字だけの大文字にする（最大10文字）。
         * 入力欄で区切りを自動で入れるために使う
         */
        fun normalize(input: String): String = codeChars(input).take(LENGTH)

        private fun codeChars(input: String): String = buildString {
            for (c in input.uppercase()) {
                when (c) {
                    'I', 'L' -> append('1')
                    'O' -> append('0')
                    in ALPHABET -> append(c)
                }
            }
        }

        /** 入力途中の文字列の表示。[normalize] した文字列に、4文字目と8文字目のあとで区切りを入れる */
        fun formatPartial(input: String): String = normalize(input).chunkedBy(4, 4, 2).joinToString("-")

        private fun String.chunkedBy(vararg sizes: Int): List<String> {
            val chunks = mutableListOf<String>()
            var start = 0
            for (size in sizes) {
                if (start >= length) break
                chunks += substring(start, minOf(start + size, length))
                start += size
            }
            return chunks
        }
    }
}
