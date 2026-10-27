package com.cheesecomer.cartmate.domain.suggest

import com.cheesecomer.cartmate.domain.model.Item
import com.cheesecomer.cartmate.domain.model.Product
import com.cheesecomer.cartmate.domain.model.ProductId
import com.cheesecomer.cartmate.domain.model.Purchase
import com.cheesecomer.cartmate.domain.product.NormalizationDictionary
import com.cheesecomer.cartmate.domain.product.ProductGraph
import com.cheesecomer.cartmate.domain.product.ProductHiding
import com.cheesecomer.cartmate.domain.product.ProductResolver
import com.cheesecomer.cartmate.domain.product.ProductResolver.CycleKey
import com.cheesecomer.cartmate.domain.product.ProductResolver.ResolvedKey
import com.cheesecomer.cartmate.domain.product.ProductUsage
import com.cheesecomer.cartmate.domain.text.NameNormalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * 未入力時のサジェスト・入力中の補完・前回の購入・備考の候補（docs/design/suggestions.md）。
 * 入力はどれも自分に見えるもの（[purchases] は直近365日分、[openItems] は家族の未購入の項目）。
 * 購入の記録は、統合先（名寄せの解決のまとめる先）で集計し、周期は周期のグループで集計する。
 *
 * 予測に使う購入の記録は、有効（Undo していない）で、「予測に使わない」にしておらず、商品が非表示でなく、
 * 非表示にしたことがある商品なら非表示にしたあとのもの（docs/requirements.md §4.1）
 */
