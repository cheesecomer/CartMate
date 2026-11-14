package com.cheesecomer.cartmate.ui.listdetail

import com.cheesecomer.cartmate.domain.model.ProductId
import com.cheesecomer.cartmate.domain.model.UserId
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.distinctUntilChanged
import com.cheesecomer.cartmate.ui.suggestions.JudgementRequester
import com.cheesecomer.cartmate.ui.suggestions.SuggestionSource
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cheesecomer.cartmate.domain.input.AddItemEffect
import com.cheesecomer.cartmate.domain.input.AddItemEvent
import com.cheesecomer.cartmate.domain.input.AddItemReducer
import com.cheesecomer.cartmate.domain.input.AddItemState
import com.cheesecomer.cartmate.domain.model.Item
import com.cheesecomer.cartmate.domain.model.ItemId
import com.cheesecomer.cartmate.domain.model.ItemStatus
import com.cheesecomer.cartmate.domain.suggest.EarlyPurchaseNotice
import com.cheesecomer.cartmate.domain.model.ListId
import com.cheesecomer.cartmate.domain.model.ListStatus
import com.cheesecomer.cartmate.domain.model.NewItem
import com.cheesecomer.cartmate.domain.model.ShoppingList
import com.cheesecomer.cartmate.domain.model.Visibility
import com.cheesecomer.cartmate.domain.repository.CompleteResult
import com.cheesecomer.cartmate.domain.repository.DeleteItemResult
import com.cheesecomer.cartmate.domain.repository.DeleteListResult
import com.cheesecomer.cartmate.domain.repository.ItemRepository
import com.cheesecomer.cartmate.domain.repository.ReopenResult
import com.cheesecomer.cartmate.domain.repository.ShoppingListRepository
import com.cheesecomer.cartmate.domain.visibility.VisibilityDefaults
import com.cheesecomer.cartmate.ui.itementry.ItemEntryResult
import com.cheesecomer.cartmate.ui.itementry.LastVisibilityStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import com.cheesecomer.cartmate.domain.list.ShoppingRound
import com.cheesecomer.cartmate.domain.list.ListDisplay
import com.cheesecomer.cartmate.domain.notice.MemberNames
import com.cheesecomer.cartmate.domain.notice.OtherChecks
import com.cheesecomer.cartmate.domain.repository.CurrentUserProvider
import com.cheesecomer.cartmate.domain.repository.MemberDirectory
import com.cheesecomer.cartmate.domain.repository.RejectedCheck
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import java.time.Instant
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.merge

data class ListDetailUiState(
    val listName: String = "",
    val listMemo: String? = null,
    val listVisibility: Visibility = Visibility.SHARED,
    /** 未購入の項目と、直近24時間にチェックした項目（並び順。チェック済みも元の位置に表示する） */
    val items: List<Item> = emptyList(),
    val addItem: AddItemState = AddItemState.Initial,
    val loading: Boolean = true,
    /** Enter と「＋」で追加するときの公開範囲。サジェストのシートに表示し、共有のリストでは切り替えられる（§3.4） */
    val addVisibility: Visibility = Visibility.SHARED,
    /** 家族のメンバーの名前。チェック済みの項目に、チェックした人を表示する */
    val memberNames: MemberNames = MemberNames(emptyList()),
    /** サジェストのシートの候補 */
    val suggestions: SheetSuggestions = SheetSuggestions.Loading,
    /** 自分。自分がチェックした項目にだけ、チェックを外す操作を出す */
    val me: UserId? = null,
    /** 買い終わったリストに追加したので、買い忘れか次の買い物かを聞く（docs/requirements.md §3.2「前回の買い物の片付け」） */
    val asksRound: Boolean = false,
    /** 未購入の項目のうち、早すぎる再購入に当たるもの。カードに「前回 10/18 に購入・いつもは約14日ごと」と出す（§4.5） */
    val earlyNotices: Map<ItemId, EarlyPurchaseNotice> = emptyMap(),
) {
    /** [item] をチェックした人の名前。家族にいない人（退会したメンバー）は null */
    fun checkerName(item: Item): String? = item.completedBy?.let(memberNames::of)

    /** 自分がチェックした。チェックした人を、名前ではなく「あなた」と表示する */
    fun checkedByMe(item: Item): Boolean = item.completedBy != null && item.completedBy == me

    /** チェックを外せるのは、チェックした本人だけ */
    fun canUncheck(item: Item): Boolean = checkedByMe(item)

    val addVisibilitySelectable: Boolean get() = VisibilityDefaults.selectable(listVisibility)

    val openCount: Int get() = items.count { it.status == ItemStatus.OPEN }
    val completedCount: Int get() = items.count { it.status == ItemStatus.COMPLETED }
}

