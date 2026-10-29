package com.cheesecomer.cartmate.domain.product

import com.cheesecomer.cartmate.domain.model.Product
import com.cheesecomer.cartmate.domain.model.ProductId
import com.cheesecomer.cartmate.domain.text.ChoiceName
import com.cheesecomer.cartmate.domain.text.NameNormalizer

/**
 * 商品詳細入力画面の表記ゆれのチップ（docs/design/suggestions.md「表記ゆれの紐付け」）。
 * 入力した名前が新しい商品で、検索用のキーが前方一致・部分一致する既存の商品があれば、「『牛乳』と同じ商品として扱う」の候補にする。
 * 同じくらい近い候補が別々の商品で複数あれば（「コーヒー豆」に対する「コーヒー豆（深煎り）」と「コーヒー豆（中煎り）」）、
 * どちらにまとめるか決められないので候補を出さない
 */
object MergeCandidates {

    /** 判定に使う検索用のキーの最小の長さ。1文字だと、ほかの名前の一部に当たりやすい（「す」と「れたす」） */
    const val MIN_KEY_LENGTH = 2

    /**
     * [name] の候補。統合先（[ProductGraph] でたどった先）を返す。候補がなければ null。
     * [visible] は自分に見える項目か購入の記録がある商品（他のメンバーがプライベートでだけ使った名前を出さない）
     */
    fun find(name: String, visible: Collection<Product>, all: Collection<Product>, dictionary: NormalizationDictionary): Product? {
        val id = ProductId(NameNormalizer.productId(name))
        // 同じ名前の商品がすでにあれば、新しい商品ではない
        if (all.any { it.id == id }) return null
        // 選択肢を含む名前（「カブもしくは大根」）は、どちらを買うか決まっていないので、ほかの商品と同じにしない
        if (ChoiceName.hasChoice(name)) return null
        val key = NameNormalizer.searchKey(name)
        if (key.length < MIN_KEY_LENGTH) return null
        val inputCanonical = dictionary.lookup(NameNormalizer.normalizedName(name))?.canonicalId
        val graph = ProductGraph(all)
        val byId = all.associateBy { it.id }
        // 一致の度合い（前方一致が先）、キーの短い順で近さを決める。同じ近さの候補は、統合先が1つに決まるときだけ出す
        val nearest = visible
            .asSequence()
            .filter { it.searchKey.length >= MIN_KEY_LENGTH && !ChoiceName.hasChoice(it.displayName) }
            .mapNotNull { product -> rank(key, product.searchKey)?.let { product to (it to product.searchKey.length) } }
            .filter { (product, _) ->
                val canonical = dictionary.lookup(product.normalizedName)?.canonicalId
                inputCanonical == null || canonical == null || dictionary.mayMerge(inputCanonical, canonical)
            }
            .groupBy({ it.second }, { it.first })
            .minWithOrNull(compareBy({ it.key.first }, { it.key.second }))
            ?.value
            ?: return null
        return nearest.map { byId[graph.resolve(it.id)] ?: it }.distinctBy { it.id }.singleOrNull()
    }

    /** 0: 前方一致、1: 部分一致。当たらなければ null */
    private fun rank(input: String, existing: String): Int? = when {
        input.startsWith(existing) || existing.startsWith(input) -> 0
        input.contains(existing) || existing.contains(input) -> 1
        else -> null
    }
}
