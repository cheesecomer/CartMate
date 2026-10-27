package com.cheesecomer.cartmate.domain.product

import com.cheesecomer.cartmate.domain.model.Product
import com.cheesecomer.cartmate.domain.model.ProductId

/**
 * 「商品名の管理」と「商品をまとめる」（docs/requirements.md §3.5「商品名の管理」）。
 * 統合先（残す商品）を中心に、まとめている商品と辞書の名寄せを分けて示し、統合と解除で書き込む内容を決める
 */
object ProductMerge {

    /**
     * 一度にまとめられる商品の数。ルールが1回の書き込みで読めるドキュメントは20件まで（メンバーの確認と統合先で2件、
     * ほかの商品をまとめている商品を選ぶと1件ずつ）なので、どの選び方でも収まる数にする（rules-test で確かめている）
     */
    const val MAX_SELECTION = 18

    /** 未統合の商品（統合先）と、それにまとめている商品 */
    data class Entry(
        val product: Product,
        /** 手動でまとめている商品（多段の統合もたどる）。自分に見えるものだけを、名前の順に */
        val members: List<Product>,
        /** 辞書の名寄せ。辞書でまとめていなければ null */
        val dictionary: DictionaryGroup?,
    )

    /** 辞書でまとめている品目と、そこにまとまっている自分に見える名前（統合先の名前） */
    data class DictionaryGroup(val canonicalName: String, val names: List<String>) {
        /** 表示する意味があるか。辞書の名前と同じ名前の商品が1つだけなら、何もまとめていないので出さない */
        val isMeaningful: Boolean get() = names.size >= 2 || names.singleOrNull() != canonicalName
    }

    /** 商品を開いたときの画面 */
    sealed interface Detail {
        /** 未統合の商品。まとめている商品、辞書の名寄せ、「商品をまとめる」 */
        data class Target(val entry: Entry) : Detail

        /** 統合されている商品。今の統合先と「解除」 */
        data class Merged(val product: Product, val target: Product) : Detail
    }

    /** 統合と解除で、1つのバッチに書き込む変更（docs/design/data-model.md「統合」） */
    sealed interface Change {
        val id: ProductId

        /** 未統合の商品を統合する。最初に統合した人と時刻を書く */
        data class Merge(override val id: ProductId, val into: ProductId) : Change

        /** 統合先を付け替える。最初に統合した人と時刻は変えない */
        data class Repoint(override val id: ProductId, val into: ProductId) : Change

        /** 統合を解除する */
        data class Unmerge(override val id: ProductId) : Change
    }

    /** 「商品名の管理」の一覧。自分に見える商品があるものだけを、名前の順に並べる */
    fun entries(products: Collection<Product>, visibleIds: Set<ProductId>, dictionary: NormalizationDictionary): List<Entry> {
        val graph = ProductGraph(products)
        val byId = products.associateBy { it.id }
        // 統合先がない（読めていない）商品は、自分を統合先として扱う
        fun rootOf(product: Product): ProductId = graph.resolve(product.id).takeIf { it in byId } ?: product.id
        val membersByRoot = products.filter { it.id in visibleIds && rootOf(it) != it.id }.groupBy(::rootOf)
        val roots = products.filter { rootOf(it) == it.id && (it.id in visibleIds || it.id in membersByRoot) }

        val resolver = ProductResolver(products, dictionary)
        val canonicalOf = roots.associate { root ->
            root.id to (resolver.resolve(root.id).reason as? ProductResolver.Reason.Dictionary)?.canonicalId
        }
        val namesByCanonical = roots.filter { canonicalOf[it.id] != null }
            .groupBy({ canonicalOf.getValue(it.id)!! }, { it })
            .mapValues { (_, products) -> products.sortedBy { it.searchKey }.map { it.displayName } }

        return roots.map { root ->
            Entry(
                product = root,
                members = membersByRoot[root.id].orEmpty().sortedBy { it.searchKey },
                dictionary = canonicalOf[root.id]?.let { canonical ->
                    DictionaryGroup(dictionary.canonicalNames[canonical] ?: root.displayName, namesByCanonical.getValue(canonical))
                },
            )
        }.sortedBy { it.product.searchKey }
    }

    /** [id] の商品を開いたときの画面。見えない商品や、ない商品は null */
    fun detail(id: ProductId, products: Collection<Product>, visibleIds: Set<ProductId>, dictionary: NormalizationDictionary): Detail? {
        val byId = products.associateBy { it.id }
        val product = byId[id] ?: return null
        val root = byId[ProductGraph(products).resolve(id)]
        if (root != null && root.id != id) return Detail.Merged(product, root).takeIf { id in visibleIds }
        return entries(products, visibleIds, dictionary).find { it.product.id == id }?.let(Detail::Target)
    }

    /** [target] にまとめられる商品。ほかの未統合の商品（すでに [target] にまとまっている商品は、統合済みなので入らない） */
    fun candidates(
        target: ProductId,
        products: Collection<Product>,
        visibleIds: Set<ProductId>,
        dictionary: NormalizationDictionary,
    ): List<Entry> = entries(products, visibleIds, dictionary).filter { it.product.id != target }

    /**
     * [selected] を [target] にまとめる変更。選んだ商品にまとまっている商品は、同じ統合先に付け替えて1段にする。
     * 統合先が統合済み、選んだ商品が統合済みか統合先そのもの、選んだ数が 0 か [MAX_SELECTION] を超える場合は null
     */
    fun mergeChanges(target: ProductId, selected: Collection<ProductId>, products: Collection<Product>): List<Change>? {
        val byId = products.associateBy { it.id }
        val ids = selected.toSet()
        if (ids.isEmpty() || ids.size > MAX_SELECTION) return null
        if (byId[target]?.mergedInto != null || target !in byId || target in ids) return null
        if (ids.any { byId[it] == null || byId.getValue(it).mergedInto != null }) return null
        return ids.flatMap { id ->
            listOf(Change.Merge(id, target)) +
                products.filter { it.mergedInto == id }.map { Change.Repoint(it.id, target) }
        }
    }

    /**
     * [id] の統合を解除する変更。[id] に直接まとまっている商品は、[id] の元の統合先に付け替えて、統合先に残す
     * （画面で1件を解除したら、その1件だけが外れる）。統合していなければ null
     */
    fun unmergeChanges(id: ProductId, products: Collection<Product>): List<Change>? {
        val parent = products.find { it.id == id }?.mergedInto ?: return null
        return listOf(Change.Unmerge(id)) +
            products.filter { it.mergedInto == id }.map { Change.Repoint(it.id, parent) }
    }
}
