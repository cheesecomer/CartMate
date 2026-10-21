package com.cheesecomer.cartmate.ui.lists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cheesecomer.cartmate.R
import com.cheesecomer.cartmate.domain.model.ListId
import com.cheesecomer.cartmate.domain.model.ShoppingList
import com.cheesecomer.cartmate.ui.theme.CartMateTheme

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
        onDeleteList = viewModel::deleteList,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListsContent(
    state: ListsUiState,
    onOpenList: (ListId) -> Unit,
    onCreateList: (String) -> Unit,
    onDeleteList: (ListId) -> Unit,
) {
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ListSummary?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.lists_title)) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                text = { Text(stringResource(R.string.lists_create)) },
            )
        },
    ) { innerPadding ->
        if (!state.loading && state.lists.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(innerPadding).padding(24.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.lists_empty))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    // FAB に最後の行が隠れないようにする
                    bottom = innerPadding.calculateBottomPadding() + 88.dp,
                ),
            ) {
                items(state.lists, key = { it.id.value }) { list ->
                    ListRow(list, onClick = { onOpenList(list.id) }, onDelete = { deleteTarget = list })
                    HorizontalDivider()
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateListDialog(
            onConfirm = { name ->
                onCreateList(name)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false },
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
private fun ListRow(list: ListSummary, onClick: () -> Unit, onDelete: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(list.name) },
        supportingContent = { Text(stringResource(R.string.lists_open_count, list.openCount)) },
        trailingContent = if (list.canDelete) {
            {
                IconButton(onClick = onDelete) {
                    Icon(
                        painterResource(R.drawable.ic_delete),
                        contentDescription = stringResource(R.string.lists_delete, list.name),
                    )
                }
            }
        } else {
            null
        },
    )
}

@Composable
private fun CreateListDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val canCreate = name.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lists_create_dialog_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(ShoppingList.MAX_NAME_LENGTH) },
                label = { Text(stringResource(R.string.lists_create_dialog_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (canCreate) onConfirm(name) }),
                modifier = Modifier.focusRequester(focusRequester),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = canCreate) {
                Text(stringResource(R.string.lists_create_dialog_confirm))
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
                    ListSummary(ListId("1"), "スーパー", 3),
                    ListSummary(ListId("2"), "薬局", 0),
                ),
                loading = false,
            ),
            onOpenList = {},
            onCreateList = {},
            onDeleteList = {},
        )
    }
}
