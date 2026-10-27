package com.cheesecomer.cartmate.domain.suggest

import com.cheesecomer.cartmate.domain.text.NameNormalizer

/**
 * リストの類似度（docs/design/suggestions.md「リスト類似度」）。リストで買った商品の集合（プロファイル）を比べ、
 * そのリストで提示する候補を選ぶ。周期の計算には全リストの履歴を使う
 */
object ListSimilarity {

    /** 同じ名前のリストを同じものとして扱うためのキー */
    fun listKey(listName: String): String = NameNormalizer.normalizedName(listName)

    /** リストのキーごとに、そこで買った商品（統合先で集計したキー）の集合 */
    fun <K> buildProfiles(purchases: Collection<Pair<String, K>>): Map<String, Set<K>> =
        purchases.groupBy({ listKey(it.first) }, { it.second }).mapValues { (_, keys) -> keys.toSet() }

    /** 重複係数 |A∩B| / min(|A|,|B|)。件数が少なくて判定できなければ null（関連付けない） */
    fun <K> overlap(a: Set<K>, b: Set<K>, params: SuggestionParams = SuggestionParams()): Double? {
        if (a.size < params.minProfileSize || b.size < params.minProfileSize) return null
        val intersection = a.count { it in b }
        if (intersection < params.minIntersection) return null
        return intersection.toDouble() / minOf(a.size, b.size)
    }

    /**
     * [target] のリストでの、商品ごとの関連度。同じ名前のリストで買った商品は 1.0、類似度が [SuggestionParams.minOverlap] 以上の
     * リストで買った商品は、その類似度（複数あれば最大）。どれにも当たらない商品は入らない
     */
    fun <K> relevance(target: String, profiles: Map<String, Set<K>>, params: SuggestionParams = SuggestionParams()): Map<K, Double> {
        val own = profiles[target].orEmpty()
        val result = HashMap<K, Double>()
        for ((key, products) in profiles) {
            if (key == target) continue
            val similarity = overlap(own, products, params)?.takeIf { it >= params.minOverlap } ?: continue
            for (product in products) result.merge(product, similarity, ::maxOf)
        }
        for (product in own) result[product] = 1.0
        return result
    }
}
