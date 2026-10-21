package com.cheesecomer.cartmate.ui.lists

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cheesecomer.cartmate.R
import com.cheesecomer.cartmate.domain.model.ListId
import com.cheesecomer.cartmate.domain.model.ShoppingList
import com.cheesecomer.cartmate.ui.components.AccentCard
import com.cheesecomer.cartmate.ui.components.CardContent
import com.cheesecomer.cartmate.ui.components.DraggingElevation
import com.cheesecomer.cartmate.ui.components.rememberDraggableOrder
import com.cheesecomer.cartmate.ui.components.cardSlot
import com.cheesecomer.cartmate.ui.theme.CartMateTheme
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun ListsScreen(
    onOpenList: (ListId) -> Unit,
    viewModel: ListsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ListsContent(
        state = state,
        onOpenList = onOpenList,
        onCreateList = viewModel::createList,
        onRenameList = viewModel::renameList,
        onMoveList = viewModel::moveList,
        onDeleteList = viewModel::deleteList,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListsContent(
    state: ListsUiState,
    onOpenList: (ListId) -> Unit,
    onCreateList: (String) -> Unit,
    onRenameList: (ListId, String) -> Unit,
    onMoveList: (ListId, previous: ListId?, next: ListId?) -> Unit,
    onDeleteList: (ListId) -> Unit,
) {
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<ListSummary?>(null) }
    var deleteTarget by remember { mutableStateOf<ListSummary?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.lists_title)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }, shape = CircleShape) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.lists_create))
            }
        },
    ) { innerPadding ->
        if (!state.loading && state.lists.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(innerPadding).padding(24.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.lists_empty))
            }
        } else {
            val lazyListState = rememberLazyListState()
            val order = rememberDraggableOrder(state.lists) { it.id.value }
            val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
                order.move(from.key, to.key)
            }
            val moveUpLabel = stringResource(R.string.action_move_up)
            val moveDownLabel = stringResource(R.string.action_move_down)
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    // FAB に最後の行が隠れないようにする
                    bottom = innerPadding.calculateBottomPadding() + 88.dp,
                ),
            ) {
                items(order.items, key = { it.id.value }) { list ->
                    ReorderableItem(reorderState, key = list.id.value) { dragging ->
                        ListRow(
                            list,
                            onClick = { onOpenList(list.id) },
                            onRename = { renameTarget = list },
                            onDelete = { deleteTarget = list },
                            dragging = dragging,
                            modifier = Modifier.longPressDraggableHandle(
                                onDragStarted = { order.startDrag() },
                                onDragStopped = {
                                    order.finishDrag(list.id.value)
                                        ?.let { onMoveList(list.id, it.previous?.id, it.next?.id) }
                                },
                            ),
                            actions = listOfNotNull(
                                order.moveUpTarget(list.id.value)?.let { target ->
                                    CustomAccessibilityAction(moveUpLabel) {
                                        onMoveList(list.id, target.previous?.id, target.next?.id)
                                        true
                                    }
                                },
                                order.moveDownTarget(list.id.value)?.let { target ->
                                    CustomAccessibilityAction(moveDownLabel) {
                                        onMoveList(list.id, target.previous?.id, target.next?.id)
                                        true
                                    }
                                },
                            ),
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        ListNameDialog(
            title = stringResource(R.string.lists_create_dialog_title),
            confirmLabel = stringResource(R.string.lists_create_dialog_confirm),
            initialName = "",
            onConfirm = { name ->
                onCreateList(name)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false },
        )
    }

    renameTarget?.let { target ->
        ListNameDialog(
            title = stringResource(R.string.lists_rename_dialog_title),
            confirmLabel = stringResource(R.string.lists_rename_dialog_confirm),
            initialName = target.name,
            onConfirm = { name ->
                onRenameList(target.id, name)
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.lists_delete_dialog_title)) },
            text = { Text(stringResource(R.string.lists_delete_dialog_message, target.name)) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteList(target.id)
                    deleteTarget = null
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun ListRow(
    list: ListSummary,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    dragging: Boolean,
    actions: List<CustomAccessibilityAction>,
    // ロングタップでドラッグを始める部分
    modifier: Modifier = Modifier,
) {
    // 帯の色は、これから買うものがあるリスト（未購入あり・新しいリスト）はブランドカラー、買い終わったリストはグレー
    val active = list.openCount > 0 || list.isNew
    AccentCard(
        accent = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        onClickLabel = stringResource(R.string.lists_open),
        onClick = onClick,
        modifier = Modifier.cardSlot(elevation = if (dragging) DraggingElevation else 0.dp).then(modifier),
        customActions = actions,
    ) {
        CardContent(
            headline = { Text(list.name) },
            supporting = {
                Text(
                    if (list.isNew) {
                        stringResource(R.string.lists_new)
                    } else {
                        stringResource(R.string.lists_open_count, list.openCount)
                    },
                )
            },
            trailing = {
                Row {
                    ListIconButton(R.drawable.ic_edit, stringResource(R.string.lists_rename, list.name), onRename)
                    // 未購入が残っているリストは削除できない
                    if (list.canDelete) {
                        ListIconButton(R.drawable.ic_delete, stringResource(R.string.lists_delete, list.name), onDelete)
                    }
                }
            },
        )
    }
}

@Composable
private fun ListIconButton(icon: Int, contentDescription: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            painterResource(icon),
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** リストの作成と名前の変更で共通のダイアログ */
@Composable
private fun ListNameDialog(
    title: String,
    confirmLabel: String,
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // 名前の変更では、今の名前を全選択しておき、そのまま入力すれば置き換わるようにする
    var name by remember {
        mutableStateOf(TextFieldValue(initialName, TextRange(0, initialName.length)))
    }
    val focusRequester = remember { FocusRequester() }
    val canConfirm = name.text.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.copy(text = it.text.take(ShoppingList.MAX_NAME_LENGTH)) },
                label = { Text(stringResource(R.string.lists_create_dialog_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (canConfirm) onConfirm(name.text) }),
                modifier = Modifier.focusRequester(focusRequester),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.text) }, enabled = canConfirm) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Preview
@Composable
private fun ListsContentPreview() {
    CartMateTheme {
        ListsContent(
            state = ListsUiState(
                lists = listOf(
                    ListSummary(ListId("1"), "スーパー", 3, hasPurchases = true),
                    ListSummary(ListId("2"), "薬局", 0, hasPurchases = true),
                    ListSummary(ListId("3"), "ホームセンター", 0, hasPurchases = false),
                ),
                loading = false,
            ),
            onOpenList = {},
            onCreateList = {},
            onRenameList = { _, _ -> },
            onMoveList = { _, _, _ -> },
            onDeleteList = {},
        )
    }
}
