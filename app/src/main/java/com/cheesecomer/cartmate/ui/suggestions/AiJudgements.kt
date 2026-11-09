package com.cheesecomer.cartmate.ui.suggestions

import android.util.Log
import com.cheesecomer.cartmate.domain.model.Item
import com.cheesecomer.cartmate.domain.model.ProductId
import com.cheesecomer.cartmate.domain.model.Visibility
import com.cheesecomer.cartmate.domain.product.AiCandidates
import com.cheesecomer.cartmate.domain.product.JudgementRequest
import com.cheesecomer.cartmate.domain.product.NormalizationDictionary
import com.cheesecomer.cartmate.domain.product.ProductGraph
import com.cheesecomer.cartmate.domain.product.ProductHiding
import com.cheesecomer.cartmate.domain.product.ProductMerge
import com.cheesecomer.cartmate.domain.product.ProductUsage
import com.cheesecomer.cartmate.domain.repository.AiDismissalRepository
import com.cheesecomer.cartmate.domain.repository.DictionaryProvider
import com.cheesecomer.cartmate.domain.repository.ItemRepository
import com.cheesecomer.cartmate.domain.repository.NameJudgementRepository
import com.cheesecomer.cartmate.domain.repository.OnlineStatus
import com.cheesecomer.cartmate.domain.repository.ProductRepository
import com.cheesecomer.cartmate.domain.repository.PurchaseRepository
import com.cheesecomer.cartmate.domain.text.NameNormalizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 購入のあとに、AI の判定を頼む（docs/design/suggestions.md「問い合わせる条件」）。チェックの操作を止めないよう、
 * アプリの中で非同期に行う。通信できなければ、つながるまで待つ
 */
fun interface JudgementRequester {
    /** [item] をチェックして購入を記録した */
    fun onPurchased(item: Item)

    companion object {
        /** 何もしない（テスト） */
        val NONE = JudgementRequester { }
    }
}

@Singleton
class NameJudgementRequester(
    private val productRepository: ProductRepository,
    private val purchaseRepository: PurchaseRepository,
    private val dictionaryProvider: DictionaryProvider,
    private val judgementRepository: NameJudgementRepository,
    private val onlineStatus: OnlineStatus,
    private val clock: Clock,
    private val scope: CoroutineScope,
) : JudgementRequester {

    @Inject
    constructor(
        productRepository: ProductRepository,
        purchaseRepository: PurchaseRepository,
        dictionaryProvider: DictionaryProvider,
        judgementRepository: NameJudgementRepository,
        onlineStatus: OnlineStatus,
        clock: Clock,
    ) : this(
        productRepository, purchaseRepository, dictionaryProvider, judgementRepository, onlineStatus, clock,
        CoroutineScope(SupervisorJob() + Dispatchers.Default),
    )

    /** 頼んでいる途中のキー。同じ商品を続けてチェックしても、二重に頼まない */
    private val inFlight: MutableSet<String> = Collections.synchronizedSet(HashSet())

    override fun onPurchased(item: Item) {
        // プライベートな項目の名前は送らない
        if (item.visibility != Visibility.SHARED) return
        val id = item.productId ?: ProductId(NameNormalizer.productId(item.inputName))
        scope.launch {
            runCatching { request(id) }.onFailure { Log.w(TAG, "request failed", it) }
        }
    }

    private suspend fun request(id: ProductId) {
        val products = productRepository.observeProducts().first()
        val purchases = purchaseRepository.observePurchases(since = clock.instant().minus(PURCHASE_WINDOW)).first()
        // 辞書を読み込み終えるまで待つ（空の辞書では、辞書で決まる名前まで送ってしまう）
        val dictionary = dictionaryProvider.observeDictionary().first { it != NormalizationDictionary.EMPTY }
        val request = JudgementRequest.of(id, products, purchases, dictionary) ?: return
        val existing = judgementRepository.observeJudgements().first().firstOrNull { it.key == request.key }
        if (!JudgementRequest.shouldAsk(existing, clock.instant())) return
        if (!inFlight.add(request.key)) return
        try {
            onlineStatus.observeOnline().first { it }
            judgementRepository.requestJudgement(request, dictionary.version)
        } finally {
            inFlight.remove(request.key)
        }
    }

    private companion object {
        const val TAG = "JudgementRequester"
        val PURCHASE_WINDOW: Duration = Duration.ofDays(365)
    }
}

/** AI の名寄せの候補（docs/design/suggestions.md「候補にする条件」）。表記ゆれのチップと商品名の管理が使う */
interface AiCandidateSource {
    /** 自分に見える商品の候補の組。非表示・常に非表示の商品と、「この候補を出さない」にした組は除く */
    fun observe(): Flow<List<AiCandidates.Candidate>>

    /** 「この候補を出さない」 */
    suspend fun dismiss(pair: AiCandidates.Pair)

    /**
     * 候補を選んで、[AiCandidates.Candidate.source] を [AiCandidates.Candidate.target] にまとめる（手動の統合と同じ書き込み）。
     * まとめられなければ（ほかのメンバーが先に統合関係を変えていた など）false
     */
    suspend fun merge(candidate: AiCandidates.Candidate): Boolean

    companion object {
        /** 候補を出さない（テスト） */
        val NONE = object : AiCandidateSource {
            override fun observe(): Flow<List<AiCandidates.Candidate>> = flowOf(emptyList())
            override suspend fun dismiss(pair: AiCandidates.Pair) = Unit
            override suspend fun merge(candidate: AiCandidates.Candidate) = false
        }
    }
}

class JudgedAiCandidateSource @Inject constructor(
    private val productRepository: ProductRepository,
    private val itemRepository: ItemRepository,
    private val purchaseRepository: PurchaseRepository,
    private val dictionaryProvider: DictionaryProvider,
    private val judgementRepository: NameJudgementRepository,
    private val dismissals: AiDismissalRepository,
    private val clock: Clock,
) : AiCandidateSource {

    override fun observe(): Flow<List<AiCandidates.Candidate>> {
        val usage = combine(
            itemRepository.observeOpenItems(),
            purchaseRepository.observePurchases(since = clock.instant().minus(PURCHASE_WINDOW)),
        ) { items, purchases -> ProductUsage.of(items, purchases) to purchases }
        return combine(
            productRepository.observeProducts(),
            usage,
            dictionaryProvider.observeDictionary(),
            judgementRepository.observeJudgements(),
            dismissals.observeDismissed(),
        ) { products, (usage, purchases), dictionary, judgements, dismissed ->
            // 表記ゆれのチップと同じく、非表示と常に非表示の商品は候補にしない
            val hidden = ProductHiding.hiddenRoots(products, usage)
            val graph = ProductGraph(products)
            val byId = products.associateBy { it.id }
            val visible = products.filter {
                it.id in usage.visibleIds && !ProductHiding.isHidden(it.id, products, hidden) &&
                    byId[graph.resolve(it.id)]?.suggestionExcluded != true
            }
            AiCandidates.find(products, visible, purchases, judgements.associateBy { it.key }, dictionary, dismissed)
        }
    }

    override suspend fun dismiss(pair: AiCandidates.Pair) = dismissals.dismiss(pair)

    override suspend fun merge(candidate: AiCandidates.Candidate): Boolean {
        val products = productRepository.observeProducts().first()
        val changes = ProductMerge.mergeChanges(candidate.target.id, listOf(candidate.source.id), products) ?: return false
        productRepository.applyMergeChanges(changes)
        return true
    }

    private companion object {
        val PURCHASE_WINDOW: Duration = Duration.ofDays(365)
    }
}
