package com.cheesecomer.cartmate.domain.text

import com.google.common.truth.Truth.assertWithMessage
import org.junit.jupiter.api.Test

class ChoiceNameTest {

    @Test
    fun `選択の語を含む名前は選択肢を含む`() {
        listOf("カブもしくは大根", "カブまたは大根", "カブあるいは大根", "カブか大根のどちらか", "カブ/大根", "カブ／大根", "Coke or Pepsi", "カブ か 大根", "カブか大根")
            .forEach { assertWithMessage(it).that(ChoiceName.hasChoice(it)).isTrue() }
    }

    @Test
    fun `ひらがなの中の「か」や、英単語の中の or は選択ではない`() {
        listOf("カブ", "大根", "みかん", "いか", "かぼちゃ", "わかめ", "orange", "color", "牛乳", "あかもく")
            .forEach { assertWithMessage(it).that(ChoiceName.hasChoice(it)).isFalse() }
    }
}
