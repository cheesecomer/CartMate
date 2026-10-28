package com.cheesecomer.cartmate.domain.product

import com.cheesecomer.cartmate.domain.product.NormalizationDictionary.Attribute
import com.cheesecomer.cartmate.domain.product.NormalizationDictionary.AttributeKind
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class AttributeExtractorTest {

    private val dictionary = NormalizationDictionary(
        version = "test",
        canonicalNames = emptyMap(),
        aliases = emptyMap(),
        attributes = mapOf(
            "麦茶" to Attribute(AttributeKind.ITEM, "barley_tea"),
            "ほうじ茶" to Attribute(AttributeKind.ITEM, "hojicha"),
            "茶葉" to Attribute(AttributeKind.FORM, "茶葉"),
            "ペットボトル" to Attribute(AttributeKind.FORM, "飲料"),
            "ペット" to Attribute(AttributeKind.FORM, "飲料"),
            "詰め替え" to Attribute(AttributeKind.VARIANT, "詰め替え"),
            "燃えるゴミ用" to Attribute(AttributeKind.USE, "燃えるゴミ"),
            "特売なら" to Attribute(AttributeKind.IGNORE, "-"),
            "特売" to Attribute(AttributeKind.IGNORE, "-"),
            "いつもの" to Attribute(AttributeKind.IGNORE, "-"),
        ),
    )

    private fun extract(name: String, memo: String? = null) = AttributeExtractor.extract(name, memo, dictionary)

    @Test
    fun `備考の品目の語を取り出す`() {
        val attributes = extract("お茶", "麦茶")
        assertThat(attributes.nameItems).isEmpty()
        assertThat(attributes.memoItems).containsExactly(CanonicalId("barley_tea"))
        assertThat(attributes.fromMemo).isTrue()
    }

    @Test
    fun `商品名の品目の語は商品名の側に入る`() {
        assertThat(extract("麦茶").nameItems).containsExactly(CanonicalId("barley_tea"))
    }

    @Test
    fun `形態・種類・用途を取り出す`() {
        assertThat(extract("緑茶", "茶葉").qualifiers).containsExactly(Qualifier("form", "茶葉"))
        assertThat(extract("シャンプー", "詰め替え").qualifiers).containsExactly(Qualifier("variant", "詰め替え"))
        assertThat(extract("ゴミ袋", "燃えるゴミ用").qualifiers).containsExactly(Qualifier("use", "燃えるゴミ"))
    }

    @Test
    fun `長い語を先に当て、短い語で二重に数えない`() {
        assertThat(extract("緑茶", "ペットボトル").qualifiers).containsExactly(Qualifier("form", "飲料"))
    }

    @Test
    fun `判定に使わない語は除く`() {
        val attributes = extract("牛乳", "特売なら いつもの")
        assertThat(attributes.qualifiers).isEmpty()
        assertThat(attributes.fromMemo).isFalse()
    }

    @Test
    fun `判定に使わない語を除いたあとの語は取り出す`() {
        assertThat(extract("お茶", "特売なら麦茶").memoItems).containsExactly(CanonicalId("barley_tea"))
    }

    @Test
    fun `容量は単位をそろえて同じものとみなす`() {
        val twoLiters = Qualifier("volume", "2000ml")
        assertThat(extract("緑茶", "2L").qualifiers).containsExactly(twoLiters)
        assertThat(extract("緑茶", "2リットル").qualifiers).containsExactly(twoLiters)
        assertThat(extract("緑茶", "２０００ｍｌ").qualifiers).containsExactly(twoLiters)
        assertThat(extract("米", "5kg").qualifiers).containsExactly(Qualifier("volume", "5000g"))
        assertThat(extract("牛乳", "500ml").qualifiers).containsExactly(Qualifier("volume", "500ml"))
    }

    @Test
    fun `属性のない備考は属性なし`() {
        val attributes = extract("牛乳", "低脂肪のやつ")
        assertThat(attributes.qualifiers).isEmpty()
        assertThat(attributes.memoItems).isEmpty()
        assertThat(attributes.fromMemo).isFalse()
    }
}
