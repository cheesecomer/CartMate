package com.cheesecomer.cartmate.domain.text

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class NameNormalizerTest {

    @Test
    fun `表示名は全角・半角の差と前後・連続した空白を整える`() {
        assertThat(NameNormalizer.displayName("ｷﾞｭｳﾆｭｳ")).isEqualTo("ギュウニュウ")
        assertThat(NameNormalizer.displayName("牛乳　１Ｌ")).isEqualTo("牛乳 1L")
        assertThat(NameNormalizer.displayName("  豆乳   1000ml ")).isEqualTo("豆乳 1000ml")
    }

    @Test
    fun `正規化名は英字を小文字にする`() {
        assertThat(NameNormalizer.normalizedName("Coca-Cola ZERO")).isEqualTo("coca-cola zero")
        assertThat(NameNormalizer.normalizedName("ＭＩＬＫ")).isEqualTo("milk")
    }

    @Test
    fun `正規化名はカナを統一しない（同一性の判定に使うため）`() {
        assertThat(NameNormalizer.normalizedName("ミルク")).isNotEqualTo(NameNormalizer.normalizedName("みるく"))
    }

    @Test
    fun `検索キーはカタカナをひらがなにし、空白と記号を除く`() {
        assertThat(NameNormalizer.searchKey("ミルク")).isEqualTo("みるく")
        assertThat(NameNormalizer.searchKey("Coca-Cola ZERO")).isEqualTo("cocacolazero")
        assertThat(NameNormalizer.searchKey("牛乳 1L")).isEqualTo("牛乳1l")
        assertThat(NameNormalizer.searchKey("ヵヶ")).isEqualTo("ゕゖ")
    }

    @Test
    fun `検索キーは長音記号を残す`() {
        assertThat(NameNormalizer.searchKey("コーヒー")).isEqualTo("こーひー")
    }

    @Test
    fun `記号だけの名前の検索キーは正規化名にする`() {
        assertThat(NameNormalizer.searchKey("!!!")).isEqualTo("!!!")
    }

    @Test
    fun `機械的な正規化で一致する名前は同じ productId になる`() {
        val id = NameNormalizer.productId("牛乳 1L")
        assertThat(NameNormalizer.productId("  牛乳　１ｌ ")).isEqualTo(id)
        assertThat(NameNormalizer.productId("牛乳 2L")).isNotEqualTo(id)
    }

    @Test
    fun `productId は n_ と128ビットの16進数`() {
        assertThat(NameNormalizer.productId("牛乳")).matches("n_[0-9a-f]{32}")
    }
}