/** Snackbar で一度だけ表示するメッセージ */
sealed interface DetailMessage {
    /** 削除した。元に戻せる */
    data class Deleted(val item: Item) : DetailMessage
    /** すでにチェック済みだった。「牛乳は 花子さんが 10:02 にチェック済みです」。[checkerName] が null なら退会したメンバー */
    data class AlreadyCompleted(val itemName: String, val checkerName: String?, val at: Instant) : DetailMessage
    /** 開いている間に、ほかのメンバーがチェックした。「花子さんが牛乳ほか2件をチェックしました」 */
    data class OthersChecked(val checkerName: String?, val firstItemName: String, val othersCount: Int) : DetailMessage
    /** 送ったチェックを反映できなかった（オフラインのまま7日を過ぎた など） */
    data class CheckNotApplied(val itemName: String) : DetailMessage
    data object AlreadyOpen : DetailMessage
    /** ほかのメンバーがチェックした項目は外せない。[checkerName] が null なら退会したメンバー */
    data class CheckedByOther(val checkerName: String?) : DetailMessage
    data class CannotDeleteCompleted(val item: Item) : DetailMessage
    /** 編集中に、ほかの操作でチェックされていた */
    data object CannotEditCompleted : DetailMessage
    /** 編集中に、ほかの操作で削除されていた */
    data object EditedItemNotFound : DetailMessage
    /** のこりの商品があるため、リストを削除できない */
    data object CannotDeleteList : DetailMessage
    data object Failed : DetailMessage
}

/** 画面の移動 */
sealed interface DetailNavigation {
    /** 新しい商品の商品詳細入力画面を開く */
    data class OpenEntry(val name: String, val quantity: Double?, val unit: String?) : DetailNavigation
    /** リストを削除したので一覧に戻る */
    data object ListDeleted : DetailNavigation

    /** 候補の長押しで、商品の画面（商品名の管理）を開く */
    data class OpenProduct(val productId: ProductId) : DetailNavigation
}

