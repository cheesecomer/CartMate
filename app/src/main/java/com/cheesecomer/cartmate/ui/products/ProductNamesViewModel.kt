package com.cheesecomer.cartmate.ui.products

import com.cheesecomer.cartmate.domain.suggest.PurchaseHistory
import com.cheesecomer.cartmate.domain.model.Purchase
import com.cheesecomer.cartmate.domain.repository.CurrentUserProvider
import com.cheesecomer.cartmate.domain.repository.ItemRepository
import com.cheesecomer.cartmate.domain.repository.MemberDirectory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cheesecomer.cartmate.domain.model.Product
import com.cheesecomer.cartmate.domain.model.ProductId
import com.cheesecomer.cartmate.domain.text.NameNormalizer
import com.cheesecomer.cartmate.domain.product.ProductMerge
import com.cheesecomer.cartmate.domain.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

private const val STOP_TIMEOUT_MILLIS = 5_000L

/** 「商品名の管理」の一覧（docs/requirements.md §3.5「商品名の管理」）。未統合の商品を並べ、検索欄で絞り込む */
@HiltViewModel
class ProductNamesViewModel @Inject constructor(catalog: ProductCatalog, memberDirectory: MemberDirectory) : ViewModel() {

    /** 非表示の商品と、非表示にした人（家族にいない人は null） */
    data class HiddenEntry(val entry: ProductMerge.Entry, val byName: String?)

    data class Lists(
        val entries: List<ProductMerge.Entry>,
        val hidden: List<HiddenEntry>,
        /** 検索欄の文字。空なら絞り込まない */
        val query: String = "",
    )

    private val query = MutableStateFlow("")

