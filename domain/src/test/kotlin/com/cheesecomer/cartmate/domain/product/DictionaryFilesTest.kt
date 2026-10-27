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
}
