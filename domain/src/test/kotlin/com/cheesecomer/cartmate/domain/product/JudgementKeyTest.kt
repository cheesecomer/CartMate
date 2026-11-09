package com.cheesecomer.cartmate.domain.product

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import java.io.File

/** 判定のキーと判定のバージョンが、関数（firebase/functions）と同じであること（docs/design/data-model.md「判定のキー」） */
class JudgementKeyTest {

    private val functions = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "firebase/functions") }
        .first { File(it, "package.json").exists() }

    @Test
    fun `関数と同じ組で、同じキーになる`() {
        val vectors = Json.parseToJsonElement(File(functions, "test/judgement-keys.json").readText()).jsonArray
        assertThat(vectors).isNotEmpty()
        for (vector in vectors.map { it.jsonObject }) {
            val name = vector.getValue("normalizedName").jsonPrimitive.content
            val qualifiers = vector.getValue("qualifiers").jsonObject
                .mapValues { (_, values) -> values.jsonArray.map { it.jsonPrimitive.content } }
            assertThat(JudgementKey.of(name, qualifiers)).isEqualTo(vector.getValue("key").jsonPrimitive.content)
        }
    }

    @Test
    fun `属性の並びと、種類の並びによらず同じキーになる`() {
        val a = JudgementKey.of("ゴミ袋", mapOf("volume" to listOf("45000ml", "30000ml"), "use" to listOf("燃えるゴミ")))
        val b = JudgementKey.of("ゴミ袋", mapOf("use" to listOf("燃えるゴミ"), "volume" to listOf("30000ml", "45000ml")))
        assertThat(a).isEqualTo(b)
        assertThat(a).hasLength(32)
        assertThat(JudgementKey.of("ゴミ袋", emptyMap())).isNotEqualTo(a)
    }

    @Test
    fun `規則で読み取った属性を、種類ごとに並べ替えた値にする`() {
        val qualifiers = setOf(Qualifier("volume", "45000ml"), Qualifier("use", "燃えるゴミ"), Qualifier("volume", "30000ml"))
        assertThat(JudgementKey.qualifiersOf(qualifiers))
            .containsExactly("use", listOf("燃えるゴミ"), "volume", listOf("30000ml", "45000ml"))
    }

    @Test
    fun `判定のバージョンと、問い合わせ直すまでの時間が関数と同じ`() {
        val ai = File(functions, "src/ai.ts").readText()
        assertThat(ai).contains("""export const JUDGE_VERSION = "${NameJudgement.CURRENT_VERSION}";""")
        val judge = File(functions, "src/judge.ts").readText()
        assertThat(judge).contains("PENDING_TIMEOUT_MS = ${NameJudgement.PENDING_TIMEOUT.toMinutes()} * 60 * 1000;")
        assertThat(judge).contains("RETRY_AFTER_FAILURE_MS = ${NameJudgement.RETRY_AFTER_FAILURE.toHours()} * 60 * 60 * 1000;")
    }
}
