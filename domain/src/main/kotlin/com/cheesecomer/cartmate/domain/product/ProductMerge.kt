package com.cheesecomer.cartmate.domain.product

import com.cheesecomer.cartmate.domain.model.Product
import com.cheesecomer.cartmate.domain.model.ProductId
import com.cheesecomer.cartmate.domain.text.NameNormalizer

/**
 * 「商品名の管理」と「商品をまとめる」（docs/requirements.md §3.5「商品名の管理」）。
 * 統合先（残す商品）を中心に、まとめている商品と辞書の名寄せを分けて示し、統合と解除で書き込む内容を決める
 */
object ProductMerge {

    /**
     * 1つのバッチ（1回の書き込み）で、ルールが読む商品の数の上限。ルールが1回の書き込みで読めるドキュメントは20件まで
     * （メンバーの確認と統合先で2件、ほかの商品をまとめている商品の統合や解除で1件ずつ）なので、18件に収める
     * （rules-test で確かめている）。「商品をまとめる」では、超える分を次のバッチに分けて送るので、画面には上限がない
     */
    const val MAX_READS_PER_BATCH = 18

    /** 1つのバッチに入れる書き込みの数の上限（Firestore のバッチは500件まで。余裕を持たせる） */
    private const val MAX_WRITES_PER_BATCH = 400

    /** サジェストへの出し方（docs/requirements.md §4.3） */
    enum class SuggestionMode {
        /** 通常どおり表示 */
        NORMAL,

        /** 一時的に非表示（再購入すると候補に戻る） */
        TEMPORARILY_HIDDEN,

        /** 常に非表示（再購入しても候補に戻らない） */
        ALWAYS_HIDDEN,
    }

    /** 未統合の商品（統合先）と、それにまとめている商品 */
    data class Entry(
        val product: Product,
        /** 手動でまとめている商品（多段の統合もたどる）。自分に見えるものだけを、名前の順に */
        val members: List<Product>,
        /** 辞書の名寄せ。辞書でまとめていなければ null */
        val dictionary: DictionaryGroup?,
        val mode: SuggestionMode = SuggestionMode.NORMAL,
    )

    /** 辞書でまとめている品目と、そこにまとまっている自分に見える名前（統合先の名前） */
    data class DictionaryGroup(val canonicalName: String, val names: List<String>) {
        /** 表示する意味があるか。辞書の名前と同じ名前の商品が1つだけなら、何もまとめていないので出さない */
        val isMeaningful: Boolean get() = names.size >= 2 || names.singleOrNull() != canonicalName
    }

    /** 商品を開いたときの画面 */
    sealed interface Detail {
        /** 未統合の商品。まとめている商品、辞書の名寄せ、「商品をまとめる」、サジェストへの出し方（非表示の商品も同じ画面） */
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

        /**
         * [name] の商品を作る（名前の訂正で、正しい名前の商品がまだないとき）。ID は名前から決まる。
         * [alwaysHidden] は訂正する前の商品の「常に非表示」を引き継ぐ（訂正で勝手に解除しない）
         */
        data class Create(override val id: ProductId, val name: String, val alwaysHidden: Boolean = false) : Change

        /** 非表示にする */
        data class Hide(override val id: ProductId) : Change

        /** また表示する。非表示にする前の記録も、また予測に使う */
        data class Show(override val id: ProductId) : Change
    }

    /**
     * 「商品名の管理」の一覧。自分に見える商品があるものだけを、名前の順に並べる。
     * [hidden]（[ProductHiding.hiddenRoots]）の商品は出さない（[hiddenEntries] に出す）
     */
    fun entries(
        products: Collection<Product>,
        visibleIds: Set<ProductId>,
        dictionary: NormalizationDictionary,
        hidden: Set<ProductId> = emptySet(),
    ): List<Entry> = allEntries(products, visibleIds, dictionary, hidden).filter { it.mode == SuggestionMode.NORMAL }

    /** 一時的に非表示か常に非表示の統合先。「非表示の商品」に、名前の順に並べる */
    fun hiddenEntries(
        products: Collection<Product>,
        visibleIds: Set<ProductId>,
        dictionary: NormalizationDictionary,
        hidden: Set<ProductId>,
    ): List<Entry> = allEntries(products, visibleIds, dictionary, hidden).filter { it.mode != SuggestionMode.NORMAL }

    /** [product]（統合先）の出し方。常に非表示は、一時的な非表示より優先する */
    fun modeOf(product: Product, hidden: Set<ProductId>): SuggestionMode = when {
        product.suggestionExcluded -> SuggestionMode.ALWAYS_HIDDEN
        product.id in hidden -> SuggestionMode.TEMPORARILY_HIDDEN
        else -> SuggestionMode.NORMAL
    }

