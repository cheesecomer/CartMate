package com.cheesecomer.cartmate.domain.product

import com.cheesecomer.cartmate.domain.product.NormalizationDictionary.AttributeKind
import com.cheesecomer.cartmate.domain.text.NameNormalizer

/** 名寄せに関係する属性（品目以外）。[kind] は "form"・"use"・"variant"・"volume" */
data class Qualifier(val kind: String, val value: String)

/** 1件の記録（商品名と備考）から取り出した属性 */
data class RecordAttributes(
    /** 商品名にある品目の語（「麦茶」） */
    val nameItems: Set<CanonicalId>,
    /** 備考にある品目の語 */
    val memoItems: Set<CanonicalId>,
    /** 形態・用途・種類・容量。判定に使わない語（「特売なら」）は除く */
    val qualifiers: Set<Qualifier>,
    /** 備考から取り出した属性があるか（品目か形態・用途・種類・容量）。あれば、その記録の備考に意味がある */
    val fromMemo: Boolean,
)

/**
 * 商品名と備考から、名寄せに関係する属性を取り出す（docs/design/suggestions.md「商品名と備考による判定」）。
 * 語は辞書の `attributes.csv`、容量（2L、500ml、1kg）は数字と単位の規則で読む。備考の全文を比べることはしない
 */
object AttributeExtractor {

    fun extract(name: String, memo: String?, dictionary: NormalizationDictionary): RecordAttributes {
        val fromName = scan(NameNormalizer.normalizedName(name), dictionary)
        val fromMemo = memo?.let { scan(NameNormalizer.normalizedName(it), dictionary) } ?: Scan.EMPTY
        return RecordAttributes(
            nameItems = fromName.items,
            memoItems = fromMemo.items,
            qualifiers = fromName.qualifiers + fromMemo.qualifiers,
            fromMemo = fromMemo.items.isNotEmpty() || fromMemo.qualifiers.isNotEmpty(),
        )
    }

    private data class Scan(val items: Set<CanonicalId>, val qualifiers: Set<Qualifier>) {
        companion object {
            val EMPTY = Scan(emptySet(), emptySet())
        }
    }

    /** 長い語から順に当て、当たった部分は消す（「ペットボトル」の中の「ペット」や、「特売なら」の中の「特売」を二重に数えない） */
    private fun scan(text: String, dictionary: NormalizationDictionary): Scan {
        var rest = text
        val items = HashSet<CanonicalId>()
        val qualifiers = HashSet<Qualifier>()
        for ((term, attribute) in dictionary.attributes.entries.sortedByDescending { it.key.length }) {
            if (term.isEmpty() || !rest.contains(term)) continue
            rest = rest.replace(term, " ")
            when (attribute.kind) {
                AttributeKind.ITEM -> items += CanonicalId(attribute.value)
                AttributeKind.FORM -> qualifiers += Qualifier("form", attribute.value)
                AttributeKind.USE -> qualifiers += Qualifier("use", attribute.value)
                AttributeKind.VARIANT -> qualifiers += Qualifier("variant", attribute.value)
                AttributeKind.IGNORE -> Unit
            }
        }
        for (match in VOLUME.findAll(rest)) {
            volume(match)?.let { qualifiers += it }
        }
        return Scan(items, qualifiers)
    }

    /** 数字と単位（NFKC と小文字にしたあと）。ml を l より、kg を g より先に当てる */
    private val VOLUME = Regex("""(\d+(?:\.\d+)?)\s*(ml|ミリリットル|l|リットル|kg|キロ|g|グラム)""")

    /** 容量を ml か g にそろえる（「2L」と「2リットル」と「2000ml」を同じにする） */
    private fun volume(match: MatchResult): Qualifier? {
        val amount = match.groupValues[1].toBigDecimalOrNull() ?: return null
        val (factor, unit) = when (match.groupValues[2]) {
            "ml", "ミリリットル" -> 1 to "ml"
            "l", "リットル" -> 1000 to "ml"
            "kg", "キロ" -> 1000 to "g"
            else -> 1 to "g"
        }
        return Qualifier("volume", amount.multiply(factor.toBigDecimal()).stripTrailingZeros().toPlainString() + unit)
    }
}
