package com.cheesecomer.cartmate.domain.product

import com.cheesecomer.cartmate.domain.text.NameNormalizer

/**
 * 名寄せ辞書の元データの検証（docs/design/suggestions.md「名寄せ辞書」）。問題の一覧を返す（空なら問題なし）
 */
object DictionaryValidator {

    /** 前方一致に使うと誤爆しやすい、短いひらがな・カタカナの別名の長さ */
    const val SHORT_KANA_LENGTH = 2

    fun validate(source: DictionarySource): List<String> {
        val problems = mutableListOf<String>()
        if (source.version.isBlank()) problems += "版（VERSION）がない"

        val items = source.itemRows()
        val ids = items.map { it.id }
        ids.groupingBy { it }.eachCount().filter { it.value > 1 }.keys.forEach { problems += "品目の ID が重複: $it" }
        items.filter { it.id.isBlank() || it.name.isBlank() || it.source.isBlank() }.forEach { problems += "品目の列が足りない: $it" }
        val known = ids.toSet()

        val retired = source.retiredIdList().toSet()
        (known intersect retired).forEach { problems += "使わなくなった ID を再利用している: $it" }

        // 品目の名前と別名を、正規化したキーでまとめて衝突を調べる
        val keys = mutableMapOf<String, MutableSet<String>>()
        fun register(name: String, id: String) {
            keys.getOrPut(NameNormalizer.normalizedName(name)) { mutableSetOf() } += id
        }
        items.forEach { register(it.name, it.id) }
        for (row in source.aliasRows()) {
            if (row.alias.isBlank() || row.source.isBlank()) problems += "別名の列が足りない: $row"
            if (row.id !in known) problems += "別名の品目がない: ${row.alias} → ${row.id}"
            if (row.match !in MATCHES) {
                problems += "照合の条件が exact・prefix・default のどれでもない: ${row.alias} (${row.match})"
            }
            val key = NameNormalizer.normalizedName(row.alias)
            if (row.match == DictionarySource.MATCH_PREFIX && key.length <= SHORT_KANA_LENGTH && key.all(::isKana)) {
                problems += "短いかなの別名は exact に限る: ${row.alias}"
            }
            register(row.alias, row.id)
        }
        keys.filter { it.value.size > 1 }.forEach { (key, owners) -> problems += "正規化すると同じ名前が複数の品目にある: $key → $owners" }

        for (row in source.cycleRows()) {
            if (row.id !in known) problems += "周期のグループの品目がない: ${row.groupId} → ${row.id}"
            if (row.groupId in known) problems += "周期のグループの ID が品目の ID と同じ: ${row.groupId}"
        }
        for (row in source.neverMergeRows()) {
            if (row.left !in known || row.right !in known) problems += "統合禁止の品目がない: ${row.left} / ${row.right}"
            if (row.left == row.right) problems += "統合禁止の組が同じ品目: ${row.left}"
            if (row.reason.isBlank()) problems += "統合禁止の理由がない: ${row.left} / ${row.right}"
        }
        val attributeTerms = mutableSetOf<String>()
        for (row in source.attributeRows()) {
            if (row.term.isBlank() || row.value.isBlank() || row.source.isBlank()) problems += "属性の列が足りない: $row"
            val kind = DictionarySource.ATTRIBUTE_KINDS[row.kind]
            if (kind == null) problems += "属性の種類が不正: ${row.term} (${row.kind})"
            if (kind == NormalizationDictionary.AttributeKind.ITEM && row.value !in known) problems += "属性の品目がない: ${row.term} → ${row.value}"
            if (!attributeTerms.add(NameNormalizer.normalizedName(row.term))) problems += "属性の語が重複: ${row.term}"
        }
        return problems
    }

    private val MATCHES = setOf(DictionarySource.MATCH_EXACT, DictionarySource.MATCH_PREFIX, DictionarySource.MATCH_DEFAULT)

    private fun isKana(c: Char): Boolean = c in 'ぁ'..'ゖ' || c in 'ァ'..'ヺ' || c == 'ー'
}
