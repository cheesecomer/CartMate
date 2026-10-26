package com.cheesecomer.cartmate.ui.listdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cheesecomer.cartmate.R
import com.cheesecomer.cartmate.domain.input.AddItemEvent
import com.cheesecomer.cartmate.domain.input.AddItemState
import com.cheesecomer.cartmate.domain.model.Item
import com.cheesecomer.cartmate.domain.model.ItemId
import com.cheesecomer.cartmate.domain.model.ItemStatus
import com.cheesecomer.cartmate.domain.model.ListId
import com.cheesecomer.cartmate.domain.model.ShoppingList
import com.cheesecomer.cartmate.domain.model.UserId
import com.cheesecomer.cartmate.domain.model.Visibility
import com.cheesecomer.cartmate.ui.components.SheetPreviewFrame
import com.cheesecomer.cartmate.ui.components.rememberDraggableOrder
import com.cheesecomer.cartmate.ui.itementry.ItemEntryResult
import com.cheesecomer.cartmate.ui.theme.CartMateSpacing
import com.cheesecomer.cartmate.ui.theme.CartMateTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.time.Instant

@Serializable
data class ListDetailRoute(val listId: String)

/**
 * @param entryResults 商品詳細入力画面から戻ったときの結果
 * @param onOpenEntry 新しい商品の商品詳細入力画面を開く
 * @param onEditItem 項目の商品詳細入力画面を編集モードで開く
 */
