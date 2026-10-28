package com.cheesecomer.cartmate.domain.text

/**
 * 選択肢を含む商品名（「カブもしくは大根」「カブか大根」）の判定（docs/requirements.md §4.2）。
 * どちらを買ったかは確定しないので、こうした名前は表記ゆれの候補にせず、ほかの商品にまとめない
 */
object ChoiceName {

    /** 選択を表す語。「か」は前後で判定するので別に扱う */
    private val WORDS = listOf("もしくは", "または", "あるいは", "どちらか", "どっちか", "いずれか", "/", "／", "|", "｜")

    /** 英語の「or」は、前後が英字でないときだけ（「orange」「color」を除く） */
    private val OR = Regex("""(?<![a-z])or(?![a-z])""")

    fun hasChoice(name: String): Boolean {
        val text = NameNormalizer.normalizedName(name)
        if (WORDS.any { text.contains(it) } || OR.containsMatchIn(text)) return true
        // 「カブか大根」の「か」。前後が両方ともひらがな以外（カタカナ・漢字・英数字・空白・記号）のときだけ。
        // 「みかん」「いか」のように、ひらがなの中の「か」は選択ではない
        return text.indices.any { i ->
            text[i] == 'か' && i > 0 && i < text.lastIndex && !text[i - 1].isHiragana() && !text[i + 1].isHiragana()
        }
    }

    private fun Char.isHiragana(): Boolean = this in 'ぁ'..'ゖ' || this == 'ー'
}