@HiltViewModel
class ListDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val listRepository: ShoppingListRepository,
    private val itemRepository: ItemRepository,
    private val lastVisibility: LastVisibilityStore,
    private val clock: Clock,
    private val currentUser: CurrentUserProvider,
    private val memberDirectory: MemberDirectory,
    suggestionSource: SuggestionSource,
    /** 購入のあとに AI の判定を頼む（docs/design/suggestions.md「問い合わせる条件」） */
    private val judgementRequester: JudgementRequester = JudgementRequester.NONE,
) : ViewModel() {

    private val members: Flow<MemberNames> = memberDirectory.observeNames()

    private val listId = ListId(checkNotNull(savedStateHandle.get<String>(ListDetailRoute::listId.name)))
    /** 開く前の画面で表示していたリスト名。読み込みを待たずに、最初の画面からタイトルに出す（フィードバック #021） */
    private val routeListName = savedStateHandle.get<String>(ListDetailRoute::listName.name).orEmpty()

    private val addItemState = MutableStateFlow(AddItemState.Initial)
    private val asksRound = MutableStateFlow(false)
    private val _messages = Channel<DetailMessage>(Channel.BUFFERED)
    /** 画面の操作の結果と、ほかのメンバーのチェック・拒否されたチェックの通知。受け取り始めたときに通知の購読を始める */
    val messages: Flow<DetailMessage> = merge(_messages.receiveAsFlow(), notices())
    private val _navigation = Channel<DetailNavigation>(Channel.BUFFERED)
    val navigation: Flow<DetailNavigation> = _navigation.receiveAsFlow()

    val uiState: StateFlow<ListDetailUiState> = combine(
        listRepository.observeLists().map { lists -> lists.firstOrNull { it.id == listId } },
        listItems(),
        addItemState,
    ) { listOrDeleted, (items, names), addItem ->
        // 削除したリストでも、未購入の項目が残っている間は開ける（docs/design/sync.md「リスト削除の競合」）
        val list = listOrDeleted?.takeIf { ListDisplay.isShown(it, items.count { item -> item.status == ItemStatus.OPEN }) }
        ListDetailUiState(
            listName = list?.name.orEmpty(),
            listMemo = list?.memo,
            listVisibility = list?.visibility ?: Visibility.SHARED,
            // 前回の買い物のチェック済みは、片付けたあとは表示しない
            items = ShoppingRound.visible(items, windowStart = clock.instant().minus(COMPLETED_WINDOW), clearedAt = list?.clearedAt),
            addItem = addItem,
            loading = false,
            memberNames = names,
        )
    }.combine(suggestions(suggestionSource)) { state, suggestions -> state.copy(suggestions = suggestions, me = currentUser.currentUserId()) }
        .combine(lastVisibility.observe(listId)) { state, remembered ->
            state.copy(addVisibility = VisibilityDefaults.initial(state.listVisibility, remembered))
        }
        .combine(asksRound) { state, asks -> state.copy(asksRound = asks) }
        .combine(earlyNotices(suggestionSource)) { state, notices -> state.copy(earlyNotices = notices) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ListDetailUiState(listName = routeListName))

    /**
     * 項目と、チェックした人の名前。端末のキャッシュから項目の購読が届くまでは、リスト一覧を表示している間に読んでおいた
     * 家族の未購入と最近チェックした項目、メンバーの名前から、このリストの分を先に出す（開いた直後に白紙にせず、
     * チェック済みの項目もあとから現れない。フィードバック #021）。読んでおいたものがなければ、購読が届くのを待つ
     */
    private fun listItems(): Flow<Pair<List<Item>, MemberNames>> {
        val loaded = combine(
            itemRepository.observeListItems(listId, completedSince = clock.instant().minus(COMPLETED_WINDOW)),
            members,
            ::Pair,
        )
        val preview = combine(
            itemRepository.observeRecentItems().map { items -> items.filter { it.listId == listId } },
            members,
            ::Pair,
        )
        return loaded.precededBy(preview)
    }

    /**
     * 開いている間に、ほかのメンバーがチェックしたら、約3秒分をチェックした人ごとに1件にまとめて知らせる
     * （docs/design/ui.md「他のメンバーによるチェックの通知」）。送ったチェックが拒否された場合も知らせる
     */
    private fun notices(): Flow<DetailMessage> = channelFlow {
        var names = MemberNames(emptyList())
        launch { members.collect { names = it } }
        launch {
            itemRepository.observeRejectedChecks().collect { rejected ->
                send(
                    when (rejected) {
                        is RejectedCheck.AlreadyCompleted -> DetailMessage.AlreadyCompleted(rejected.itemName, names.of(rejected.by), rejected.at)
                        is RejectedCheck.NotApplied -> DetailMessage.CheckNotApplied(rejected.itemName)
                    },
                )
            }
        }
        val me = currentUser.currentUserId()
        val pending = mutableListOf<Item>()
        var flush: Job? = null
        var previous: List<Item>? = null
        itemRepository.observeListItems(listId, completedSince = clock.instant().minus(COMPLETED_WINDOW)).collect { items ->
            val checked = OtherChecks.newlyChecked(previous, items, me)
            previous = items
            if (checked.isEmpty()) return@collect
            pending += checked
            if (flush?.isActive == true) return@collect
            flush = launch {
                delay(OTHERS_CHECKED_WINDOW_MILLIS)
                val summaries = OtherChecks.summarize(pending.toList())
                pending.clear()
                summaries.forEach { send(DetailMessage.OthersChecked(names.of(it.checker), it.firstItemName, it.othersCount)) }
            }
        }
    }

    /**
     * シートの候補。未入力なら時期が近い商品とよく買う商品、入力中なら補完（docs/design/suggestions.md）。
     * 計算が終わるまでは読み込み中を流し、項目の一覧の表示を待たせない（フィードバック #016）
     */
    private fun suggestions(source: SuggestionSource): Flow<SheetSuggestions> = combine(
        source.observe(),
        addItemState.map { it.text }.distinctUntilChanged(),
        listRepository.observeLists().map { lists -> lists.associate { it.id to it.name } }.distinctUntilChanged(),
    ) { engine, text, listNames -> SheetSuggestions.of(engine, text, listNames[listId].orEmpty(), listId, listNames) }
        .flowOn(source.dispatcher)
        .onStart { emit(SheetSuggestions.Loading) }

    /** 項目のカードに出す早すぎる再購入。計算が終わるまでは出さず、項目の一覧の表示を待たせない */
    private fun earlyNotices(source: SuggestionSource): Flow<Map<ItemId, EarlyPurchaseNotice>> =
        source.observe()
            .map { it.earlyNotices() }
            .distinctUntilChanged()
            .flowOn(source.dispatcher)
            .onStart { emit(emptyMap()) }

    /** サジェストのシートで公開範囲を切り替える。そのリストの次の追加の初期値になる（docs/requirements.md §3.4） */
    fun setAddVisibility(visibility: Visibility) {
        if (uiState.value.addVisibilitySelectable) lastVisibility.set(listId, visibility)
    }

    fun onAddItemEvent(event: AddItemEvent) {
        val transition = AddItemReducer.reduce(addItemState.value, event)
        addItemState.value = transition.state
        when (val effect = transition.effect) {
            // Enter と候補の「＋」は、そのリストの直前の公開範囲で追加する（直前の公開範囲は変えない）
            is AddItemEffect.AddItem -> viewModelScope.launch {
                val list = listRepository.observeLists().first().firstOrNull { it.id == listId } ?: return@launch
                val visibility = VisibilityDefaults.initial(list.visibility, lastVisibility.get(listId))
                if (itemRepository.addItem(NewItem(listId, effect.name, effect.quantity, effect.unit, effect.memo, visibility)).asksRound) {
                    asksRound.value = true
                }
            }
            is AddItemEffect.OpenEntry -> _navigation.trySend(DetailNavigation.OpenEntry(effect.name, effect.quantity, effect.unit))
            is AddItemEffect.OpenProduct -> _navigation.trySend(DetailNavigation.OpenProduct(effect.productId))
            null -> Unit
        }
    }

    /** 商品詳細入力画面から戻った */
    fun onEntryResult(result: ItemEntryResult) {
        when (result) {
            ItemEntryResult.ADDED -> onAddItemEvent(AddItemEvent.EntryClosed(added = true))
            ItemEntryResult.ADDED_ASKS_ROUND -> {
                onAddItemEvent(AddItemEvent.EntryClosed(added = true))
                asksRound.value = true
            }
            ItemEntryResult.CANCELLED -> onAddItemEvent(AddItemEvent.EntryClosed(added = false))
            ItemEntryResult.UPDATED -> Unit
            ItemEntryResult.EDIT_REJECTED_COMPLETED -> _messages.trySend(DetailMessage.CannotEditCompleted)
            ItemEntryResult.EDIT_REJECTED_NOT_FOUND -> _messages.trySend(DetailMessage.EditedItemNotFound)
        }
    }

    /**
     * 買い忘れか次の買い物かの答え。次の買い物なら前回のチェック済みを片付ける。買い忘れか、答えずに閉じたら片付けない
     * （片付けは元に戻せないため、残す方にする）
     */
    fun answerRound(nextShopping: Boolean) {
        asksRound.value = false
        if (nextShopping) viewModelScope.launch { listRepository.clearRound(listId) }
    }

    /** チェックする。できたことは知らせない。チェックを外すのはカードからできる（フィードバック #019） */
    fun check(item: Item) {
        viewModelScope.launch {
            when (val result = itemRepository.completeItem(item.id, checkedAt = clock.instant())) {
                CompleteResult.Completed -> judgementRequester.onPurchased(item)
                is CompleteResult.AlreadyCompleted -> {
                    val names = memberDirectory.observeNames().first()
                    _messages.send(DetailMessage.AlreadyCompleted(item.inputName, names.of(result.by), result.at))
                }
                is CompleteResult.Retry, is CompleteResult.Rejected -> _messages.send(DetailMessage.Failed)
            }
        }
    }

    /**
     * チェックを外す（Undo）。ほかのメンバーがチェックした項目は外さずに理由を知らせる
     * （その人の購入の記録を予測から外させない。docs/design/sync.md「Undo」）
     */
    fun uncheck(item: Item) {
        viewModelScope.launch {
            val checker = item.completedBy
            if (checker != null && checker != currentUser.currentUserId()) {
                _messages.send(DetailMessage.CheckedByOther(memberDirectory.observeNames().first().of(checker)))
                return@launch
            }
            when (itemRepository.reopenItem(item.id)) {
                ReopenResult.Reopened -> Unit
                ReopenResult.AlreadyOpen -> _messages.send(DetailMessage.AlreadyOpen)
                is ReopenResult.Retry, is ReopenResult.Rejected -> _messages.send(DetailMessage.Failed)
            }
        }
    }

    fun move(id: ItemId, previous: ItemId?, next: ItemId?) {
        viewModelScope.launch { itemRepository.moveItem(id, previous, next) }
    }

    fun delete(item: Item) {
        viewModelScope.launch {
            when (itemRepository.deleteItem(item.id)) {
                DeleteItemResult.Deleted -> _messages.send(DetailMessage.Deleted(item))
                DeleteItemResult.AlreadyCompleted -> _messages.send(DetailMessage.CannotDeleteCompleted(item))
                DeleteItemResult.NotFound -> Unit
            }
        }
    }

    fun undoDelete(item: Item) {
        viewModelScope.launch { itemRepository.restoreItem(item) }
    }

    fun updateList(name: String, memo: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.length > ShoppingList.MAX_NAME_LENGTH) return
        val trimmedMemo = memo.trim().take(ShoppingList.MAX_MEMO_LENGTH).ifEmpty { null }
        viewModelScope.launch { listRepository.updateList(listId, trimmed, trimmedMemo) }
    }

    /** 「リストを削除」を選んだ。のこりの商品があれば理由を伝え、なければ true を返して確認ダイアログを出させる */
    fun canDeleteList(): Boolean {
        if (uiState.value.openCount == 0) return true
        _messages.trySend(DetailMessage.CannotDeleteList)
        return false
    }

    fun deleteList() {
        viewModelScope.launch {
            when (runCatching { listRepository.deleteList(listId) }.getOrNull()) {
                DeleteListResult.Deleted, DeleteListResult.NotFound -> _navigation.send(DetailNavigation.ListDeleted)
                DeleteListResult.NotEmpty -> _messages.send(DetailMessage.CannotDeleteList)
                // 通信の問題など
                null -> _messages.send(DetailMessage.Failed)
            }
        }
    }

    /** この Flow の最初の値が届くまでは、[preview] の最初の値を流す。届いたら [preview] はやめる */
    private fun <T> Flow<T>.precededBy(preview: Flow<T>): Flow<T> = channelFlow {
        val early = launch { preview.firstOrNull()?.let { send(it) } }
        collect {
            early.cancelAndJoin()
            send(it)
        }
    }

    private companion object {
        const val OTHERS_CHECKED_WINDOW_MILLIS = 3_000L
        val COMPLETED_WINDOW: Duration = Duration.ofHours(24)
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