@Composable
fun ListDetailScreen(
    onBack: () -> Unit,
    onOpenEntry: (name: String, quantity: Double?, unit: String?) -> Unit,
    onEditItem: (ItemId) -> Unit,
    entryResults: Flow<ItemEntryResult> = emptyFlow(),
    viewModel: ListDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            // 新しいメッセージが来たら、表示中のものは閉じる
            snackbarHostState.currentSnackbarData?.dismiss()
            launch {
                val (text, undo) = when (message) {
                    is DetailMessage.Checked ->
                        resources.getString(R.string.detail_checked_message, message.item.inputName) to
                            { viewModel.uncheck(message.item) }
                    is DetailMessage.Deleted ->
                        resources.getString(R.string.detail_deleted_message, message.item.inputName) to
                            { viewModel.undoDelete(message.item) }
                    is DetailMessage.AlreadyCompleted ->
                        resources.getString(R.string.detail_already_completed, message.item.inputName) to null
                    DetailMessage.AlreadyOpen -> resources.getString(R.string.detail_already_open) to null
                    is DetailMessage.CannotDeleteCompleted ->
                        resources.getString(R.string.detail_cannot_delete_completed, message.item.inputName) to null
                    DetailMessage.CannotEditCompleted -> resources.getString(R.string.detail_cannot_edit_completed) to null
                    DetailMessage.EditedItemNotFound -> resources.getString(R.string.detail_edited_item_not_found) to null
                    DetailMessage.CannotDeleteList -> resources.getString(R.string.detail_cannot_delete_list) to null
                    DetailMessage.Failed -> resources.getString(R.string.detail_operation_failed) to null
                }
                val result = snackbarHostState.showSnackbar(
                    message = text,
                    actionLabel = undo?.let { resources.getString(R.string.action_undo) },
                    duration = SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) undo?.invoke()
            }
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.navigation.collect { navigation ->
            when (navigation) {
                is DetailNavigation.OpenEntry -> onOpenEntry(navigation.name, navigation.quantity, navigation.unit)
                DetailNavigation.ListDeleted -> onBack()
            }
        }
    }
    LaunchedEffect(viewModel, entryResults) { entryResults.collect(viewModel::onEntryResult) }

    ListDetailContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onAddItemEvent = viewModel::onAddItemEvent,
        onCheck = viewModel::check,
        onUncheck = viewModel::uncheck,
        onEdit = { onEditItem(it.id) },
        onDelete = viewModel::delete,
        onMove = viewModel::move,
        onUpdateList = viewModel::updateList,
        onRequestDeleteList = viewModel::canDeleteList,
        onDeleteList = viewModel::deleteList,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListDetailContent(
    state: ListDetailUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onAddItemEvent: (AddItemEvent) -> Unit,
    onCheck: (Item) -> Unit,
    onUncheck: (Item) -> Unit,
    onEdit: (Item) -> Unit,
    onDelete: (Item) -> Unit,
    onMove: (ItemId, previous: ItemId?, next: ItemId?) -> Unit,
    onUpdateList: (name: String, memo: String) -> Unit,
    /** 削除できるなら true。できなければ理由を伝えて false */
    onRequestDeleteList: () -> Boolean,
    onDeleteList: () -> Unit,
) {
    var showEditDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

    // サジェストのシートは画面の上に重ねて表示する
    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(state.listName, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            if (state.listVisibility == Visibility.PRIVATE) {
                                Icon(
                                    painterResource(R.drawable.ic_lock),
                                    contentDescription = stringResource(R.string.private_label),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = CartMateSpacing.small).size(20.dp),
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back))
                        }
                    },
                    actions = {
                        ListMenu(
                            onEdit = { showEditDialog = true },
                            onDelete = { if (onRequestDeleteList()) showDeleteDialog = true },
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                AddItemField(
                    text = state.addItem.text,
                    onClick = { onAddItemEvent(AddItemEvent.FieldFocused) },
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                )
            },
        ) { innerPadding ->
            ItemList(
                state = state,
                contentPadding = innerPadding,
                onCheck = onCheck,
                onUncheck = onUncheck,
                onEdit = onEdit,
                onDelete = onDelete,
                onMove = onMove,
            )
        }

        (state.addItem as? AddItemState.Open)?.let { AddItemSheet(it, onAddItemEvent) }
    }

    if (showEditDialog) {
        EditListDialog(
            initialName = state.listName,
            initialMemo = state.listMemo.orEmpty(),
            onConfirm = { name, memo ->
                onUpdateList(name, memo)
                showEditDialog = false
            },
            onDismiss = { showEditDialog = false },
        )
    }
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.detail_delete_dialog_title)) },
            text = { Text(stringResource(R.string.detail_delete_dialog_message, state.listName)) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteList()
                    showDeleteDialog = false
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun ItemList(
    state: ListDetailUiState,
    contentPadding: PaddingValues,
    onCheck: (Item) -> Unit,
    onUncheck: (Item) -> Unit,
    onEdit: (Item) -> Unit,
    onDelete: (Item) -> Unit,
    onMove: (ItemId, previous: ItemId?, next: ItemId?) -> Unit,
) {
    val lazyListState = rememberLazyListState()
    val order = rememberDraggableOrder(state.items) { it.id.value }
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to -> order.move(from.key, to.key) }
    val moveUpLabel = stringResource(R.string.action_move_up)
    val moveDownLabel = stringResource(R.string.action_move_down)

    LazyColumn(state = lazyListState, modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
        if (!state.loading && state.items.isEmpty()) {
            item(key = "empty") {
                Box(Modifier.fillMaxWidth().padding(CartMateSpacing.xLarge), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.detail_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (state.items.isNotEmpty()) {
            item(key = "progress") { ProgressCard(openCount = state.openCount, completedCount = state.completedCount) }
            item(key = "hint") {
                Text(
                    stringResource(R.string.detail_tap_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = CartMateSpacing.small),
                    textAlign = TextAlign.Center,
                )
            }
        }
        // チェック済みの項目も元の位置に残し、打ち消し線で示す
        items(order.items, key = { it.id.value }) { item ->
            ReorderableItem(reorderState, key = item.id.value) { dragging ->
                val drag = RowDrag(
                    dragging = dragging,
                    handle = Modifier.longPressDraggableHandle(
                        onDragStarted = { order.startDrag() },
                        onDragStopped = {
                            order.finishDrag(item.id.value)?.let { onMove(item.id, it.previous?.id, it.next?.id) }
                        },
                    ),
                    actions = listOfNotNull(
                        order.moveUpTarget(item.id.value)?.let { target ->
                            CustomAccessibilityAction(moveUpLabel) {
                                onMove(item.id, target.previous?.id, target.next?.id)
                                true
                            }
                        },
                        order.moveDownTarget(item.id.value)?.let { target ->
                            CustomAccessibilityAction(moveDownLabel) {
                                onMove(item.id, target.previous?.id, target.next?.id)
                                true
                            }
                        },
                    ),
                )
                val showLock = item.showsLock(state.listVisibility)
                when (item.status) {
                    ItemStatus.OPEN -> OpenItemCard(
                        item = item,
                        showLock = showLock,
                        onEdit = { onEdit(item) },
                        onCheck = { onCheck(item) },
                        onDelete = { onDelete(item) },
                        drag = drag,
                    )
                    ItemStatus.COMPLETED ->
                        CompletedItemCard(item = item, showLock = showLock, onUncheck = { onUncheck(item) }, drag = drag)
                }
            }
        }
    }
}

/** 「買い物進捗」。のこりと完了（直近24時間）の件数と進捗バー */
@Composable
private fun ProgressCard(openCount: Int, completedCount: Int) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.large
    Column(
        modifier = Modifier
            .padding(horizontal = CartMateSpacing.large, vertical = CartMateSpacing.medium)
            .fillMaxWidth()
            .background(colors.surfaceContainerLow, shape)
            .border(1.dp, colors.outlineVariant, shape)
            .padding(CartMateSpacing.large),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.detail_progress_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Row(
                modifier = Modifier
                    .background(colors.primaryContainer, CircleShape)
                    .padding(horizontal = CartMateSpacing.medium, vertical = CartMateSpacing.xSmall),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CartMateSpacing.xSmall),
            ) {
                Icon(
                    painterResource(R.drawable.ic_check_circle),
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    stringResource(R.string.detail_progress_badge, openCount, completedCount),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.primary,
                )
            }
        }
        Spacer(Modifier.height(CartMateSpacing.medium))
        val total = openCount + completedCount
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else completedCount.toFloat() / total },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = colors.primary,
            trackColor = colors.surfaceContainer,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

@Composable
private fun ListMenu(onEdit: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.detail_menu))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.detail_edit_list)) },
                onClick = {
                    expanded = false
                    onEdit()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.detail_delete_list)) },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

