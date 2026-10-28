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
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.Switch
import java.time.format.DateTimeFormatter
import com.cheesecomer.cartmate.domain.input.formatQuantity
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.RadioButton
import com.cheesecomer.cartmate.ui.listdetail.PurchaseDeleteDialog
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

/** 商品ごとの購入履歴 */
@Serializable
data class PurchaseHistoryRoute(val productId: String)

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
            // 非表示の商品は、一覧と分けた別枠に控えめに出す。見出しのタップで開閉する
            if (loaded.hidden.isNotEmpty()) {
                item(key = "hidden") {
                    Spacer(Modifier.height(CartMateSpacing.xLarge))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) { showHidden = !showHidden }
                            .heightIn(min = 48.dp)
                            .padding(horizontal = CartMateSpacing.large),
                    ) {
                        Text(
                            stringResource(R.string.product_names_hidden, loaded.hidden.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            painterResource(if (showHidden) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                if (showHidden) {
                    items(loaded.hidden, key = { "hidden-" + it.entry.product.id.value }) { hidden ->
                        HiddenEntryRow(hidden, onOpen)
                    }
                }
                item(key = "hidden-bottom") { Spacer(Modifier.height(CartMateSpacing.xLarge)) }
            }
        }
    }
}

/** 非表示の商品の行。一覧の行より小さく、薄い色で出す。どちらの非表示かと、非表示にした人を添える */
@Composable
private fun HiddenEntryRow(hidden: ProductNamesViewModel.HiddenEntry, onOpen: (ProductId) -> Unit) {
    val entry = hidden.entry
    val mode = stringResource(
        if (entry.mode == ProductMerge.SuggestionMode.ALWAYS_HIDDEN) R.string.suggestion_mode_always else R.string.suggestion_mode_temporary,
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { onOpen(entry.product.id) }
            .heightIn(min = 40.dp)
            .padding(horizontal = CartMateSpacing.xLarge, vertical = CartMateSpacing.small),
    ) {
        Text(entry.product.displayName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            listOf(mode, hidden.byName ?: stringResource(R.string.former_member)).joinToString("・"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
    onOpenHistory: (ProductId) -> Unit,
    viewModel: ProductDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var renaming by rememberSaveable { mutableStateOf(false) }
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
                    onOpenHistory = { onOpenHistory(detail.entry.product.id) },
                    onMode = viewModel::setMode,
                )
                is ProductMerge.Detail.Merged -> MergedDetail(
                    target = detail.target,
                    onUnmerge = { viewModel.unmerge(detail.product.id) },
                    onOpenTarget = { onOpen(detail.target.id) },
                )
            }
        }
    }

    val loaded = (state as? ProductDetailViewModel.State.Loaded)?.detail as? ProductMerge.Detail.Target
    if (renaming && loaded != null) {
        RenameDialog(
            initial = loaded.entry.product.displayName,
            alwaysHidden = loaded.entry.product.suggestionExcluded,
            findTarget = viewModel::renameTarget,
            onConfirm = {
                renaming = false
                viewModel.rename(it)
            },
            onDismiss = { renaming = false },
        )
    }
}

/**
 * 名前の訂正。正しい名前を入力する。今の名前と同じ（正規化すると同じ）なら確定できない。
 * 既にある商品にまとめることになり、常に非表示の設定が変わる場合は、そのことを伝える
 */
@Composable
private fun RenameDialog(
    initial: String,
    /** 今の商品が常に非表示か */
    alwaysHidden: Boolean,
    findTarget: suspend (String) -> Product?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial) }
    val changed = name.isNotBlank() && NameNormalizer.productId(name) != NameNormalizer.productId(initial)
    var target by remember { mutableStateOf<Product?>(null) }
    LaunchedEffect(name) { target = if (changed) findTarget(name) else null }
    val notice = target?.takeIf { it.suggestionExcluded != alwaysHidden }?.let {
        stringResource(if (it.suggestionExcluded) R.string.rename_notice_always else R.string.rename_notice_normal, it.displayName)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.product_detail_rename)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(CartMateSpacing.small)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(Item.MAX_NAME_LENGTH) },
                    supportingText = { Text(stringResource(R.string.product_detail_rename_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
                notice?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary) }
            }
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
    onOpenHistory: () -> Unit,
    onMode: (ProductMerge.SuggestionMode) -> Unit,
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

        // 予測。購入履歴（1件ずつ予測に使わないにできる）と、サジェストへの出し方
        SectionLabel(stringResource(R.string.product_detail_prediction_section))
        ListItem(
            headlineContent = { Text(stringResource(R.string.product_detail_history)) },
            trailingContent = { Chevron() },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.clickable(role = Role.Button, onClick = onOpenHistory),
        )
        ModeSelector(entry.mode, onMode)

        SectionLabel(stringResource(R.string.product_detail_other_section))
        Column(Modifier.padding(horizontal = CartMateSpacing.large)) {
            TextButton(onClick = onRename) { Text(stringResource(R.string.product_detail_rename)) }
            Text(stringResource(R.string.product_detail_rename_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(CartMateSpacing.xLarge))
    }
}