    /** 一覧と、一番下の「非表示の商品」（一時的に非表示と常に非表示）。どちらも検索欄で絞り込む。読み込み中は null */
    val lists: StateFlow<Lists?> = combine(catalog.observe(), memberDirectory.observeNames(), query) { snapshot, names, query ->
        Lists(
            entries = ProductMerge.entries(snapshot.products, snapshot.visibleIds, snapshot.dictionary, snapshot.hidden)
                .filter { with(ProductMerge) { it.matches(query) } },
            hidden = ProductMerge.hiddenEntries(snapshot.products, snapshot.visibleIds, snapshot.dictionary, snapshot.hidden)
                .filter { with(ProductMerge) { it.matches(query) } }
                .map { entry ->
                    val by = if (entry.mode == ProductMerge.SuggestionMode.ALWAYS_HIDDEN) entry.product.suggestionExcludedBy else entry.product.hiddenBy
                    HiddenEntry(entry, by?.let(names::of))
                },
            query = query,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    fun search(text: String) {
        query.value = text
    }
}

/** 商品を開いた画面。まとめている商品と個別の解除、辞書の名寄せ。統合されている商品なら、統合先と解除 */
@HiltViewModel
class ProductDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val catalog: ProductCatalog,
    private val productRepository: ProductRepository,
    private val itemRepository: ItemRepository,
    private val currentUser: CurrentUserProvider,
    private val clock: Clock,
) : ViewModel() {

    val productId = ProductId(checkNotNull(savedStateHandle.get<String>(ProductDetailRoute::productId.name)))

    sealed interface State {
        data object Loading : State
        /** 開いたときから見えない商品 */
        data object NotFound : State
        data class Loaded(
            val detail: ProductMerge.Detail,
            /** この商品（まとめている商品と辞書でまとまる商品を含む）の、自分がチェックした購入の数。まとめて削除できる */
            val ownPurchases: Int = 0,
            /** ほかのメンバーがチェックした購入の数。削除しても残る */
            val othersPurchases: Int = 0,
            /** 「前回: 10/18 購入（約14日ごと）」。購入の記録がなければ null */
            val summary: PurchaseHistory.Summary? = null,
        ) : State

        /**
         * 表示していた商品が一覧から外れた（購入の記録がすべて削除された など）。「見つかりません」は見せず、
         * 商品名の管理に戻って知らせる
         */
        data class Gone(val name: String) : State
    }

    val state: StateFlow<State> = catalog.observe()
        .map { snapshot ->
            val detail = ProductMerge.detail(productId, snapshot.products, snapshot.visibleIds, snapshot.dictionary, snapshot.hidden)
                ?: return@map State.NotFound
            val history = history(snapshot)
            val (own, others) = history.partition { it.purchasedBy == currentUser.currentUserId() }
            State.Loaded(detail, ownPurchases = own.size, othersPurchases = others.size, summary = PurchaseHistory.summary(history, clock.zone))
        }
        // 一度表示できた商品が見つからなくなったら、外れたものとして区別する
        .scan<State, State>(State.Loading) { previous, next ->
            when {
                next != State.NotFound -> next
                previous is State.Loaded -> State.Gone(previous.detail.productName())
                previous is State.Gone -> previous
                else -> next
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), State.Loading)

    sealed interface Event {
        /** 名前を訂正した。訂正した名前の商品（統合先）の画面に移る */
        data class Renamed(val target: ProductId) : Event
    }

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events: Flow<Event> = _events.receiveAsFlow()

    /** この商品の購入履歴（Undo したものを除く） */
    private fun history(snapshot: ProductCatalog.Snapshot): List<Purchase> =
        PurchaseHistory.of(productId, snapshot.products, snapshot.purchases, snapshot.dictionary)

    /**
     * この商品の、自分がチェックした購入をすべて、項目ごと削除する（確認は画面で行う）。家族の画面からも消え、元に戻せない。
     * ほかのメンバーの購入は残る。1件ずつ、項目と購入の記録を同じ書き込みで消す
     */
    fun deleteMyPurchases() {
        viewModelScope.launch {
            val me = currentUser.currentUserId()
            history(catalog.observe().first()).filter { it.purchasedBy == me }.forEach { itemRepository.deletePurchase(it.itemId) }
        }
    }

    /** 名前を [name] に訂正する。購入の記録は書き換えず、正しい名前の商品（なければ作る）にまとめる */
    fun rename(name: String) {
        viewModelScope.launch {
            val snapshot = catalog.observe().first()
            val changes = ProductMerge.renameChanges(productId, name.trim(), snapshot.products, snapshot.hidden) ?: return@launch
            productRepository.applyMergeChanges(changes)
            val target = changes.first { it is ProductMerge.Change.Merge } as ProductMerge.Change.Merge
            _events.send(Event.Renamed(target.into))
        }
    }

    /** 家族全員の一覧・候補・予測に出さない。記録は消さない */
    /**
     * サジェストへの出し方を変える（docs/requirements.md §4.3）。一時的に非表示（再購入で戻る）と常に非表示（戻らない）は
     * どちらか一方にする。家族で共有し、オフラインでも行える
     */
    fun setMode(mode: ProductMerge.SuggestionMode) {
        viewModelScope.launch {
            val snapshot = catalog.observe().first()
            val product = snapshot.products.find { it.id == productId } ?: return@launch
            val current = ProductMerge.modeOf(product, snapshot.hidden)
            if (mode == current) return@launch
            if (product.suggestionExcluded != (mode == ProductMerge.SuggestionMode.ALWAYS_HIDDEN)) {
                productRepository.setSuggestionExcluded(productId, mode == ProductMerge.SuggestionMode.ALWAYS_HIDDEN)
            }
            when {
                mode == ProductMerge.SuggestionMode.TEMPORARILY_HIDDEN ->
                    ProductMerge.hideChanges(productId, snapshot.products)?.let { productRepository.applyMergeChanges(it) }
                product.hiddenAt != null -> productRepository.applyMergeChanges(listOf(ProductMerge.Change.Show(productId)))
            }
        }
    }

    fun show() = setMode(ProductMerge.SuggestionMode.NORMAL)

    /** [name] に訂正したときにまとめる先（既にある商品）。設定がその商品のものになることを、確認の画面で伝える */
    suspend fun renameTarget(name: String): com.cheesecomer.cartmate.domain.model.Product? =
        ProductMerge.renameTarget(productId, name.trim(), catalog.observe().first().products)

    /** [id] の統合を解除する。[id] にまとまっていた商品は、元の統合先に残す */
    fun unmerge(id: ProductId) {
        viewModelScope.launch {
            val products = catalog.observe().first().products
            ProductMerge.unmergeChanges(id, products)?.let { productRepository.applyMergeChanges(it) }
        }
    }

    fun setDictionaryOptOut(optOut: Boolean) {
        viewModelScope.launch { productRepository.setDictionaryOptOut(productId, optOut) }
    }
}

/**
 * 「商品をまとめる」。統合先を固定し、まとめる商品を編集する。今まとめている商品はチェックした状態で並べ、外すと解除する。
 * ほかの商品を選ぶとまとめ、その商品にまとまっている商品も一緒に移る。件数に上限はない（書き込みは分けて送る）
 */
@HiltViewModel
class MergeProductsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val catalog: ProductCatalog,
    private val productRepository: ProductRepository,
) : ViewModel() {

    private val targetId = ProductId(checkNotNull(savedStateHandle.get<String>(MergeProductsRoute::targetId.name)))

    /** 候補の1行。[member] なら今まとめている商品（[entry] は null）。そうでなければ、ほかの統合先の商品 */
    data class Choice(val product: Product, val member: Boolean, val entry: ProductMerge.Entry?)

    data class State(
        /** 統合先。読み込み中や、統合済み・なくなった場合は null */
        val target: ProductMerge.Entry? = null,
        /** 今まとめている商品と、ほかの統合先の商品を、名前の順に */
        val choices: List<Choice> = emptyList(),
        /** 新しくまとめる商品 */
        val added: Set<ProductId> = emptySet(),
        /** チェックを外した（解除する）、今まとめている商品 */
        val removed: Set<ProductId> = emptySet(),
        val query: String = "",
        val loading: Boolean = true,
        /** まとめる操作を送った。二重に送らない */
        val submitted: Boolean = false,
    ) {
        /** チェックしている商品の数（今まとめていて外していない商品と、新しくまとめる商品） */
        val selectedCount: Int get() = (target?.members.orEmpty().count { it.id !in removed }) + added.size
        fun isChecked(choice: Choice): Boolean = if (choice.member) choice.product.id !in removed else choice.product.id in added
        val addedEntries: List<ProductMerge.Entry> get() = choices.mapNotNull { it.entry }.filter { it.product.id in added }
        val removedProducts: List<Product> get() = target?.members.orEmpty().filter { it.id in removed }
        val canSubmit: Boolean get() = target != null && (added.isNotEmpty() || removed.isNotEmpty()) && !submitted

        /** 検索欄で絞り込んだ候補。選択は、絞り込みで隠れても残す */
        val shownChoices: List<Choice> get() {
            val key = NameNormalizer.searchKey(query).takeIf { query.isNotBlank() } ?: return choices
            return choices.filter { choice ->
                choice.product.searchKey.contains(key) || choice.entry?.members.orEmpty().any { it.searchKey.contains(key) }
            }
        }
    }

    sealed interface Event {
        /** まとめ終えた（書き込みを送った）。統合先の画面に戻る */
        data object Done : Event
    }

    private val added = MutableStateFlow(emptySet<ProductId>())
    private val removed = MutableStateFlow(emptySet<ProductId>())
    private val query = MutableStateFlow("")
    private val submitted = MutableStateFlow(false)
    private val _events = Channel<Event>(Channel.BUFFERED)
    val events: Flow<Event> = _events.receiveAsFlow()

    val state: StateFlow<State> = combine(catalog.observe(), added, removed, query, submitted) { snapshot, added, removed, query, submitted ->
        val target = ProductMerge.entries(snapshot.products, snapshot.visibleIds, snapshot.dictionary, snapshot.hidden)
            .find { it.product.id == targetId }
        val candidates = ProductMerge.candidates(targetId, snapshot.products, snapshot.visibleIds, snapshot.dictionary, snapshot.hidden)
        val choices = (target?.members.orEmpty().map { Choice(it, member = true, entry = null) } + candidates.map { Choice(it.product, member = false, entry = it) })
            .sortedBy { it.product.searchKey }
        State(
            target = target,
            choices = choices,
            // ほかの端末の変更で候補やまとめている商品から外れた商品は、選択からも外す
            added = added.filterTo(HashSet()) { id -> candidates.any { it.product.id == id } },
            removed = removed.filterTo(HashSet()) { id -> target?.members.orEmpty().any { it.id == id } },
            query = query,
            loading = false,
            submitted = submitted,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), State())

    fun toggle(choice: Choice) {
        val id = choice.product.id
        if (choice.member) removed.update { if (id in it) it - id else it + id }
        else added.update { if (id in it) it - id else it + id }
    }

    /** 「すべて解除」。今まとめている商品も含めて、すべてのチェックを外す */
    fun clearAll() {
        added.value = emptySet()
        removed.value = state.value.target?.members.orEmpty().mapTo(HashSet()) { it.id }
    }

    fun search(text: String) {
        query.value = text
    }

    fun submit() {
        val current = state.value
        if (!current.canSubmit) return
        submitted.value = true
        viewModelScope.launch {
            val products = catalog.observe().first().products
            val batches = ProductMerge.editChanges(targetId, current.added, current.removed, products)
            if (batches == null) {
                // 送る前に、ほかの端末の変更で統合先や選んだ商品が統合された
                submitted.value = false
                return@launch
            }
            // 順に送る。前のバッチの結果を、次のバッチのルールが読む
            batches.forEach { productRepository.applyMergeChanges(it) }
            _events.send(Event.Done)
        }
    }
}

/** 商品の画面の商品名 */
private fun ProductMerge.Detail.productName(): String = when (this) {
    is ProductMerge.Detail.Target -> entry.product.displayName
    is ProductMerge.Detail.Merged -> product.displayName
}
