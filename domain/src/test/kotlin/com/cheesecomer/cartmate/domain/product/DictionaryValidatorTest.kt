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
    ) = DictionarySource(version, items, aliases, cycleGroups, neverMerge, retiredIds)

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
}
