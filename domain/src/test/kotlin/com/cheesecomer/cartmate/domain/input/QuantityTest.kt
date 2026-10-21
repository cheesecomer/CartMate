package com.cheesecomer.cartmate.domain.input

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class QuantityTest {

    @ParameterizedTest
    @CsvSource("1, 1.0", "1.5, 1.5", "' 2 ', 2.0", "２, 2.0", "9999, 9999.0", "0.1, 0.1")
    fun `正の数は有効`(text: String, expected: Double) {
        assertThat(QuantityInput.parse(text)).isEqualTo(QuantityInput.Valid(expected))
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "  "])
    fun `空欄は未指定として有効`(text: String) {
        assertThat(QuantityInput.parse(text)).isEqualTo(QuantityInput.Valid(null))
    }

    @ParameterizedTest
    @ValueSource(strings = ["0", "-1", "10000", "abc", "1..2", "NaN", "Infinity"])
    fun `0以下・上限超え・数値でないものは無効`(text: String) {
        assertThat(QuantityInput.parse(text)).isEqualTo(QuantityInput.Invalid)
    }

    @ParameterizedTest
    @CsvSource("1.0, 1", "1.50, 1.5", "0.25, 0.25", "100.0, 100")
    fun `表示用の数量は末尾の0を省く`(value: Double, expected: String) {
        assertThat(formatQuantity(value)).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource("' 本 ', 本", "'', ", "'  ', ")
    fun `単位は前後の空白を除き、空なら未指定`(text: String, expected: String?) {
        assertThat(normalizeUnit(text)).isEqualTo(expected)
    }
}