    private fun allEntries(
        products: Collection<Product>,
        visibleIds: Set<ProductId>,
        dictionary: NormalizationDictionary,
        hidden: Set<ProductId> = emptySet(),
    ): List<Entry> {
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
                mode = modeOf(root, hidden),
            )
        }.sortedBy { it.product.searchKey }
    }

    /**
     * 検索欄の [query] に一致するか。商品名か、まとめている商品の名前に、読みをそろえた検索キー（カタカナとひらがなを区別しない）で
     * 含まれていれば一致する。空なら、すべて一致する
     */
    fun Entry.matches(query: String): Boolean {
        val key = NameNormalizer.searchKey(query).takeIf { query.isNotBlank() } ?: return true
        return product.searchKey.contains(key) || members.any { it.searchKey.contains(key) }
    }

    /** [id] の商品を開いたときの画面。見えない商品や、ない商品は null */
    fun detail(
        id: ProductId,
        products: Collection<Product>,
        visibleIds: Set<ProductId>,
        dictionary: NormalizationDictionary,
        hidden: Set<ProductId> = emptySet(),
    ): Detail? {
        val byId = products.associateBy { it.id }
        val product = byId[id] ?: return null
        val root = byId[ProductGraph(products).resolve(id)]
        if (root != null && root.id != id) return Detail.Merged(product, root).takeIf { id in visibleIds }
        val entry = allEntries(products, visibleIds, dictionary, hidden).find { it.product.id == id } ?: return null
        // 一時的に非表示・常に非表示の商品も、同じ統合先の画面で出し方を選び直す
        return Detail.Target(entry)
    }

    /**
     * [target] にまとめられる商品。ほかの未統合の商品（すでに [target] にまとまっている商品は、統合済みなので入らない）。
     * 一時的に非表示・常に非表示の商品は出さない
     */
    fun candidates(
        target: ProductId,
        products: Collection<Product>,
        visibleIds: Set<ProductId>,
        dictionary: NormalizationDictionary,
        hidden: Set<ProductId> = emptySet(),
    ): List<Entry> = entries(products, visibleIds, dictionary, hidden).filter { it.product.id != target }

    /**
     * [selected] を [target] にまとめる変更（1つのバッチ）。選んだ商品にまとまっている商品は、同じ統合先に付け替えて1段にする。
     * 統合先が統合済み、選んだ商品が統合済みか統合先そのもの、選んだ数が 0 か [MAX_READS_PER_BATCH] を超える場合は null
     */
    fun mergeChanges(target: ProductId, selected: Collection<ProductId>, products: Collection<Product>): List<Change>? {
        val byId = products.associateBy { it.id }
        val ids = selected.toSet()
        if (ids.isEmpty() || ids.size > MAX_READS_PER_BATCH) return null
        if (byId[target]?.mergedInto != null || target !in byId || target in ids) return null
        if (ids.any { byId[it] == null || byId.getValue(it).mergedInto != null }) return null
        return ids.flatMap { id ->
            listOf(Change.Merge(id, target)) +
                products.filter { it.mergedInto == id }.map { Change.Repoint(it.id, target) }
        }
    }

    /**
     * 「商品をまとめる」で、[target] にまとめる商品を編集する変更。[add]（未統合の商品）をまとめ、[remove]（今 [target] に
     * まとまっている商品）の統合を解除する。件数に上限はなく、ルールが1回の書き込みで読める数に収まるようにバッチに分けて返す。
     * バッチは順に送る（前のバッチの結果を、次のバッチのルールが読む）。
     *
     * - まとめる商品にまとまっている商品は、同じ統合先に付け替えて1段にする（[mergeChanges] と同じ）
     * - 解除する商品にまとまっている商品は、解除する商品の元の統合先に付け替えて、統合先に残す（[unmergeChanges] と同じ）。
     *   多段の統合で上下の両方を解除するときは、下から順に解除する
     * - 同じ商品を書くか読む変更は、同じバッチに入れない（ルールは、バッチの前の状態をもとに付け替えを確かめるため）
     *
     * 統合先がない・統合済み、まとめる商品が未統合でないか統合先そのものなら null。
     * 解除する商品が、ほかの端末の変更ですでに [target] から外れていれば、その商品は何もしない。変更がなければ空
     */
    fun editChanges(target: ProductId, add: Set<ProductId>, remove: Set<ProductId>, products: Collection<Product>): List<List<Change>>? {
        val state = products.associateBy { it.id }.toMutableMap()
        if (state[target]?.mergedInto != null || target !in state) return null
        if (add.any { it == target || state[it] == null || state.getValue(it).mergedInto != null }) return null

        // 1件の統合か解除と、それに伴う付け替え。書き込む商品と、ルールが読む商品（統合先を除く）
        class Group(val changes: List<Change>, val writes: Set<ProductId>, val reads: Set<ProductId>)
        val groups = mutableListOf<Group>()
        fun childrenOf(id: ProductId) = state.values.filter { it.mergedInto == id }.map { it.id }
        fun depthOf(id: ProductId): Int = generateSequence(state[id]?.mergedInto) { state[it]?.mergedInto }.count()

        val graph = ProductGraph(products)
        for (id in remove.filter { it != target && graph.resolve(it) == target }.sortedByDescending(::depthOf)) {
            val parent = state[id]?.mergedInto ?: continue
            val children = childrenOf(id)
            groups += Group(
                changes = listOf(Change.Unmerge(id)) + children.map { Change.Repoint(it, parent) },
                writes = setOf(id) + children,
                reads = if (children.isEmpty()) emptySet() else setOf(id),
            )
            state[id] = state.getValue(id).copy(mergedInto = null)
            children.forEach { state[it] = state.getValue(it).copy(mergedInto = parent) }
        }
        for (id in add) {
            val children = childrenOf(id)
            groups += Group(
                changes = listOf(Change.Merge(id, target)) + children.map { Change.Repoint(it, target) },
                writes = setOf(id) + children,
                reads = if (children.isEmpty()) emptySet() else setOf(id),
            )
            state[id] = state.getValue(id).copy(mergedInto = target)
            children.forEach { state[it] = state.getValue(it).copy(mergedInto = target) }
        }

        val batches = mutableListOf<MutableList<Change>>()
        val touched = HashSet<ProductId>()
        val reads = HashSet<ProductId>()
        var writes = 0
        for (group in groups) {
            val fits = batches.isNotEmpty() &&
                (group.writes + group.reads).none { it in touched } &&
                (reads + group.reads).size <= MAX_READS_PER_BATCH &&
                writes + group.writes.size <= MAX_WRITES_PER_BATCH
            if (!fits) {
                batches += mutableListOf<Change>()
                touched.clear()
                reads.clear()
                writes = 0
            }
            batches.last() += group.changes
            touched += group.writes + group.reads
            reads += group.reads
            writes += group.writes.size
        }
        return batches
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

    /**
     * [id] の統合先の商品の名前を [name] に訂正する変更（誤字のまま完了した記録の訂正）。購入の記録は書き換えず、
     * 正しい名前の商品に [id] をまとめる。正しい名前の商品がなければ作る。[id] にまとまっていた商品も一緒に移す。
     * 正しい名前の商品が [id] にまとめられていれば、その商品を統合先にして入れ替える。
     * 正しい名前の商品（の統合先）が一時的に非表示なら、また表示する。常に非表示などの設定は、新しく作る商品には引き継ぎ、
     * 既にある商品にまとめるときはその商品（統合先）の設定に従う（[renameTarget] で確認の画面に出す）。
     * [id] が統合されている、名前が [id] と同じ（正規化すると同じ）、名前が空なら null
     */
    fun renameChanges(id: ProductId, name: String, products: Collection<Product>, hidden: Set<ProductId> = emptySet()): List<Change>? {
        if (name.isBlank()) return null
        val byId = products.associateBy { it.id }
        val product = byId[id] ?: return null
        if (product.mergedInto != null) return null
        val newId = ProductId(NameNormalizer.productId(name))
        if (newId == id) return null
        val children = products.filter { it.mergedInto == id }
        if (newId !in byId) {
            return listOf(Change.Create(newId, name, alwaysHidden = product.suggestionExcluded), Change.Merge(id, newId)) +
                children.map { Change.Repoint(it.id, newId) }
        }
        val root = ProductGraph(products).resolve(newId)
        if (root == id) {
            // 正しい名前の商品が、この商品にまとめられている。統合を外して統合先にし、残りを移す
            return listOf(Change.Unmerge(newId), Change.Merge(id, newId)) +
                children.filter { it.id != newId }.map { Change.Repoint(it.id, newId) }
        }
        val changes = listOf(Change.Merge(id, root)) + children.map { Change.Repoint(it.id, root) }
        return if (root in hidden) changes + Change.Show(root) else changes
    }

    /** [id] を非表示にする変更。統合されている商品は、統合先を非表示にする（ここでは null） */
    fun hideChanges(id: ProductId, products: Collection<Product>): List<Change>? =
        products.find { it.id == id }?.takeIf { it.mergedInto == null }?.let { listOf(Change.Hide(id)) }

    /**
     * [id] の名前を [name] に訂正したときにまとめる先（既にある商品の統合先）。新しく作るときや、訂正できないときは null。
     * 設定が統合先のものに変わることを、確認の画面で伝えるために使う
     */
    fun renameTarget(id: ProductId, name: String, products: Collection<Product>): Product? {
        if (name.isBlank()) return null
        val newId = ProductId(NameNormalizer.productId(name))
        val byId = products.associateBy { it.id }
        if (newId == id || newId !in byId) return null
        val root = ProductGraph(products).resolve(newId)
        return byId[root]?.takeIf { root != id }
    }
}
