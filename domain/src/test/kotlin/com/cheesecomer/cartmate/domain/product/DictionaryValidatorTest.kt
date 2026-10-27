package com.cheesecomer.cartmate.domain.product

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class DictionaryValidatorTest {

    private fun source(
        items: String = "canonical_id,name,source\negg,卵,m\nmilk,牛乳,m",
        aliases: String = "alias,canonical_id,match,source\nたまご,egg,prefix,m",
        cycleGroups: String = "cycle_group_id,canonical_id",
        neverMerge: String = "left_id,right_id,reason",
        retiredIds: String = "canonical_id,retired_in,reason",
        version: String = "1",
        attributes: String = "term,kind,value,source",
    ) = DictionarySource(version, items, aliases, cycleGroups, neverMerge, retiredIds, attributes)

    @Test
    fun `正しい元データには問題がない`() {
        assertThat(DictionaryValidator.validate(source())).isEmpty()
    }

    @Test
    fun `正規化すると同じ名前が別の品目にあれば問題`() {
        val problems = DictionaryValidator.validate(source(aliases = "alias,canonical_id,match,source\nﾀﾏｺﾞ,egg,prefix,m\nタマゴ,milk,prefix,m"))
        assertThat(problems.joinToString()).contains("タマゴ")
    }

    @Test
    fun `存在しない品目への参照は問題`() {
        val problems = DictionaryValidator.validate(source(aliases = "alias,canonical_id,match,source\nたまご,eggs,prefix,m"))
        assertThat(problems.joinToString()).contains("別名の品目がない")
    }

    @Test
    fun `使わなくなった ID の再利用は問題`() {
        val problems = DictionaryValidator.validate(source(retiredIds = "canonical_id,retired_in,reason\nmilk,2026.09.0,分割"))
        assertThat(problems.joinToString()).contains("再利用")
    }

    @Test
    fun `短いかなの別名を前方一致に使うのは問題`() {
        val problems = DictionaryValidator.validate(
            source(items = "canonical_id,name,source\nvinegar,酢,m", aliases = "alias,canonical_id,match,source\nす,vinegar,prefix,m"),
        )
        assertThat(problems.joinToString()).contains("exact")
        assertThat(
            DictionaryValidator.validate(source(items = "canonical_id,name,source\nvinegar,酢,m", aliases = "alias,canonical_id,match,source\nす,vinegar,exact,m")),
        ).isEmpty()
    }

    @Test
    fun `周期のグループの ID は品目と別の名前空間`() {
        val problems = DictionaryValidator.validate(source(cycleGroups = "cycle_group_id,canonical_id\nmilk,egg"))
        assertThat(problems.joinToString()).contains("品目の ID と同じ")
    }

    @Test
    fun `統合禁止の組に理由がなければ問題`() {
        val problems = DictionaryValidator.validate(source(neverMerge = "left_id,right_id,reason\negg,milk,"))
        assertThat(problems.joinToString()).contains("理由")
    }

    @Test
    fun `辞書を作ると、品目の名前も別名になり、正規化して照合できる`() {
        val dictionary = source().toDictionary()
        assertThat(dictionary.lookup("卵")?.canonicalId).isEqualTo(CanonicalId("egg"))
        assertThat(dictionary.lookup("たまご")?.canonicalId).isEqualTo(CanonicalId("egg"))
        assertThat(dictionary.canonicalNames[CanonicalId("milk")]).isEqualTo("牛乳")
    }

    @Test
    fun `既定の同義語（default）を使える`() {
        val problems = DictionaryValidator.validate(source(aliases = "alias,canonical_id,match,source\nたまご,egg,prefix,m\nぎゅうにゅう,milk,default,m"))
        assertThat(problems).isEmpty()
        assertThat(source(aliases = "alias,canonical_id,match,source\nお茶,milk,default,m").toDictionary().lookup("お茶")?.match)
            .isEqualTo(NormalizationDictionary.Match.DEFAULT)
    }

    @Test
    fun `属性の語を読み、種類と品目を検証する`() {
        val ok = source(attributes = "term,kind,value,source\n茶葉,form,茶葉,m\n特売なら,ignore,-,m\n卵焼き用,item,egg,m")
        assertThat(DictionaryValidator.validate(ok)).isEmpty()
        assertThat(ok.toDictionary().attributes["茶葉"]).isEqualTo(
            NormalizationDictionary.Attribute(NormalizationDictionary.AttributeKind.FORM, "茶葉"),
        )

        val bad = source(attributes = "term,kind,value,source\n茶葉,shape,茶葉,m\n麦茶,item,barley,m\n茶葉,form,茶葉,m")
        val problems = DictionaryValidator.validate(bad).joinToString()
        assertThat(problems).contains("属性の種類が不正")
        assertThat(problems).contains("属性の品目がない")
        assertThat(problems).contains("属性の語が重複")
    }
}
