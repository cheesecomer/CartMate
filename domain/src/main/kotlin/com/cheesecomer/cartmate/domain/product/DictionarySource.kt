package com.cheesecomer.cartmate.domain.product

import com.cheesecomer.cartmate.domain.text.NameNormalizer

/**
 * 名寄せ辞書の元データ（リポジトリの dictionary/。docs/design/suggestions.md「名寄せ辞書」）。
 * CSV はカンマを含まない値だけを使い、1行目は見出し
 */
data class DictionarySource(
    val version: String,
    /** canonical_id,name,source */
    val items: String,
    /** alias,canonical_id,match,source（match は exact か prefix） */
    val aliases: String,
    /** cycle_group_id,canonical_id */
    val cycleGroups: String,
    /** left_id,right_id,reason */
    val neverMerge: String,
    /** canonical_id,retired_in,reason（使わなくなった ID。再利用しない） */
    val retiredIds: String,
) {
    data class ItemRow(val id: String, val name: String, val source: String)
    data class AliasRow(val alias: String, val id: String, val match: String, val source: String)
    data class CycleRow(val groupId: String, val id: String)
    data class NeverMergeRow(val left: String, val right: String, val reason: String)

    fun itemRows(): List<ItemRow> = rows(items, 3).map { ItemRow(it[0], it[1], it[2]) }
    fun aliasRows(): List<AliasRow> = rows(aliases, 4).map { AliasRow(it[0], it[1], it[2], it[3]) }
    fun cycleRows(): List<CycleRow> = rows(cycleGroups, 2).map { CycleRow(it[0], it[1]) }
    fun neverMergeRows(): List<NeverMergeRow> = rows(neverMerge, 3).map { NeverMergeRow(it[0], it[1], it[2]) }
    fun retiredIdList(): List<String> = rows(retiredIds, 3).map { it[0] }

    /**
     * 辞書を作る。品目の名前は、それ自体も別名（前方一致にも使う）として登録する。別名は NameNormalizer.normalizedName で正規化して照合する。
     * 元データの誤りは [DictionaryValidator] で検出する前提で、ここでは後の行を優先する
     */
    fun toDictionary(): NormalizationDictionary {
        val items = itemRows()
        val aliases = LinkedHashMap<String, NormalizationDictionary.Alias>()
        for (item in items) {
            aliases[NameNormalizer.normalizedName(item.name)] = NormalizationDictionary.Alias(CanonicalId(item.id), NormalizationDictionary.Match.PREFIX)
        }
        for (row in aliasRows()) {
            val match = if (row.match == MATCH_EXACT) NormalizationDictionary.Match.EXACT else NormalizationDictionary.Match.PREFIX
            aliases[NameNormalizer.normalizedName(row.alias)] = NormalizationDictionary.Alias(CanonicalId(row.id), match)
        }
        return NormalizationDictionary(
            version = version.trim(),
            canonicalNames = items.associate { CanonicalId(it.id) to it.name },
            aliases = aliases,
            cycleGroups = cycleRows().associate { CanonicalId(it.id) to CycleGroupId(it.groupId) },
            neverMerge = neverMergeRows().mapTo(HashSet()) { setOf(CanonicalId(it.left), CanonicalId(it.right)) },
        )
    }

    companion object {
        const val MATCH_EXACT = "exact"
        const val MATCH_PREFIX = "prefix"

        /** 見出しを除いた行。空行は飛ばし、列の数が違う行もそのまま返す（検証で見つける） */
        internal fun rows(csv: String, columns: Int): List<List<String>> =
            csv.lineSequence().drop(1).map { it.trimEnd('\r') }.filter { it.isNotBlank() }
                .map { line -> line.split(',').map(String::trim).let { if (it.size < columns) it + List(columns - it.size) { "" } else it } }
                .toList()
    }
}
