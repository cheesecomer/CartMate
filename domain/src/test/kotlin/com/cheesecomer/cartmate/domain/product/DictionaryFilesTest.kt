package com.cheesecomer.cartmate.domain.product

import com.cheesecomer.cartmate.domain.text.NameNormalizer
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.io.File

/** リポジトリの dictionary/ の元データを検証する（dictionary/README.md） */
class DictionaryFilesTest {

    private val dir = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "dictionary") }
        .first { File(it, "items.csv").exists() }

    private fun read(name: String) = File(dir, name).readText()

    private val source = DictionarySource(
        version = read("VERSION"),
        items = read("items.csv"),
        aliases = read("aliases.csv"),
        cycleGroups = read("cycle_groups.csv"),
        neverMerge = read("never_merge.csv"),
        retiredIds = read("retired_ids.csv"),
        attributes = read("attributes.csv"),
    )

    @Test
    fun `同梱する辞書の元データに問題がない`() {
        assertThat(DictionaryValidator.validate(source)).isEmpty()
    }

    @Test
    fun `同梱する辞書で、代表的な表記ゆれがまとまる`() {
        val dictionary = source.toDictionary()
        fun canonical(name: String) = dictionary.lookup(NameNormalizer.normalizedName(name))?.canonicalId
        assertThat(canonical("たまご")).isEqualTo(canonical("卵"))
        assertThat(canonical("胡麻油")).isEqualTo(canonical("ごま油"))
        assertThat(canonical("食パン6枚切り")).isNotEqualTo(canonical("食パン8枚切り"))
        assertThat(dictionary.cycleGroups[canonical("食パン6枚切り")]).isEqualTo(dictionary.cycleGroups[canonical("食パン8枚切り")])
    }

    /**
     * 名前だけで判定できる正例・負例（dictionary/tests/cases.csv）。備考を使う例はフェーズ4で足す。
     * - equivalent: 同じ品目になる
     * - cycle_group: 違う品目で、同じ周期のグループ
     * - never_merge: 統合禁止の組に入っている
     * - distinct: 同じ品目にならない（自動ではまとめない）
     * - not_prefix: 左の別名は前方一致に使わない（短い読みの衝突）
     */
    @Test
    fun `名寄せの正例・負例がすべて期待どおり`() {
        val dictionary = source.toDictionary()
        fun alias(name: String) = dictionary.lookup(NameNormalizer.normalizedName(name))
        // 既定の同義語（お茶）は、備考を使う判定ができるまでまとめないので、確定の名寄せには使わない
        fun canonical(name: String) = alias(name)?.takeIf { it.match != NormalizationDictionary.Match.DEFAULT }?.canonicalId
        val failures = DictionarySource.rows(read("tests/cases.csv"), 3).mapNotNull { (expect, left, right) ->
            val a = canonical(left)
            val b = canonical(right)
            val ok = when (expect) {
                "equivalent" -> a != null && a == b
                "cycle_group" -> a != null && b != null && a != b && dictionary.cycleGroups[a] != null &&
                    dictionary.cycleGroups[a] == dictionary.cycleGroups[b]
                "never_merge" -> a != null && b != null && !dictionary.mayMerge(a, b)
                "distinct" -> a == null || a != b
                "not_prefix" -> alias(left)?.match != NormalizationDictionary.Match.PREFIX
                else -> false
            }
            "$expect $left / $right → $a / $b".takeUnless { ok }
        }
        assertThat(failures).isEmpty()
    }
}