class SuggestionEngine(
    products: Collection<Product>,
    purchases: Collection<Purchase>,
    openItems: Collection<Item>,
    private val dictionary: NormalizationDictionary,
    private val today: LocalDate,
    private val zone: ZoneId,
    private val params: SuggestionParams = SuggestionParams(),
) {
    private val byId = products.associateBy { it.id }
    private val graph = ProductGraph(products)
    private val resolver = ProductResolver(products, dictionary)
    private val usage = ProductUsage.of(openItems, purchases)
    private val hidden = ProductHiding.hiddenRoots(products, usage)

    private fun rootOf(id: ProductId): ProductId = graph.resolve(id)

    private fun keyOf(id: ProductId): ResolvedKey = resolver.resolve(id).key

    private fun cycleKeyOf(id: ProductId): CycleKey = resolver.cycleKey(id)

    /** 有効で、非表示の商品でも、非表示にする前のものでもない購入の記録（前回の購入と備考の候補に使う） */
    private val activePurchases: List<Purchase> = purchases.filter { purchase ->
        val id = purchase.productId ?: return@filter false
        val root = rootOf(id)
        val hiddenAt = byId[root]?.hiddenAt
        purchase.active && root !in hidden && (hiddenAt == null || !purchase.purchasedAt.isBefore(hiddenAt))
    }

    /** 予測に使う購入の記録 */
    private val facts: List<Purchase> = activePurchases.filter { !it.excludedFromPrediction }

    private fun Purchase.date(): LocalDate = checkedAt.atZone(zone).toLocalDate()

    private val factsByCycle: Map<CycleKey, List<Purchase>> = facts.groupBy { cycleKeyOf(it.productId!!) }
    private val factsByKey: Map<ResolvedKey, List<Purchase>> = facts.groupBy { keyOf(it.productId!!) }
    private val estimates = HashMap<CycleKey, CycleEstimate?>()

    private fun estimate(cycle: CycleKey): CycleEstimate? = estimates.getOrPut(cycle) {
        CycleEstimator.estimate(factsByCycle[cycle].orEmpty().map { it.date() }, params)
    }

    /** 家族のいずれかのリストに未購入である商品（統合先で比較） */
    private val openKeys: Set<ResolvedKey> = openItems.mapNotNull { it.productId?.let(::keyOf) }.toSet()
    private val openCycles: Set<CycleKey> = openItems.mapNotNull { it.productId?.let(::cycleKeyOf) }.toSet()

    /**
     * 統合先ごとの、名前を出す商品の候補（どこにもまとめられていない、非表示でない商品）。自分に見える項目か購入の記録がある
     * 商品の統合先だけ（他のメンバーがプライベートでだけ使った名前を出さない）。統合された商品の名前（誤字など）は出さない
     */
    private val rootsByKey: Map<ResolvedKey, Set<ProductId>> = usage.visibleIds
        .map(::rootOf)
        .filter { it in byId && it !in hidden }
        .groupBy(::keyOf)
        .mapValues { (_, roots) -> roots.toSet() }

    /** 統合先ごとの、最後に使った時刻（まとめている商品の項目と購入を含む） */
    private val lastUsedByRoot: Map<ProductId, Instant> = HashMap<ProductId, Instant>().apply {
        for ((id, at) in usage.lastUsedAt) merge(rootOf(id), at, ::maxOf)
    }

    /** [key] の候補に出す商品。家族が最近使った名前の商品 */
    private fun representative(key: ResolvedKey): Product? =
        rootsByKey[key].orEmpty()
            .mapNotNull { byId[it] }
            .maxWithOrNull(compareBy<Product>({ lastUsedByRoot[it.id] ?: Instant.MIN }, { it.displayName }))

    private fun latest(purchases: Collection<Purchase>?): Purchase? = purchases?.maxByOrNull { it.checkedAt }

    private fun relevance(listName: String): Map<ResolvedKey, Double> =
        ListSimilarity.relevance(
            ListSimilarity.listKey(listName),
            ListSimilarity.buildProfiles(facts.map { it.listNameSnapshot to keyOf(it.productId!!) }),
            params,
        )

    // 未入力時のサジェスト

    sealed interface DueResult {
        /** 同じ名前のリストの履歴も、関連するリストもない。「商品名を入力すると候補が出ます」とだけ表示する */
        data object NoContext : DueResult

        data class Suggestions(val items: List<DueSuggestion>) : DueResult
    }

    data class DueSuggestion(
        /** 追加に使う商品と、表示する名前 */
        val product: Product,
        val estimate: CycleEstimate,
        val ratio: Double,
        val relevance: Double,
        /** 前回の数量と単位（「＋」と商品詳細入力画面の初期値） */
        val lastQuantity: Double?,
        val lastUnit: String?,
        /** 最後に買ってからの日数（「最後は16日前」） */
        val daysSinceLast: Long,
    )

    /**
     * [listName] のリストで出す、時期が近い商品。周期の経過率が [SuggestionParams.dueRatio] 以上 [SuggestionParams.staleRatio] 以下で、
     * 未購入の項目になく、提案から除外しておらず、このリストで関連がある商品を、経過率・関連度の大きい順に
     */
    fun due(listName: String): DueResult {
        val relevance = relevance(listName)
        if (relevance.isEmpty()) return DueResult.NoContext
        val items = factsByCycle.keys.mapNotNull { cycle ->
            if (cycle in openCycles) return@mapNotNull null
            val estimate = estimate(cycle) ?: return@mapNotNull null
            val ratio = CycleEstimator.elapsedRatio(estimate, today)
            if (ratio < params.dueRatio || ratio > params.staleRatio) return@mapNotNull null
            val purchases = factsByCycle.getValue(cycle)
            val keys = purchases.map { keyOf(it.productId!!) }.toSet()
            if (keys.any { it in openKeys }) return@mapNotNull null
            if (keys.flatMap { rootsByKey[it].orEmpty() }.any { byId[it]?.suggestionExcluded == true }) return@mapNotNull null
            val score = keys.maxOf { relevance[it] ?: 0.0 }
            if (score <= 0.0) return@mapNotNull null
            val last = latest(purchases)!!
            val product = representative(keyOf(last.productId!!)) ?: return@mapNotNull null
            DueSuggestion(product, estimate, ratio, score, last.quantity, last.unit, ChronoUnit.DAYS.between(estimate.lastPurchase, today))
        }
        return DueResult.Suggestions(
            items.sortedWith(compareByDescending<DueSuggestion> { it.ratio }.thenByDescending { it.relevance }).take(params.maxDue),
        )
    }

    // 入力中の補完

    /** 補完の一致の理由。順位と表示の補足に使う */
    enum class MatchReason { PREFIX, DICTIONARY, PARTIAL }

    data class Completion(
        val product: Product,
        val reason: MatchReason,
        /** 直近365日の購入回数（予測に使う記録） */
        val purchaseCount: Int,
        /** 家族のいずれかのリストに未購入である（「追加済み」）。追加はできる */
        val alreadyAdded: Boolean,
        /** 早すぎる再購入 */
        val early: EarlyPurchaseNotice?,
        val lastQuantity: Double?,
        val lastUnit: String?,
    )

    /**
     * [query] の補完。候補の生成元は購入履歴（自分に見える項目と購入の記録）だけで、辞書は照合を助けるだけ。
     * 前方一致（辞書の別名を含む）を部分一致より優先し、次に購入回数、次に [listName] での関連度で並べる。
     * 部分一致だけの候補は、2回以上買った商品に限る。周期の条件は使わない
     */
    fun complete(query: String, listName: String): List<Completion> {
        val key = NameNormalizer.searchKey(query)
        if (query.isBlank() || key.isEmpty()) return emptyList()
        val reasons = HashMap<ResolvedKey, MatchReason>()
        fun hit(resolved: ResolvedKey, reason: MatchReason) {
            if (resolved in rootsByKey) reasons.merge(resolved, reason, ::minOf)
        }
        for (id in usage.visibleIds) {
            val product = byId[id] ?: continue
            if (rootOf(id) in hidden) continue
            when {
                product.searchKey.startsWith(key) -> hit(keyOf(id), MatchReason.PREFIX)
                product.searchKey.contains(key) -> hit(keyOf(id), MatchReason.PARTIAL)
            }
        }
        // 辞書は照合だけに使う。品目名と exact・prefix の別名で、履歴の商品に当てる（既定の同義語は使わない）
        val normalized = NameNormalizer.normalizedName(query)
        for ((alias, entry) in dictionary.aliases) {
            val matched = when (entry.match) {
                NormalizationDictionary.Match.PREFIX -> alias.startsWith(normalized)
                NormalizationDictionary.Match.EXACT -> alias == normalized
                NormalizationDictionary.Match.DEFAULT -> false
            }
            if (matched) hit(ResolvedKey.OfCanonical(entry.canonicalId), MatchReason.DICTIONARY)
        }
        for ((canonical, name) in dictionary.canonicalNames) {
            if (NameNormalizer.normalizedName(name).startsWith(normalized)) hit(ResolvedKey.OfCanonical(canonical), MatchReason.DICTIONARY)
        }

        val relevance = relevance(listName)
        return reasons.mapNotNull { (resolved, reason) ->
            val count = factsByKey[resolved].orEmpty().size
            if (reason == MatchReason.PARTIAL && count < 2) return@mapNotNull null
            val product = representative(resolved) ?: return@mapNotNull null
            val last = latest(activePurchases.filter { keyOf(it.productId!!) == resolved })
            Completion(
                product = product,
                reason = reason,
                purchaseCount = count,
                alreadyAdded = resolved in openKeys,
                early = EarlyPurchaseDetector.detect(estimate(cycleKeyOf(product.id)), today, params),
                lastQuantity = last?.quantity,
                lastUnit = last?.unit,
            ) to (relevance[resolved] ?: 0.0)
        }
            .sortedWith(
                compareBy<Pair<Completion, Double>> { if (it.first.reason == MatchReason.PARTIAL) 1 else 0 }
                    .thenByDescending { it.first.purchaseCount }
                    .thenByDescending { it.second }
                    .thenBy { it.first.product.searchKey },
            )
            .map { it.first }
            .take(params.maxCompletions)
    }

    // 商品詳細入力画面

    /** 「前回: 1パック（10/18 購入・約14日ごと）」 */
    data class PreviousPurchase(
        val quantity: Double?,
        val unit: String?,
        val date: LocalDate,
        /** 周期が分からなければ null */
        val typicalIntervalDays: Int?,
        /** 早すぎる再購入 */
        val early: EarlyPurchaseNotice?,
    )

    /** [name] の商品の前回の購入。購入の記録がなければ null */
    fun previous(name: String): PreviousPurchase? {
        val id = ProductId(NameNormalizer.productId(name))
        val resolved = keyOfName(name, id)
        val last = latest(activePurchases.filter { keyOf(it.productId!!) == resolved }) ?: return null
        val estimate = estimate(cycleKeyOfName(name, id))
        return PreviousPurchase(
            quantity = last.quantity,
            unit = last.unit,
            date = last.date(),
            typicalIntervalDays = estimate?.typicalIntervalDays,
            early = EarlyPurchaseDetector.detect(estimate, today, params),
        )
    }

    /** [name] の商品の備考の候補。自分に見える購入の記録の、空でない備考を新しい順に、重複を除いて最大3件 */
    fun memoCandidates(name: String): List<String> {
        val resolved = keyOfName(name, ProductId(NameNormalizer.productId(name)))
        return activePurchases
            .filter { keyOf(it.productId!!) == resolved }
            .sortedByDescending { it.checkedAt }
            .mapNotNull { it.memo?.trim()?.takeIf(String::isNotEmpty) }
            .distinct()
            .take(MAX_MEMO_CANDIDATES)
    }

    /** 名前から決まる商品のまとめる先。まだない商品なら、辞書（既定の同義語を除く）で当たる品目か、その名前の商品 */
    private fun keyOfName(name: String, id: ProductId): ResolvedKey {
        if (id in byId) return keyOf(id)
        val alias = dictionary.lookup(NameNormalizer.normalizedName(name))
            ?.takeIf { it.match != NormalizationDictionary.Match.DEFAULT }
        return if (alias != null) ResolvedKey.OfCanonical(alias.canonicalId) else ResolvedKey.OfProduct(id)
    }

    private fun cycleKeyOfName(name: String, id: ProductId): CycleKey {
        if (id in byId) return cycleKeyOf(id)
        val resolved = keyOfName(name, id)
        val group = (resolved as? ResolvedKey.OfCanonical)?.canonicalId?.let(dictionary.cycleGroups::get)
        return if (group != null) CycleKey.OfGroup(group) else CycleKey.Of(resolved)
    }

    private companion object {
        const val MAX_MEMO_CANDIDATES = 3
    }
}
