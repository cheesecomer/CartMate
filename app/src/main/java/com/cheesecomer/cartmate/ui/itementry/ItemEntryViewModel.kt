package com.cheesecomer.cartmate.ui.itementry

import kotlinx.coroutines.flow.distinctUntilChanged
import com.cheesecomer.cartmate.domain.suggest.SuggestionEngine
import com.cheesecomer.cartmate.ui.suggestions.SuggestionSource
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cheesecomer.cartmate.domain.input.ItemEntry
import com.cheesecomer.cartmate.domain.model.Item
import com.cheesecomer.cartmate.domain.model.ItemId
import com.cheesecomer.cartmate.domain.model.ItemStatus
import com.cheesecomer.cartmate.domain.model.ListId
import com.cheesecomer.cartmate.domain.model.NewItem
import com.cheesecomer.cartmate.domain.model.Visibility
import com.cheesecomer.cartmate.domain.repository.ItemRepository
import com.cheesecomer.cartmate.domain.repository.ItemUpdate
import com.cheesecomer.cartmate.domain.repository.ShoppingListRepository
import com.cheesecomer.cartmate.domain.repository.UpdateItemResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import com.cheesecomer.cartmate.domain.repository.MemberDirectory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.cheesecomer.cartmate.domain.model.ProductId
import com.cheesecomer.cartmate.domain.product.MergeCandidates
import com.cheesecomer.cartmate.domain.product.ProductHiding
import com.cheesecomer.cartmate.domain.product.ProductUsage
import com.cheesecomer.cartmate.domain.repository.ProductRepository
import com.cheesecomer.cartmate.domain.repository.PurchaseRepository
import kotlinx.coroutines.flow.combine
import com.cheesecomer.cartmate.domain.repository.DictionaryProvider

/**
 * 商品詳細入力画面。[itemId] があれば編集、なければ [name]・[quantity]・[unit] を初期値にした新規。
 * [quantity] は Navigation の引数にするため文字列で渡す
 */
@Serializable
data class ItemEntryRoute(
    val listId: String,
    val itemId: String? = null,
    val name: String = "",
    val quantity: String? = null,
    val unit: String? = null,
)

/** 新しい商品の追加で、入力した名前の商品の前回の購入と備考の候補（docs/design/ui.md「商品詳細入力画面」） */
data class EntryHistory(
    /** 購入の記録がなければ null */
    val previous: SuggestionEngine.PreviousPurchase?,
    val memoCandidates: List<String>,
)

/** 表記ゆれのチップ「『[candidateName]』と同じ商品として記録する」 */
data class MergeChip(val candidateId: ProductId, val candidateName: String, val selected: Boolean)

data class ItemEntryUiState(
    val listName: String = "",
    val listVisibility: Visibility = Visibility.SHARED,
    /** 読み込み中は null */
    val entry: ItemEntry? = null,
)

