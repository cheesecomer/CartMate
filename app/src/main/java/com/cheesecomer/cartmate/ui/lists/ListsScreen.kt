package com.cheesecomer.cartmate.ui.lists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cheesecomer.cartmate.R
import com.cheesecomer.cartmate.domain.list.ListProgress
import com.cheesecomer.cartmate.domain.model.ListId
import com.cheesecomer.cartmate.domain.model.Visibility
import com.cheesecomer.cartmate.ui.components.OfflineBanner
import com.cheesecomer.cartmate.ui.components.rememberDraggableOrder
import com.cheesecomer.cartmate.ui.theme.CartMateSpacing
import com.cheesecomer.cartmate.ui.theme.CartMateTheme
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import androidx.compose.material3.IconButton
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.cheesecomer.cartmate.domain.model.Account
import com.cheesecomer.cartmate.domain.model.UserId
import com.cheesecomer.cartmate.ui.components.Avatar
import com.cheesecomer.cartmate.ui.components.AvatarSmall

@Composable
fun ListsScreen(
    onOpenList: (ListId) -> Unit,
    onOpenFamily: () -> Unit = {},
    viewModel: ListsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ListsContent(
        state = state,
        onOpenList = onOpenList,
        onOpenFamily = onOpenFamily,
        onCreateList = viewModel::createList,
        onMoveList = viewModel::moveList,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListsContent(
    state: ListsUiState,
    onOpenList: (ListId) -> Unit,
    onOpenFamily: () -> Unit,
    onCreateList: (name: String, memo: String, Visibility) -> Unit,
    onMoveList: (ListId, previous: ListId?, next: ListId?) -> Unit,
) {
    var showCreateSheet by rememberSaveable { mutableStateOf(false) }
    val familyLabel = stringResource(R.string.family_open)
    val empty = !state.loading && state.lists.isEmpty()

    // リスト作成のシートは画面の上に重ねて表示する
    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium) },
                    actions = {
                        // 自分のアカウントのアバター。家族画面への入口（docs/requirements.md §3.1）
                        state.account?.let { account ->
                            IconButton(onClick = onOpenFamily) {
                                Avatar(
                                    name = account.displayName,
                                    photoUrl = account.photoUrl,
                                    size = AvatarSmall,
                                    modifier = Modifier.semantics { contentDescription = familyLabel },
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            },
            floatingActionButton = {
                // 空の状態では画面中央のボタンから作成する
                if (!empty) {
                    ExtendedFloatingActionButton(
                        onClick = { showCreateSheet = true },
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                        text = { Text(stringResource(R.string.lists_create), style = MaterialTheme.typography.titleMedium) },
                    )
                }
            },
        ) { innerPadding ->
            Column(Modifier.padding(top = innerPadding.calculateTopPadding())) {
                if (state.offline) OfflineBanner()
                val contentPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding())
                if (empty) {
                    EmptyLists(onCreate = { showCreateSheet = true }, modifier = Modifier.padding(contentPadding))
                } else {
                    ListGrid(
                        lists = state.lists,
                        onOpenList = onOpenList,
                        onMoveList = onMoveList,
                        contentPadding = contentPadding,
                    )
                }
            }
        }

        if (showCreateSheet) {
            CreateListSheet(
                onCreate = { name, memo, visibility ->
                    onCreateList(name, memo, visibility)
                    showCreateSheet = false
                },
                onDismiss = { showCreateSheet = false },
            )
        }
    }
}

