package com.cheesecomer.cartmate.domain.product

import java.security.MessageDigest
import java.time.Duration
import java.time.Instant

/**
 * AI の名寄せの判定（docs/design/data-model.md「AI の名寄せ」）。関数が書き、家族で共有する。
 * [qualifiers] は判定した属性（種類ごとに、並べ替えた値）
 */
data class NameJudgement(
    val key: String,
    val normalizedName: String,
    val qualifiers: Map<String, List<String>>,
    val status: Status,
    val judgeVersion: String,
    val requestedAt: Instant?,
    val judgedAt: Instant? = null,
    val item: String? = null,
    val form: String? = null,
    val volume: String? = null,
    val use: String? = null,
    val variant: String? = null,
    val confidence: Double? = null,
    val reason: String? = null,
) {
    enum class Status { PENDING, DONE, FAILED }

    /** 今のバージョンの判定か。違えば使わない */
    val isCurrent: Boolean get() = judgeVersion == CURRENT_VERSION

    companion object {
        /** 関数の JUDGE_VERSION（firebase/functions/src/ai.ts）と同じ。モデルかプロンプトを変えたら両方を上げる */
        const val CURRENT_VERSION = "gpt-6-luna/2"

        /** pending のまま、これだけたったら問い合わせ直してよい（関数の PENDING_TIMEOUT_MS と同じ） */
        val PENDING_TIMEOUT: Duration = Duration.ofMinutes(5)

        /** failed から、これだけたったら問い合わせ直してよい（関数の RETRY_AFTER_FAILURE_MS と同じ） */
        val RETRY_AFTER_FAILURE: Duration = Duration.ofDays(1)
    }
}

/**
 * 判定のキー（docs/design/data-model.md「判定のキー」）。関数の judgementKey と同じ計算で、
 * firebase/functions/test/judgement-keys.json の組を両方のテストで確かめる
 */
object JudgementKey {

    fun of(normalizedName: String, qualifiers: Map<String, List<String>>): String {
        val lines = qualifiers.flatMap { (kind, values) -> values.map { "$kind=$it" } }.sorted()
        val canonical = (listOf("v1", normalizedName) + lines).joinToString("\n")
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(32)
    }

    /** 規則で読み取った属性を、判定に送る形（種類ごとに並べ替えた値）にする */
    fun qualifiersOf(qualifiers: Set<Qualifier>): Map<String, List<String>> =
        qualifiers.groupBy({ it.kind }, { it.value }).mapValues { (_, values) -> values.distinct().sorted() }.toSortedMap()
}
