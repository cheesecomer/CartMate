package com.cheesecomer.cartmate.domain.suggest

import com.cheesecomer.cartmate.domain.model.Item
import com.cheesecomer.cartmate.domain.model.ListId
import com.cheesecomer.cartmate.domain.model.Product
import com.cheesecomer.cartmate.domain.model.ProductId
import com.cheesecomer.cartmate.domain.model.Purchase
import com.cheesecomer.cartmate.domain.product.AttributeExtractor
import com.cheesecomer.cartmate.domain.product.NormalizationDictionary
import com.cheesecomer.cartmate.domain.product.Qualifier
import com.cheesecomer.cartmate.domain.product.RecordAttributes
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

    /** 周期を合わせて数える単位。周期のグループ（なければまとめる先）と、名寄せに関係する属性（形態・用途・種類・容量） */
    private data class Group(val cycle: CycleKey, val qualifiers: Set<Qualifier>)

    /** 1件の記録のまとめる先と属性 */
    private data class Record(val key: ResolvedKey, val group: Group, val attributes: RecordAttributes)

    /**
     * 商品 [id] を、商品名 [name] と備考 [memo] で記録したもののまとめる先（docs/design/suggestions.md「商品名と備考による判定」）。
     * 手動の統合・分離と辞書の名寄せは [ProductResolver] のとおり。そのうえで、
     * - 既定の同義語の商品（「お茶」）は、商品名や備考に品目の語（「麦茶」）があればその品目、なければ既定の品目（緑茶）にまとめる。
     *   品目の語が食い違えば、まとめない（その商品のまま）
     * - 辞書でまとめた商品でも、備考の品目の語が食い違えば（「緑茶」＋備考「麦茶」）、まとめない
     * 属性（形態・用途・種類・容量）が未指定の記録と指定のある記録は、別のものとして数える（未指定を一致とみなさない）
     */
    private fun record(id: ProductId, name: String, memo: String?): Record {
        val resolution = resolver.resolve(id)
        val attributes = AttributeExtractor.extract(name, memo, dictionary)
        val own = ResolvedKey.OfProduct(rootOf(id))
        val key = when (val reason = resolution.reason) {
            ProductResolver.Reason.Self -> {
                val alias = byId[rootOf(id)]?.normalizedName?.let(dictionary::lookup)
                    ?.takeIf { it.match == NormalizationDictionary.Match.DEFAULT }
                val items = attributes.nameItems + attributes.memoItems
                when {
                    alias == null -> resolution.key
                    items.isEmpty() -> ResolvedKey.OfCanonical(alias.canonicalId)
                    items.size == 1 -> ResolvedKey.OfCanonical(items.single())
                    else -> own
                }
            }
            is ProductResolver.Reason.Dictionary ->
                if (attributes.memoItems.isEmpty() || attributes.memoItems == setOf(reason.canonicalId)) resolution.key else own
            else -> resolution.key
        }
        return Record(key, Group(cycleOf(key), attributes.qualifiers), attributes)
    }

    private fun Purchase.record(): Record = record(productId!!, inputName, memo)

    private fun Item.record(): Record? = productId?.let { record(it, inputName, memo) }

    /** 商品のまとめる先（名前だけで決まるもの。備考は使わない） */
    private fun keyOf(id: ProductId): ResolvedKey = record(id, byId[id]?.displayName.orEmpty(), memo = null).key

    private fun cycleOf(key: ResolvedKey): CycleKey {
        val group = (key as? ResolvedKey.OfCanonical)?.canonicalId?.let(dictionary.cycleGroups::get)
        return if (group != null) CycleKey.OfGroup(group) else CycleKey.Of(key)
    }

    /** 有効で、非表示の商品でも、非表示にする前のものでもない購入の記録（前回の購入と備考の候補に使う） */
    private val activePurchases: List<Purchase> = purchases.filter { purchase ->
        val id = purchase.productId ?: return@filter false
        val root = rootOf(id)
        val hiddenAt = byId[root]?.hiddenAt
        purchase.active && root !in hidden && (hiddenAt == null || !purchase.purchasedAt.isBefore(hiddenAt))
    }

    private val records: Map<Purchase, Record> = activePurchases.associateWith { it.record() }

    /** 予測に使う購入の記録 */
    private val facts: List<Purchase> = activePurchases.filter { !it.excludedFromPrediction }

    private fun Purchase.date(): LocalDate = checkedAt.atZone(zone).toLocalDate()

    private val factsByGroup: Map<Group, List<Purchase>> = facts.groupBy { records.getValue(it).group }
    private val factsByKey: Map<ResolvedKey, List<Purchase>> = facts.groupBy { records.getValue(it).key }
    private val estimates = HashMap<Group, CycleEstimate?>()

    private fun estimate(group: Group): CycleEstimate? = estimates.getOrPut(group) {
        CycleEstimator.estimate(factsByGroup[group].orEmpty().map { it.date() }, params)
    }

    /** 周期が分からないときの早すぎる再購入の判定にも、周期と同じ記録を使う */
    private fun early(group: Group): EarlyPurchaseNotice? =
        EarlyPurchaseDetector.detect(estimate(group), factsByGroup[group]?.maxOf { it.date() }, today, params)

    /** 家族のいずれかのリストに未購入である商品（統合先で比較）。属性が違っても、同じ商品があれば出さない */
    private val openRecords: List<Record> = openItems.mapNotNull { it.record() }
    private val openKeys: Set<ResolvedKey> = openRecords.map { it.key }.toSet()
    /** 未購入の項目があるリスト（「追加済み（スーパー）」）。商品（統合先）ごとに */
    private val openListsByKey: Map<ResolvedKey, Set<ListId>> = openItems
        .mapNotNull { item -> item.record()?.let { it.key to item.listId } }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, ids) -> ids.toSet() }
    private val openCycles: Set<CycleKey> = openRecords.map { it.group.cycle }.toSet()

    /**
     * まとめる先ごとの、名前を出す商品の候補（どこにもまとめられていない、非表示でない商品）。自分に見える項目か購入の記録がある
     * 商品の統合先だけ（他のメンバーがプライベートでだけ使った名前を出さない）。統合された商品の名前（誤字など）は出さない
     */
    private val rootsByKey: Map<ResolvedKey, Set<ProductId>> = usage.visibleIds
        .map(::rootOf)
        // 一時的に非表示と常に非表示の商品は、候補に名前を出さない（docs/requirements.md §4.3）
        .filter { it in byId && it !in hidden && !alwaysHidden(it) }
        .groupBy(::keyOf)
        .mapValues { (_, roots) -> roots.toSet() }

    /** 常に非表示（統合先の設定）。購入の記録は残り、周期も数えるが、候補と注意には出さない */
    private fun alwaysHidden(root: ProductId): Boolean = byId[root]?.suggestionExcluded == true

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
            ListSimilarity.buildProfiles(facts.map { it.listNameSnapshot to records.getValue(it).key }),
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
        /**
         * 備考から読んだ属性で分けて数えたときの、前回の備考（「＋」で一緒に入れて、属性を引き継ぐ）。分けていなければ null
         */
        val lastMemo: String? = null,
        /** 名前に添えて表示する属性（「茶葉」「2L」「麦茶」）。同じ名前の候補を見分けるため */
        val labels: List<String> = emptyList(),
    )

    /**
     * [listName] のリストで出す、時期が近い商品。周期が安定していて（ばらつきが [SuggestionParams.maxDispersion] 以下）、
     * 経過率が [SuggestionParams.dueRatio] 以上 [SuggestionParams.staleRatio] 以下で、未購入の項目になく、非表示でなく、
     * このリストで関連がある商品を、[priority] と関連度の大きい順に
     */
    fun due(listName: String): DueResult {
        val relevance = relevance(listName)
        if (relevance.isEmpty()) return DueResult.NoContext
        val items = factsByGroup.keys.mapNotNull { group ->
            if (group.cycle in openCycles) return@mapNotNull null
            val estimate = estimate(group) ?: return@mapNotNull null
            // 献立で買う食材のように、たまたまの間隔でばらついている商品は、補充が必要とはみなさない
            if (estimate.dispersion > params.maxDispersion) return@mapNotNull null
            val ratio = CycleEstimator.elapsedRatio(estimate, today)
            if (ratio < params.dueRatio || ratio > params.staleRatio) return@mapNotNull null
            val purchases = factsByGroup.getValue(group)
            val keys = purchases.map { records.getValue(it).key }.toSet()
            if (keys.any { it in openKeys }) return@mapNotNull null
            val score = keys.maxOf { relevance[it] ?: 0.0 }
            if (score <= 0.0) return@mapNotNull null
            val last = latest(purchases)!!
            val record = records.getValue(last)
            // まとめる先に名前を出せる商品がなければ（「お茶」＋備考「麦茶」だけで買っている場合など）、前回買った商品の名前
            val product = representative(record.key)
                ?: byId[rootOf(last.productId!!)]?.takeIf { it.id !in hidden && !alwaysHidden(it.id) }
                ?: return@mapNotNull null
            val memoItemNames = record.attributes.memoItems.map { dictionary.canonicalNames[it] ?: it.value }
            DueSuggestion(
                product = product,
                estimate = estimate,
                ratio = ratio,
                relevance = score,
                lastQuantity = last.quantity,
                lastUnit = last.unit,
                daysSinceLast = ChronoUnit.DAYS.between(estimate.lastPurchase, today),
                lastMemo = last.memo?.trim()?.takeIf { record.attributes.fromMemo && it.isNotEmpty() },
                labels = memoItemNames.filter { it != product.displayName } + group.qualifiers.map { label(it) }.sorted(),
            )
        }
        return DueResult.Suggestions(
            items.sortedWith(compareByDescending<DueSuggestion> { priority(it.ratio) }.thenByDescending { it.relevance }).take(params.maxDue),
        )
    }

    /**
     * 時期が近い商品の順位。経過率が [SuggestionParams.overduePeakRatio] までは経過率そのもの（大きいほど上）で、
     * それを超えると超えた分だけ下げる（予定日を大きく過ぎた商品は「買うのをやめた」かもしれないので、下に沈める）
     */
    private fun priority(ratio: Double): Double =
        if (ratio <= params.overduePeakRatio) ratio else params.overduePeakRatio - (ratio - params.overduePeakRatio)

    /** 属性の表示。容量は「2000ml」を「2L」に戻す */
    private fun label(qualifier: Qualifier): String {
        if (qualifier.kind != "volume") return qualifier.value
        val amount = qualifier.value.dropLast(if (qualifier.value.endsWith("ml")) 2 else 1).toBigDecimalOrNull() ?: return qualifier.value
        val thousand = 1000.toBigDecimal()
        return when {
            qualifier.value.endsWith("ml") && amount >= thousand -> amount.divide(thousand).stripTrailingZeros().toPlainString() + "L"
            qualifier.value.endsWith("g") && !qualifier.value.endsWith("ml") && amount >= thousand ->
                amount.divide(thousand).stripTrailingZeros().toPlainString() + "kg"
            else -> qualifier.value
        }
    }

    // 入力中の補完

    /** 補完の一致の理由。順位と表示の補足に使う */
    enum class MatchReason { PREFIX, DICTIONARY, PARTIAL }

    data class Completion(
        val product: Product,
        val reason: MatchReason,
        /** 直近365日の購入回数（予測に使う記録） */
        val purchaseCount: Int,
        /** 未購入の項目があるリスト（「追加済み（スーパー）」）。空でなければ追加済み。追加はできる */
        val addedListIds: Set<ListId>,
        /** 早すぎる再購入 */
        val early: EarlyPurchaseNotice?,
        val lastQuantity: Double?,
        val lastUnit: String?,
    ) {
        /** 家族のいずれかのリストに未購入である（「追加済み」） */
        val alreadyAdded: Boolean get() = addedListIds.isNotEmpty()
    }

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
            val last = latest(activePurchases.filter { records.getValue(it).key == resolved })
            Completion(
                product = product,
                reason = reason,
                purchaseCount = count,
                addedListIds = openListsByKey[resolved].orEmpty(),
                // 補完は名前で選ぶので、属性を指定していない記録の周期で判定する
                early = early(Group(cycleOf(resolved), emptySet())),
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

    /** [name] の商品の前回の購入。購入の記録がなければ null。周期は前回の購入と同じ属性の記録で求める */
    fun previous(name: String): PreviousPurchase? {
        val resolved = keyOfName(name)
        val last = latest(activePurchases.filter { records.getValue(it).key == resolved }) ?: return null
        val group = records.getValue(last).group
        return PreviousPurchase(
            quantity = last.quantity,
            unit = last.unit,
            date = last.date(),
            typicalIntervalDays = estimate(group)?.typicalIntervalDays,
            // 常に非表示の商品には、早すぎる再購入の注意も出さない
            early = early(group).takeIf { !alwaysHidden(rootOf(last.productId!!)) },
        )
    }

    /** [name] の商品の備考の候補。自分に見える購入の記録の、空でない備考を新しい順に、重複を除いて最大3件 */
    fun memoCandidates(name: String): List<String> {
        val resolved = keyOfName(name)
        return activePurchases
            .filter { records.getValue(it).key == resolved }
            .sortedByDescending { it.checkedAt }
            .mapNotNull { it.memo?.trim()?.takeIf(String::isNotEmpty) }
            .distinct()
            .take(MAX_MEMO_CANDIDATES)
    }

    /** 名前から決まるまとめる先。まだない商品なら、名前だけの新しい商品として求める（辞書と既定の同義語を使う） */
    private fun keyOfName(name: String): ResolvedKey {
        val id = ProductId(NameNormalizer.productId(name))
        if (id in byId) return keyOf(id)
        val attributes = AttributeExtractor.extract(name, null, dictionary)
        val alias = dictionary.lookup(NameNormalizer.normalizedName(name)) ?: return ResolvedKey.OfProduct(id)
        if (alias.match != NormalizationDictionary.Match.DEFAULT) return ResolvedKey.OfCanonical(alias.canonicalId)
        return when (attributes.nameItems.size) {
            0 -> ResolvedKey.OfCanonical(alias.canonicalId)
            1 -> ResolvedKey.OfCanonical(attributes.nameItems.single())
            else -> ResolvedKey.OfProduct(id)
        }
    }

    private companion object {
        const val MAX_MEMO_CANDIDATES = 3
    }
}
