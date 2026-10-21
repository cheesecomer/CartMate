package com.cheesecomer.cartmate.ui.listdetail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import com.cheesecomer.cartmate.domain.model.UserId
import com.cheesecomer.cartmate.ui.theme.CartMateTheme
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class ListDetailRoute(val listId: String)

@Composable
fun ListDetailScreen(
    onBack: () -> Unit,
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
                    is DetailMessage.CannotEditCompleted ->
                        resources.getString(R.string.detail_cannot_edit_completed, message.name) to null
                    DetailMessage.EditedItemNotFound ->
                        resources.getString(R.string.detail_edited_item_not_found) to null
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

    ListDetailContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onAddItemEvent = viewModel::onAddItemEvent,
        onCheck = viewModel::check,
        onUncheck = viewModel::uncheck,
        onDelete = viewModel::delete,
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
    onDelete: (Item) -> Unit,
) {
    val focusManager = LocalFocusManager.current

    // 戻るキー: 数量入力中は商品名の入力へ、編集中は編集をやめる、商品名の入力中はフォーカスを外す（サジェストを閉じる）
    BackHandler(enabled = state.addItem is AddItemState.Detail || state.addItem is AddItemState.Editing) {
        onAddItemEvent(AddItemEvent.Cancelled)
    }
    BackHandler(enabled = state.addItem is AddItemState.FocusedEmpty || state.addItem is AddItemState.Typing) {
        focusManager.clearFocus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.listName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            AddItemBar(
                state = state.addItem,
                onEvent = onAddItemEvent,
                // キーボードが出ているときはその直上、出ていないときはナビゲーションバーの上に置く
                modifier = Modifier.windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
            )
        },
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = innerPadding) {
            if (!state.loading && state.items.isEmpty()) {
                item(key = "empty") {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.detail_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            // チェック済みの項目も元の位置に残し、打ち消し線で示す
            items(state.items, key = { it.id.value }) { item ->
                when (item.status) {
                    ItemStatus.OPEN -> OpenItemRow(
                        item = item,
                        editing = (state.addItem as? AddItemState.Editing)?.itemId == item.id,
                        onEdit = { onAddItemEvent(AddItemEvent.EditRequested(item)) },
                        onCancelEdit = { onAddItemEvent(AddItemEvent.Cancelled) },
                        onCheck = { onCheck(item) },
                        onDelete = { onDelete(item) },
                        modifier = Modifier.animateItem(),
                    )
                    ItemStatus.COMPLETED -> CompletedItemRow(
                        item = item,
                        onUncheck = { onUncheck(item) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun ListDetailContentPreview() {
    fun item(id: String, name: String, quantity: Double?, unit: String?, status: ItemStatus) = Item(
        id = ItemId(id),
        listId = ListId("1"),
        inputName = name,
        quantity = quantity,
        unit = unit,
        status = status,
        createdAt = Instant.EPOCH,
        createdBy = UserId("u"),
        completedAt = if (status == ItemStatus.COMPLETED) Instant.EPOCH else null,
        completedBy = if (status == ItemStatus.COMPLETED) UserId("u") else null,
    )
    CartMateTheme {
        ListDetailContent(
            state = ListDetailUiState(
                listName = "スーパー",
                items = listOf(
                    item("1", "牛乳", 2.0, "本", ItemStatus.OPEN),
                    item("2", "食パン", 1.0, null, ItemStatus.COMPLETED),
                    item("3", "卵", 1.0, "パック", ItemStatus.OPEN),
                    item("4", "バナナ", null, null, ItemStatus.OPEN),
                ),
                loading = false,
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
            onAddItemEvent = {},
            onCheck = {},
            onUncheck = {},
            onDelete = {},
        )
    }
}
