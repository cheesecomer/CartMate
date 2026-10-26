package com.cheesecomer.cartmate.domain.product

/** 辞書の品目の ID。一度使った ID は再利用しない */
@JvmInline
value class CanonicalId(val value: String)

/** 周期のグループの ID。品目とは別の名前空間 */
@JvmInline
value class CycleGroupId(val value: String)

/**
 * アプリに同梱する名寄せ辞書（docs/design/suggestions.md「名寄せ辞書」）。照合は端末の中だけで行う
 */
data class NormalizationDictionary(
    val version: String,
    /** 品目の表示名 */
    val canonicalNames: Map<CanonicalId, String>,
    /** 別名。キーは NameNormalizer.normalizedName で正規化した名前 */
    val aliases: Map<String, Alias>,
    /** 周期だけ合わせて数えるグループ */
    val cycleGroups: Map<CanonicalId, CycleGroupId> = emptyMap(),
    /** 自動では同じ商品にしない品目の組（順不同） */
    val neverMerge: Set<Set<CanonicalId>> = emptySet(),
) {
    data class Alias(val canonicalId: CanonicalId, val match: Match)

    enum class Match {
        /** 完全一致だけ */
        EXACT,

        /** 補完の前方一致にも使う */
        PREFIX,
    }

    /** [normalizedName] に完全一致する別名 */
    fun lookup(normalizedName: String): Alias? = aliases[normalizedName]

    /** [a] と [b] を自動で同じ商品にしてよいか */
    fun mayMerge(a: CanonicalId, b: CanonicalId): Boolean = a == b || setOf(a, b) !in neverMerge

    companion object {
        val EMPTY = NormalizationDictionary(version = "empty", canonicalNames = emptyMap(), aliases = emptyMap())
    }
}