/** リスト名と備考の変更 */
@Composable
private fun EditListDialog(
    initialName: String,
    initialMemo: String,
    onConfirm: (name: String, memo: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var memo by rememberSaveable { mutableStateOf(initialMemo) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.detail_edit_list)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(CartMateSpacing.medium)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(ShoppingList.MAX_NAME_LENGTH) },
                    label = { Text(stringResource(R.string.lists_name_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    value = memo,
                    onValueChange = { memo = it.take(ShoppingList.MAX_MEMO_LENGTH) },
                    label = { Text(stringResource(R.string.lists_memo_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, memo) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.detail_edit_list_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private fun previewItem(
    id: String,
    name: String,
    quantity: Double?,
    unit: String?,
    memo: String?,
    status: ItemStatus,
    visibility: Visibility = Visibility.SHARED,
) = Item(
    id = ItemId(id),
    listId = ListId("1"),
    inputName = name,
    sortKey = id.toDouble(),
    quantity = quantity,
    unit = unit,
    memo = memo,
    visibility = visibility,
    status = status,
    createdAt = Instant.EPOCH,
    createdBy = UserId("u"),
    completedAt = if (status == ItemStatus.COMPLETED) Instant.EPOCH else null,
    completedBy = if (status == ItemStatus.COMPLETED) UserId("u") else null,
)

/** プレビュー用のリスト詳細。シートのプレビューの背景にも使う */
@Composable
private fun ListDetailContentPreviewBackground(addItemText: String = "") {
    ListDetailContent(
        state = ListDetailUiState(
            listName = "スーパー",
            items = listOf(
                previewItem("1", "牛乳", 2.0, "本", "無調整・特売のもの", ItemStatus.OPEN),
                previewItem("2", "卵", 1.0, "パック", "10個入り・Lサイズ推奨", ItemStatus.OPEN, Visibility.PRIVATE),
                previewItem("3", "バナナ", 1.0, "房", null, ItemStatus.COMPLETED),
                previewItem("4", "食パン", 1.0, "斤", "6枚切り", ItemStatus.OPEN),
            ),
            addItem = AddItemState.Idle(addItemText),
            loading = false,
        ),
        snackbarHostState = remember { SnackbarHostState() },
        onBack = {},
        onAddItemEvent = {},
        onCheck = {},
        onUncheck = {},
        onEdit = {},
        onDelete = {},
        onMove = { _, _, _ -> },
        onUpdateList = { _, _ -> },
        onRequestDeleteList = { true },
        onDeleteList = {},
    )
}

@Preview
@Composable
private fun ListDetailContentPreview() {
    CartMateTheme { ListDetailContentPreviewBackground() }
}

@Preview
@Composable
private fun AddItemSheetEmptyPreview() {
    CartMateTheme {
        SheetPreviewFrame(background = { ListDetailContentPreviewBackground() }) {
            AddItemSheetContent(value = TextFieldValue(""), onValueChange = {}, onEvent = {})
        }
    }
}

@Preview
@Composable
private fun AddItemSheetTypingPreview() {
    CartMateTheme {
        SheetPreviewFrame(background = { ListDetailContentPreviewBackground("たまご") }) {
            AddItemSheetContent(value = TextFieldValue("たまご"), onValueChange = {}, onEvent = {})
        }
    }
}