/** サジェストへの出し方（docs/requirements.md §4.3）。通常どおり表示・一時的に非表示・常に非表示から選ぶ */
@Composable
private fun ModeSelector(mode: ProductMerge.SuggestionMode, onSelect: (ProductMerge.SuggestionMode) -> Unit) {
    Column(Modifier.selectableGroup()) {
        listOf(
            Triple(ProductMerge.SuggestionMode.NORMAL, R.string.suggestion_mode_normal, R.string.suggestion_mode_normal_description),
            Triple(ProductMerge.SuggestionMode.TEMPORARILY_HIDDEN, R.string.suggestion_mode_temporary, R.string.suggestion_mode_temporary_description),
            Triple(ProductMerge.SuggestionMode.ALWAYS_HIDDEN, R.string.suggestion_mode_always, R.string.suggestion_mode_always_description),
        ).forEach { (value, title, description) ->
            ListItem(
                headlineContent = { Text(stringResource(title)) },
                supportingContent = { Text(stringResource(description)) },
                leadingContent = { RadioButton(selected = mode == value, onClick = null) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.selectable(selected = mode == value, role = Role.RadioButton, onClick = { onSelect(value) }),
            )
        }
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
                        // まとめた商品は統合先の設定に従う。統合先が常に非表示なら、そのことを伝える
                        if (target.product.suggestionExcluded) {
                            Text(stringResource(R.string.merge_products_confirm_always_hidden, target.product.displayName))
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

/** 「10/18」 */
private val HISTORY_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d")

@Composable
fun PurchaseHistoryScreen(onBack: () -> Unit, viewModel: PurchaseHistoryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<PurchaseHistoryViewModel.Row?>(null) }
    deleting?.let { row ->
        PurchaseDeleteDialog(
            itemName = row.purchase.inputName,
            onConfirm = {
                deleting = null
                viewModel.delete(row.purchase.itemId)
            },
            onDismiss = { deleting = null },
        )
    }
    ProductScaffold(title = stringResource(R.string.purchase_history_title, state.productName), onBack = onBack) {
        if (state.loading) return@ProductScaffold
        LazyColumn(Modifier.fillMaxSize()) {
            item { Hint(stringResource(R.string.purchase_history_hint)) }
            if (state.rows.isEmpty()) {
                item { Empty(stringResource(R.string.purchase_history_empty)) }
            }
            items(state.rows, key = { it.purchase.itemId.value }) { row ->
                PurchaseRow(row, viewModel::setExcluded, onDelete = { deleting = row }.takeIf { row.deletable })
            }
        }
    }
}

@Composable
private fun PurchaseRow(
    row: PurchaseHistoryViewModel.Row,
    onExcluded: (com.cheesecomer.cartmate.domain.model.ItemId, Boolean) -> Unit,
    /** 自分がチェックした購入だけ渡す */
    onDelete: (() -> Unit)?,
) {
    val purchase = row.purchase
    val amount = listOfNotNull(purchase.quantity?.let(::formatQuantity), purchase.unit).joinToString("")
    val excluded = purchase.excludedFromPrediction
    val dimmed = if (excluded) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    ListItem(
        headlineContent = {
            Text(
                listOf(HISTORY_DATE.format(row.date), purchase.inputName, amount).filter { it.isNotEmpty() }.joinToString("  "),
                color = dimmed,
            )
        },
        supportingContent = {
            Column {
                Text(
                    listOf(purchase.listNameSnapshot, row.checkerName ?: stringResource(R.string.former_member)).joinToString("・"),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (excluded) {
                    Text(
                        stringResource(
                            R.string.purchase_history_excluded,
                            row.excludedByName ?: stringResource(R.string.former_member),
                            row.excludedDate?.let(HISTORY_DATE::format).orEmpty(),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                onDelete?.let {
                    val description = stringResource(R.string.purchase_history_delete, purchase.inputName)
                    IconButton(onClick = it, modifier = Modifier.semantics { contentDescription = description }) {
                        Icon(painterResource(R.drawable.ic_delete), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Switch(checked = !excluded, onCheckedChange = null)
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.toggleable(
            value = !excluded,
            role = Role.Switch,
            onValueChange = { use -> onExcluded(purchase.itemId, !use) },
        ),
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
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