@Composable
private fun ListGrid(
    lists: List<ListSummary>,
    onOpenList: (ListId) -> Unit,
    onMoveList: (ListId, previous: ListId?, next: ListId?) -> Unit,
    contentPadding: PaddingValues,
) {
    val gridState = rememberLazyGridState()
    val order = rememberDraggableOrder(lists) { it.id.value }
    val reorderState = rememberReorderableLazyGridState(gridState) { from, to -> order.move(from.key, to.key) }
    val movePreviousLabel = stringResource(R.string.action_move_previous)
    val moveNextLabel = stringResource(R.string.action_move_next)

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = CartMateSpacing.large,
            end = CartMateSpacing.large,
            top = contentPadding.calculateTopPadding() + CartMateSpacing.small,
            // FAB に最後のカードが隠れないようにする
            bottom = contentPadding.calculateBottomPadding() + 88.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(CartMateSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(CartMateSpacing.medium),
    ) {
        item(key = "reorder_hint", span = { GridItemSpan(maxLineSpan) }) {
            Text(
                stringResource(R.string.lists_reorder_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = CartMateSpacing.xSmall),
            )
        }
        items(order.items, key = { it.id.value }) { list ->
            ReorderableItem(reorderState, key = list.id.value) { dragging ->
                ListCard(
                    list = list,
                    onClick = { onOpenList(list.id) },
                    dragging = dragging,
                    modifier = Modifier.longPressDraggableHandle(
                        onDragStarted = { order.startDrag() },
                        onDragStopped = {
                            order.finishDrag(list.id.value)?.let { onMoveList(list.id, it.previous?.id, it.next?.id) }
                        },
                    ),
                    actions = listOfNotNull(
                        order.moveUpTarget(list.id.value)?.let { target ->
                            CustomAccessibilityAction(movePreviousLabel) {
                                onMoveList(list.id, target.previous?.id, target.next?.id)
                                true
                            }
                        },
                        order.moveDownTarget(list.id.value)?.let { target ->
                            CustomAccessibilityAction(moveNextLabel) {
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

@Composable
private fun EmptyLists(onCreate: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(CartMateSpacing.xLarge),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.lists_empty_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(CartMateSpacing.small))
        Text(
            stringResource(R.string.lists_empty_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(CartMateSpacing.xLarge))
        Button(onClick = onCreate, shape = CircleShape, modifier = Modifier.height(48.dp)) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(CartMateSpacing.small))
            Text(stringResource(R.string.lists_empty_create), style = MaterialTheme.typography.titleMedium)
        }
    }
}

private val PreviewLists = listOf(
    ListSummary(ListId("1"), "スーパー", "週末の買い出し", Visibility.SHARED, ListProgress(5, 4, true)),
    ListSummary(ListId("2"), "薬局", "日用品・常備薬", Visibility.PRIVATE, ListProgress(2, 5, true)),
    ListSummary(ListId("3"), "ホームセンター", "園芸・DIY資材", Visibility.SHARED, ListProgress(0, 0, false)),
    ListSummary(ListId("4"), "イオン", "火曜市まとめ買い", Visibility.SHARED, ListProgress(0, 3, true)),
    ListSummary(ListId("5"), "100円ショップ", "収納小物・消耗品", Visibility.PRIVATE, ListProgress(1, 4, true)),
)

/** プレビュー用の一覧。シートのプレビューの背景にも使う */
@Composable
internal fun ListsContentPreviewBackground(offline: Boolean = false) {
    ListsContent(
        state = ListsUiState(
            lists = PreviewLists,
            loading = false,
            offline = offline,
            account = Account(UserId("taro"), "山田 太郎", "taro.yamada@gmail.com", null),
        ),
        onOpenList = {},
        onOpenFamily = {},
        onCreateList = { _, _, _ -> },
        onMoveList = { _, _, _ -> },
    )
}

@Preview
@Composable
private fun ListsContentPreview() {
    CartMateTheme { ListsContentPreviewBackground() }
}

@Preview
@Composable
private fun OfflineListsPreview() {
    CartMateTheme { ListsContentPreviewBackground(offline = true) }
}

@Preview
@Composable
private fun EmptyListsPreview() {
    CartMateTheme {
        ListsContent(
            state = ListsUiState(loading = false),
            onOpenList = {},
            onOpenFamily = {},
            onCreateList = { _, _, _ -> },
            onMoveList = { _, _, _ -> },
        )
    }
}
