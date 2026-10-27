package com.cheesecomer.cartmate.ui.products

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cheesecomer.cartmate.domain.model.ProductId
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val STOP_TIMEOUT_MILLIS = 5_000L

/** 「商品名の管理」の一覧（docs/requirements.md §3.5「商品名の管理」）。未統合の商品を並べる */
@HiltViewModel
class ProductNamesViewModel @Inject constructor(catalog: ProductCatalog) : ViewModel() {

    data class Lists(val entries: List<ProductMerge.Entry>, val hidden: List<ProductMerge.Entry>)

    /** 一覧と、一番下の「非表示の商品」。読み込み中は null */
    val lists: StateFlow<Lists?> = catalog.observe()
        .map {
            Lists(
                entries = ProductMerge.entries(it.products, it.visibleIds, it.dictionary, it.hidden),
                hidden = ProductMerge.hiddenEntries(it.products, it.visibleIds, it.dictionary, it.hidden),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)
}

/** 商品を開いた画面。まとめている商品と個別の解除、辞書の名寄せ。統合されている商品なら、統合先と解除 */
@HiltViewModel
class ProductDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val catalog: ProductCatalog,
    private val productRepository: ProductRepository,
) : ViewModel() {

    val productId = ProductId(checkNotNull(savedStateHandle.get<String>(ProductDetailRoute::productId.name)))

    sealed interface State {
        data object Loading : State
        /** 見えない商品や、なくなった商品 */
        data object NotFound : State
        data class Loaded(val detail: ProductMerge.Detail) : State
    }

    val state: StateFlow<State> = catalog.observe()
        .map { snapshot ->
            ProductMerge.detail(productId, snapshot.products, snapshot.visibleIds, snapshot.dictionary, snapshot.hidden)
                ?.let(State::Loaded) ?: State.NotFound
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), State.Loading)

    sealed interface Event {
        /** 名前を訂正した。訂正した名前の商品（統合先）の画面に移る */
        data class Renamed(val target: ProductId) : Event
    }

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events: Flow<Event> = _events.receiveAsFlow()

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
    fun hide() {
        viewModelScope.launch {
            ProductMerge.hideChanges(productId, catalog.observe().first().products)?.let { productRepository.applyMergeChanges(it) }
        }
    }

    fun show() {
        viewModelScope.launch { productRepository.applyMergeChanges(listOf(ProductMerge.Change.Show(productId))) }
    }

    /** [id] の統合を解除する。[id] にまとまっていた商品は、元の統合先に残す */
    fun unmerge(id: ProductId) {
        viewModelScope.launch {
            val products = catalog.observe().first().products
            ProductMerge.unmergeChanges(id, products)?.let { productRepository.applyMergeChanges(it) }
        }
    }

    /** 時期が近い商品の提案に出さない（[excluded] が false なら出す）。補完には出す */
    fun setSuggestionExcluded(excluded: Boolean) {
        viewModelScope.launch { productRepository.setSuggestionExcluded(productId, excluded) }
    }

    fun setDictionaryOptOut(optOut: Boolean) {
        viewModelScope.launch { productRepository.setDictionaryOptOut(productId, optOut) }
    }
}

/**
 * 「商品をまとめる」。統合先を固定し、ほかの商品を複数選んで一度にまとめる。選んだ商品にまとまっている商品も一緒に移る
 */
@HiltViewModel
class MergeProductsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val catalog: ProductCatalog,
    private val productRepository: ProductRepository,
) : ViewModel() {

    private val targetId = ProductId(checkNotNull(savedStateHandle.get<String>(MergeProductsRoute::targetId.name)))

    data class State(
        /** 統合先。読み込み中や、統合済み・なくなった場合は null */
        val target: ProductMerge.Entry? = null,
        val candidates: List<ProductMerge.Entry> = emptyList(),
        val selected: Set<ProductId> = emptySet(),
        val loading: Boolean = true,
        /** まとめる操作を送った。二重に送らない */
        val submitted: Boolean = false,
    ) {
        val selectedEntries: List<ProductMerge.Entry> get() = candidates.filter { it.product.id in selected }
        val canMerge: Boolean get() = target != null && selected.isNotEmpty() && !submitted
    }

    sealed interface Event {
        /** [ProductMerge.MAX_SELECTION] 件を超えて選ぼうとした */
        data object TooMany : Event
        /** まとめ終えた（書き込みを送った）。統合先の画面に戻る */
        data object Done : Event
    }

    private val selected = MutableStateFlow(emptySet<ProductId>())
    private val submitted = MutableStateFlow(false)
    private val _events = Channel<Event>(Channel.BUFFERED)
    val events: Flow<Event> = _events.receiveAsFlow()

    val state: StateFlow<State> = combine(catalog.observe(), selected, submitted) { snapshot, selected, submitted ->
        val entries = ProductMerge.entries(snapshot.products, snapshot.visibleIds, snapshot.dictionary, snapshot.hidden)
        val candidates = entries.filter { it.product.id != targetId }
        State(
            target = entries.find { it.product.id == targetId },
            candidates = candidates,
            // ほかの端末の変更で候補から外れた商品は、選択からも外す
            selected = selected.filterTo(HashSet()) { id -> candidates.any { it.product.id == id } },
            loading = false,
            submitted = submitted,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), State())

    fun toggle(id: ProductId) {
        val current = state.value.selected
        if (id !in current && current.size >= ProductMerge.MAX_SELECTION) {
            _events.trySend(Event.TooMany)
            return
        }
        selected.update { if (id in current) current - id else current + id }
    }

    fun merge() {
        val current = state.value
        if (!current.canMerge) return
        submitted.value = true
        viewModelScope.launch {
            val products = catalog.observe().first().products
            val changes = ProductMerge.mergeChanges(targetId, current.selected, products)
            if (changes == null) {
                // 送る前に、ほかの端末の変更で統合先や選んだ商品が統合された
                submitted.value = false
                return@launch
            }
            productRepository.applyMergeChanges(changes)
            _events.send(Event.Done)
        }
    }
}