@HiltViewModel
class ItemEntryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val listRepository: ShoppingListRepository,
    private val itemRepository: ItemRepository,
    private val lastVisibility: LastVisibilityStore,
    private val clock: Clock,
    memberDirectory: MemberDirectory,
    productRepository: ProductRepository,
    purchaseRepository: PurchaseRepository,
    dictionaryProvider: DictionaryProvider,
    suggestionSource: SuggestionSource,
) : ViewModel() {

    /** 「追加先: スーパー（共有リスト 3人）」の人数 */
    val memberCount: StateFlow<Int> = memberDirectory.observeMembers().map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), 0)

    private val listId = ListId(checkNotNull(savedStateHandle.get<String>(ItemEntryRoute::listId.name)))
    private val itemId = savedStateHandle.get<String>(ItemEntryRoute::itemId.name)?.let(::ItemId)

    private val _uiState = MutableStateFlow(ItemEntryUiState())
    val uiState: StateFlow<ItemEntryUiState> = _uiState.asStateFlow()

    /** チップで選んだ候補。候補が変わったら選択を外すため、候補の ID で持つ */
    private val selectedCandidate = MutableStateFlow<ProductId?>(null)

    /** 自分に見える項目と購入の記録から分かる、商品の使われ方（他のメンバーがプライベートでだけ使った名前を出さない） */
    private val productUsage: Flow<ProductUsage> = combine(
        itemRepository.observeOpenItems(),
        purchaseRepository.observePurchases(since = clock.instant().minus(PURCHASE_WINDOW)),
    ) { items, purchases -> ProductUsage.of(items, purchases) }

    /**
     * 表記ゆれのチップ（docs/design/suggestions.md「表記ゆれの紐付け」）。新しい商品の追加で、入力した名前が新しい商品のときだけ出す。
     * 初期状態は未選択
     */
    val mergeChip: StateFlow<MergeChip?> = combine(
        _uiState,
        productRepository.observeProducts(),
        productUsage,
        selectedCandidate,
        dictionaryProvider.observeDictionary(),
    ) { state, products, usage, selected, dictionary ->
        val entry = state.entry?.takeIf { it.editing == null } ?: return@combine null
        // 非表示の商品は候補にしない
        val hidden = ProductHiding.hiddenRoots(products, usage)
        val visible = products.filter { it.id in usage.visibleIds && !ProductHiding.isHidden(it.id, products, hidden) }
        val candidate = MergeCandidates.find(entry.name, visible, products, dictionary)
            ?: return@combine null
        MergeChip(candidate.id, candidate.displayName, selected = selected == candidate.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /** 前回の購入と備考の候補。新しい商品の追加で、商品名があるときだけ（編集では出さない） */
    val history: StateFlow<EntryHistory?> = combine(
        suggestionSource.observe(),
        _uiState.map { state -> state.entry?.takeIf { it.editing == null }?.name?.trim() }.distinctUntilChanged(),
    ) { engine, name ->
        if (name.isNullOrEmpty()) null else EntryHistory(engine.previous(name), engine.memoCandidates(name))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /** 備考の候補を選んだ。備考を候補の文で置き換える */
    fun selectMemo(memo: String) = onMemoChange(memo)

    /** チップの選択を切り替える */
    fun toggleMerge() {
        val chip = mergeChip.value ?: return
        selectedCandidate.value = if (chip.selected) null else chip.candidateId
    }

    private val _results = Channel<ItemEntryResult>(Channel.BUFFERED)
    /** 画面を閉じるときの結果 */
    val results: Flow<ItemEntryResult> = _results.receiveAsFlow()

    init {
        viewModelScope.launch {
            val list = listRepository.observeLists().first().firstOrNull { it.id == listId }
            if (list == null) {
                _results.send(ItemEntryResult.CANCELLED)
                return@launch
            }
            val entry = if (itemId == null) {
                ItemEntry.forNew(
                    name = savedStateHandle.get<String>(ItemEntryRoute::name.name).orEmpty(),
                    quantity = savedStateHandle.get<String>(ItemEntryRoute::quantity.name)?.toDoubleOrNull(),
                    unit = savedStateHandle.get<String>(ItemEntryRoute::unit.name),
                    listVisibility = list.visibility,
                    remembered = lastVisibility.get(listId),
                )
            } else {
                val item = itemRepository.observeListItems(listId, completedSince = clock.instant().minus(COMPLETED_WINDOW))
                    .first()
                    .firstOrNull { it.id == itemId }
                when {
                    item == null -> return@launch _results.send(ItemEntryResult.EDIT_REJECTED_NOT_FOUND)
                    item.status == ItemStatus.COMPLETED -> return@launch _results.send(ItemEntryResult.EDIT_REJECTED_COMPLETED)
                    else -> ItemEntry.forEdit(item)
                }
            }
            _uiState.value = ItemEntryUiState(listName = list.name, listVisibility = list.visibility, entry = entry)
        }
    }

    fun onNameChange(text: String) = updateEntry { it.copy(name = text.take(Item.MAX_NAME_LENGTH)) }

    fun onQuantityChange(text: String) = updateEntry { it.copy(quantity = text) }

    fun increment() = updateEntry(ItemEntry::increment)

    fun decrement() = updateEntry(ItemEntry::decrement)

    fun onUnitChange(text: String) = updateEntry { it.copy(unit = text.take(Item.MAX_UNIT_LENGTH)) }

    fun onMemoChange(text: String) = updateEntry { it.copy(memo = text.take(Item.MAX_MEMO_LENGTH)) }

    fun onVisibilityChange(visibility: Visibility) = updateEntry { it.withVisibility(visibility) }

    /** 確定したあとの2回目のタップで、同じ商品を二重に追加しないようにする */
    private var confirmed = false

    fun confirm() {
        val entry = _uiState.value.entry ?: return
        val values = entry.confirm() ?: return
        if (confirmed) return
        confirmed = true
        viewModelScope.launch {
            val editing = entry.editing
            if (editing == null) {
                // 選んだ公開範囲を、このリストでの次の追加の初期値にする
                if (entry.visibilitySelectable) lastVisibility.set(listId, values.visibility)
                // チップを選んでいれば、新しい商品を最初から候補の商品に統合する
                val mergeInto = mergeChip.value?.takeIf { it.selected }?.candidateId
                itemRepository.addItem(NewItem(listId, values.name, values.quantity, values.unit, values.memo, values.visibility, mergeInto))
                _results.send(ItemEntryResult.ADDED)
            } else {
                val update = ItemUpdate(values.name, values.quantity, values.unit, values.memo)
                val result = when (itemRepository.updateItem(editing, update)) {
                    UpdateItemResult.Updated -> ItemEntryResult.UPDATED
                    UpdateItemResult.AlreadyCompleted -> ItemEntryResult.EDIT_REJECTED_COMPLETED
                    UpdateItemResult.NotFound -> ItemEntryResult.EDIT_REJECTED_NOT_FOUND
                }
                _results.send(result)
            }
        }
    }

    private fun updateEntry(transform: (ItemEntry) -> ItemEntry) {
        _uiState.update { state -> state.entry?.let { state.copy(entry = transform(it)) } ?: state }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        val COMPLETED_WINDOW: Duration = Duration.ofHours(24)
        val PURCHASE_WINDOW: Duration = Duration.ofDays(365)
    }
}
