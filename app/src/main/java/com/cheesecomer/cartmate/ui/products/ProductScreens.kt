package com.cheesecomer.cartmate.ui.products

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import com.cheesecomer.cartmate.domain.model.Item
import com.cheesecomer.cartmate.domain.text.NameNormalizer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cheesecomer.cartmate.R
import com.cheesecomer.cartmate.domain.model.Product
import com.cheesecomer.cartmate.domain.model.ProductId
import com.cheesecomer.cartmate.domain.product.ProductMerge
import com.cheesecomer.cartmate.ui.theme.CartMateSpacing
import kotlinx.serialization.Serializable

/** 「商品名の管理」の一覧（docs/design/ui.md「商品名の管理」） */
@Serializable
data object ProductNamesRoute

/** 商品を開いた画面 */
@Serializable
data class ProductDetailRoute(val productId: String)

/** 「商品をまとめる」。[targetId] が残す商品（統合先） */
@Serializable
data class MergeProductsRoute(val targetId: String)

@Composable
fun ProductNamesScreen(onBack: () -> Unit, onOpen: (ProductId) -> Unit, viewModel: ProductNamesViewModel = hiltViewModel()) {
    val lists by viewModel.lists.collectAsStateWithLifecycle()
    ProductScaffold(title = stringResource(R.string.product_merge_title), onBack = onBack) {
        val loaded = lists ?: return@ProductScaffold
        var showHidden by rememberSaveable { mutableStateOf(false) }
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Hint(stringResource(R.string.product_names_hint))
            }
            if (loaded.entries.isEmpty()) {
                item { Empty(stringResource(R.string.product_names_empty)) }
            }
            items(loaded.entries, key = { it.product.id.value }) { entry ->
                EntryRow(entry, onOpen)
            }
            // 非表示の商品は一番下にまとめ、開いたときだけ並べる
            if (loaded.hidden.isNotEmpty()) {
                item(key = "hidden") {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.product_names_hidden, loaded.hidden.size)) },
                        trailingContent = {
                            Icon(
                                painterResource(if (showHidden) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            headlineColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        modifier = Modifier.clickable(role = Role.Button) { showHidden = !showHidden },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
                }
                if (showHidden) {
                    items(loaded.hidden, key = { "hidden-" + it.product.id.value }) { entry ->
                        EntryRow(entry, onOpen)
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryRow(entry: ProductMerge.Entry, onOpen: (ProductId) -> Unit) {
    ListItem(
        headlineContent = { Text(entry.product.displayName) },
        supportingContent = entryDescription(entry)?.let { { Text(it) } },
        trailingContent = { Chevron() },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.clickable(role = Role.Button) { onOpen(entry.product.id) },
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
}

/** 一覧の2行目。手動でまとめている件数と、辞書の名寄せを分けて出す */
@Composable
private fun entryDescription(entry: ProductMerge.Entry): String? = listOfNotNull(
    entry.members.size.takeIf { it > 0 }?.let { stringResource(R.string.product_names_members, it) },
    entry.dictionary?.takeIf { it.isMeaningful }?.let { stringResource(R.string.product_names_dictionary, it.names.joinToString("・")) },
    stringResource(R.string.product_names_opted_out).takeIf { entry.product.dictionaryOptOut },
).joinToString("\n").ifEmpty { null }

@Composable
fun ProductDetailScreen(
    onBack: () -> Unit,
    onMerge: (ProductId) -> Unit,
    onOpen: (ProductId) -> Unit,
    /** 名前を訂正した。今の画面を閉じて、訂正した名前の商品を開く */
    onRenamed: (ProductId) -> Unit,
    viewModel: ProductDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmHide by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ProductDetailViewModel.Event.Renamed -> onRenamed(event.target)
            }
        }
    }
    val title = when (val current = state) {
        is ProductDetailViewModel.State.Loaded -> when (val detail = current.detail) {
            is ProductMerge.Detail.Target -> detail.entry.product.displayName
            is ProductMerge.Detail.Merged -> detail.product.displayName
            is ProductMerge.Detail.Hidden -> detail.entry.product.displayName
        }
        else -> ""
    }
    ProductScaffold(title = title, onBack = onBack) {
        when (val current = state) {
            ProductDetailViewModel.State.Loading -> Unit
            ProductDetailViewModel.State.NotFound -> Empty(stringResource(R.string.product_detail_not_found))
            is ProductDetailViewModel.State.Loaded -> when (val detail = current.detail) {
                is ProductMerge.Detail.Target -> TargetDetail(
                    entry = detail.entry,
                    onUnmerge = viewModel::unmerge,
                    onOpen = onOpen,
                    onMerge = { onMerge(detail.entry.product.id) },
                    onDictionaryOptOut = viewModel::setDictionaryOptOut,
                    onRename = { renaming = true },
                    onHide = { confirmHide = true },
                )
                is ProductMerge.Detail.Merged -> MergedDetail(
                    target = detail.target,
                    onUnmerge = { viewModel.unmerge(detail.product.id) },
                    onOpenTarget = { onOpen(detail.target.id) },
                )
                is ProductMerge.Detail.Hidden -> HiddenDetail(entry = detail.entry, onShow = viewModel::show)
            }
        }
    }

    val loaded = (state as? ProductDetailViewModel.State.Loaded)?.detail as? ProductMerge.Detail.Target
    if (renaming && loaded != null) {
        RenameDialog(
            initial = loaded.entry.product.displayName,
            onConfirm = {
                renaming = false
                viewModel.rename(it)
            },
            onDismiss = { renaming = false },
        )
    }
    if (confirmHide && loaded != null) {
        AlertDialog(
            onDismissRequest = { confirmHide = false },
            title = { Text(stringResource(R.string.product_detail_hide_title, loaded.entry.product.displayName)) },
            text = { Text(stringResource(R.string.product_detail_hide_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmHide = false
                    viewModel.hide()
                }) { Text(stringResource(R.string.product_detail_hide_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmHide = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/** 名前の訂正。正しい名前を入力する。今の名前と同じ（正規化すると同じ）なら確定できない */
@Composable
private fun RenameDialog(initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    val changed = name.isNotBlank() && NameNormalizer.productId(name) != NameNormalizer.productId(initial)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.product_detail_rename)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(Item.MAX_NAME_LENGTH) },
                supportingText = { Text(stringResource(R.string.product_detail_rename_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = changed) { Text(stringResource(R.string.product_detail_rename_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun TargetDetail(
    entry: ProductMerge.Entry,
    onUnmerge: (ProductId) -> Unit,
    onOpen: (ProductId) -> Unit,
    onMerge: () -> Unit,
    onDictionaryOptOut: (Boolean) -> Unit,
    onRename: () -> Unit,
    onHide: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionLabel(stringResource(R.string.product_detail_members, entry.members.size))
        if (entry.members.isEmpty()) {
            Hint(stringResource(R.string.product_detail_no_members))
        }
        entry.members.forEach { member ->
            MemberRow(member, onUnmerge = { onUnmerge(member.id) }, onOpen = { onOpen(member.id) })
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
        }
        Button(
            onClick = onMerge,
            shape = CircleShape,
            modifier = Modifier
                .fillMaxWidth()
                .padding(CartMateSpacing.large)
                .heightIn(min = 52.dp),
        ) {
            Text(stringResource(R.string.product_detail_merge), style = MaterialTheme.typography.titleMedium)
        }

        // 辞書の名寄せは、手動でまとめた商品と分けて示す
        val dictionary = entry.dictionary?.takeIf { it.isMeaningful }
        if (dictionary != null || entry.product.dictionaryOptOut) {
            SectionLabel(stringResource(R.string.product_detail_dictionary_section))
            Column(Modifier.padding(horizontal = CartMateSpacing.large)) {
                if (dictionary != null) {
                    Text(
                        stringResource(R.string.product_detail_dictionary, dictionary.canonicalName, dictionary.names.joinToString("、")),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = { onDictionaryOptOut(true) }) { Text(stringResource(R.string.product_merge_opt_out)) }
                } else {
                    Text(stringResource(R.string.product_detail_opted_out), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { onDictionaryOptOut(false) }) { Text(stringResource(R.string.product_merge_opt_in)) }
                }
            }
        }

        SectionLabel(stringResource(R.string.product_detail_other_section))
        Column(Modifier.padding(horizontal = CartMateSpacing.large)) {
            TextButton(onClick = onRename) { Text(stringResource(R.string.product_detail_rename)) }
            Text(stringResource(R.string.product_detail_rename_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(CartMateSpacing.medium))
            TextButton(onClick = onHide) { Text(stringResource(R.string.product_detail_hide)) }
            Text(stringResource(R.string.product_detail_hide_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(CartMateSpacing.xLarge))
    }
}

@Composable
private fun HiddenDetail(entry: ProductMerge.Entry, onShow: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(CartMateSpacing.large), verticalArrangement = Arrangement.spacedBy(CartMateSpacing.medium)) {
        Text(stringResource(R.string.product_detail_hidden), style = MaterialTheme.typography.titleMedium)
        if (entry.members.isNotEmpty()) {
            Text(
                stringResource(R.string.product_detail_hidden_members, entry.members.joinToString("、") { it.displayName }),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(stringResource(R.string.product_detail_hidden_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = onShow, shape = CircleShape) { Text(stringResource(R.string.product_detail_show)) }
    }
}

@Composable
private fun MemberRow(member: Product, onUnmerge: () -> Unit, onOpen: () -> Unit) {
    val description = stringResource(R.string.product_detail_unmerge_description, member.displayName)
    ListItem(
        headlineContent = { Text(member.displayName) },
        trailingContent = {
            TextButton(onClick = onUnmerge, modifier = Modifier.semantics { contentDescription = description }) {
                Text(stringResource(R.string.product_detail_unmerge))
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.clickable(role = Role.Button, onClick = onOpen),
    )
}

@Composable
private fun MergedDetail(target: Product, onUnmerge: () -> Unit, onOpenTarget: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(CartMateSpacing.large), verticalArrangement = Arrangement.spacedBy(CartMateSpacing.medium)) {
        Text(
            stringResource(R.string.product_detail_merged_into, target.displayName),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.clickable(role = Role.Button, onClick = onOpenTarget),
        )
        Text(stringResource(R.string.product_detail_merged_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = onUnmerge, shape = CircleShape) { Text(stringResource(R.string.product_detail_unmerge)) }
    }
}

@Composable
fun MergeProductsScreen(onBack: () -> Unit, onDone: () -> Unit, viewModel: MergeProductsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    var confirming by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                MergeProductsViewModel.Event.TooMany ->
                    snackbar.showSnackbar(resources.getString(R.string.merge_products_too_many, ProductMerge.MAX_SELECTION))
                MergeProductsViewModel.Event.Done -> onDone()
            }
        }
    }
    ProductScaffold(
        title = stringResource(R.string.merge_products_title),
        onBack = onBack,
        snackbar = snackbar,
        bottomBar = {
            // 背景はナビゲーションバーの下まで伸ばし、件数とボタンはナビゲーションバーの上に置く（画面の端まで描いているため）
            Surface(tonalElevation = 3.dp) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(CartMateSpacing.large),
                ) {
                    Text(
                        stringResource(R.string.merge_products_selected, state.selected.size),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = { confirming = true }, enabled = state.canMerge, shape = CircleShape) {
                        Text(stringResource(R.string.merge_products_confirm_action))
                    }
                }
            }
        },
    ) {
        val target = state.target
        if (state.loading) return@ProductScaffold
        if (target == null) {
            Empty(stringResource(R.string.product_detail_not_found))
            return@ProductScaffold
        }
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                // 統合先は固定して上に出す
                SectionLabel(stringResource(R.string.merge_products_target))
                Text(
                    target.product.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = CartMateSpacing.large),
                )
                Hint(stringResource(R.string.merge_products_hint, target.product.displayName, ProductMerge.MAX_SELECTION))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            if (state.candidates.isEmpty()) {
                item { Empty(stringResource(R.string.merge_products_empty)) }
            }
            items(state.candidates, key = { it.product.id.value }) { entry ->
                val checked = entry.product.id in state.selected
                ListItem(
                    headlineContent = { Text(entry.product.displayName) },
                    supportingContent = entryDescription(entry)?.let { { Text(it) } },
                    leadingContent = { Checkbox(checked = checked, onCheckedChange = null) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.toggleable(
                        value = checked,
                        enabled = !state.submitted,
                        role = Role.Checkbox,
                        onValueChange = { viewModel.toggle(entry.product.id) },
                    ),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
            }
        }
    }

    if (confirming) {
        val target = state.target
        val selected = state.selectedEntries
        if (target == null || selected.isEmpty()) {
            confirming = false
        } else {
            AlertDialog(
                onDismissRequest = { confirming = false },
                title = { Text(stringResource(R.string.merge_products_confirm_title, target.product.displayName, selected.size)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(CartMateSpacing.small)) {
                        Text(selected.joinToString("、") { it.product.displayName })
                        // 選んだ商品にまとまっている商品も、一緒に移ることを伝える
                        selected.filter { it.members.isNotEmpty() }.forEach {
                            Text(stringResource(R.string.merge_products_confirm_members, it.product.displayName, it.members.size))
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        confirming = false
                        viewModel.merge()
                    }) { Text(stringResource(R.string.merge_products_confirm_action)) }
                },
                dismissButton = {
                    TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.action_cancel)) }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductScaffold(
    title: String,
    onBack: () -> Unit,
    snackbar: SnackbarHostState? = null,
    bottomBar: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = bottomBar,
        snackbarHost = { snackbar?.let { SnackbarHost(it) } },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) { content() }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = CartMateSpacing.large, end = CartMateSpacing.large, top = CartMateSpacing.xLarge, bottom = CartMateSpacing.small),
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(CartMateSpacing.large),
    )
}

@Composable
private fun Empty(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(CartMateSpacing.xLarge),
    )
}

@Composable
private fun Chevron() {
    Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}
