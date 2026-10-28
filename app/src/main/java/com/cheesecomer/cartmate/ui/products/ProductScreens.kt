package com.cheesecomer.cartmate.ui.products

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cheesecomer.cartmate.R
import com.cheesecomer.cartmate.domain.model.Item
import com.cheesecomer.cartmate.domain.model.Product
import com.cheesecomer.cartmate.domain.model.ProductId
import com.cheesecomer.cartmate.domain.product.ProductMerge
import com.cheesecomer.cartmate.domain.suggest.PurchaseHistory
import com.cheesecomer.cartmate.domain.text.NameNormalizer
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

/** 商品名の管理（docs/design/screens/item_list）。検索欄、商品の一覧のカード、非表示の商品のカード */
@Composable
fun ProductNamesScreen(onBack: () -> Unit, onOpen: (ProductId) -> Unit, viewModel: ProductNamesViewModel = hiltViewModel()) {
    val lists by viewModel.lists.collectAsStateWithLifecycle()
    ProductScaffold(title = stringResource(R.string.product_merge_title), onBack = onBack) {
        val loaded = lists ?: return@ProductScaffold
        var showHidden by rememberSaveable { mutableStateOf(false) }
        LazyColumn(Modifier.fillMaxSize()) {
            item(key = "search") {
                SearchField(loaded.query, viewModel::search, stringResource(R.string.product_names_search_hint))
                SectionLabel(stringResource(R.string.product_names_count, loaded.entries.size), Modifier.padding(bottom = CartMateSpacing.xSmall))
            }
            if (loaded.entries.isEmpty()) {
                item(key = "empty") {
                    Empty(
                        if (loaded.query.isBlank()) stringResource(R.string.product_names_empty)
                        else stringResource(R.string.product_names_no_match, loaded.query.trim()),
                    )
                }
            }
            itemsIndexed(loaded.entries, key = { _, entry -> entry.product.id.value }) { index, entry ->
                EntryRow(entry, onOpen, first = index == 0, last = index == loaded.entries.lastIndex)
            }
            // 非表示の商品は、一覧と分けたカードに出す。見出しのタップで開閉する（初期は閉じる）
            if (loaded.hidden.isNotEmpty()) {
                item(key = "hidden") {
                    Spacer(Modifier.height(CartMateSpacing.xLarge))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(CartMateSpacing.medium),
                        modifier = Modifier
                            .listCardSegment(first = true, last = !showHidden)
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .clickable(role = Role.Button) { showHidden = !showHidden }
                            .heightIn(min = 56.dp)
                            .padding(horizontal = CartMateSpacing.xLarge),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_visibility_off),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            stringResource(R.string.product_names_hidden, loaded.hidden.size),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            painterResource(if (showHidden) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (showHidden) {
                    itemsIndexed(loaded.hidden, key = { _, hidden -> "hidden-" + hidden.entry.product.id.value }) { index, hidden ->
                        HiddenEntryRow(hidden, onOpen, last = index == loaded.hidden.lastIndex)
                    }
                }
            }
            item(key = "bottom") { Spacer(Modifier.height(CartMateSpacing.xLarge)) }
        }
    }
}

/** 非表示の商品の行。商品名と、どちらの非表示かと非表示にした人を小さく添える */
@Composable
private fun HiddenEntryRow(hidden: ProductNamesViewModel.HiddenEntry, onOpen: (ProductId) -> Unit, last: Boolean) {
    val entry = hidden.entry
    val mode = stringResource(
        if (entry.mode == ProductMerge.SuggestionMode.ALWAYS_HIDDEN) R.string.suggestion_mode_always else R.string.suggestion_mode_temporary,
    )
    Column(Modifier.listCardSegment(first = false, last = last)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { onOpen(entry.product.id) }
                .heightIn(min = 64.dp)
                .padding(horizontal = CartMateSpacing.xLarge, vertical = CartMateSpacing.small),
        ) {
            Column(Modifier.weight(1f)) {
                Text(entry.product.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    listOf(mode, hidden.byName ?: stringResource(R.string.former_member)).joinToString("・"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Chevron()
        }
        if (!last) CardDivider()
    }
}

@Composable
private fun EntryRow(entry: ProductMerge.Entry, onOpen: (ProductId) -> Unit, first: Boolean, last: Boolean) {
    Column(Modifier.listCardSegment(first, last)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { onOpen(entry.product.id) }
                .heightIn(min = 64.dp)
                .padding(horizontal = CartMateSpacing.xLarge, vertical = CartMateSpacing.medium),
        ) {
            Column(Modifier.weight(1f)) {
                Text(entry.product.displayName, style = MaterialTheme.typography.titleMedium)
                entryDescription(entry)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Chevron()
        }
        if (!last) CardDivider()
    }
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
    var confirmDeleteMine by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ProductDetailViewModel.Event.Renamed -> onRenamed(event.target)
            }
        }
    }
    // 表示していた商品が一覧から外れたら、商品名の管理に戻って知らせる
    val context = LocalContext.current
    (state as? ProductDetailViewModel.State.Gone)?.let { gone ->
        val message = stringResource(R.string.product_detail_gone, gone.name)
        LaunchedEffect(gone) {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            onBack()
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
            ProductDetailViewModel.State.Loading, is ProductDetailViewModel.State.Gone -> Unit
            ProductDetailViewModel.State.NotFound -> Empty(stringResource(R.string.product_detail_not_found))
            is ProductDetailViewModel.State.Loaded -> when (val detail = current.detail) {
                is ProductMerge.Detail.Target -> TargetDetail(
                    entry = detail.entry,
                    summary = current.summary,
                    onUnmerge = viewModel::unmerge,
                    onOpen = onOpen,
                    onMerge = { onMerge(detail.entry.product.id) },
                    onDictionaryOptOut = viewModel::setDictionaryOptOut,
                    onRename = { renaming = true },
                    onOpenHistory = { onOpenHistory(detail.entry.product.id) },
                    onMode = viewModel::setMode,
                    ownPurchases = current.ownPurchases,
                    onDeleteMine = { confirmDeleteMine = true },
                )
                is ProductMerge.Detail.Merged -> MergedDetail(
                    target = detail.target,
                    onUnmerge = { viewModel.unmerge(detail.product.id) },
                    onOpenTarget = { onOpen(detail.target.id) },
                )
            }
        }
    }

    val loadedState = state as? ProductDetailViewModel.State.Loaded
    val loaded = loadedState?.detail as? ProductMerge.Detail.Target
    if (confirmDeleteMine && loaded != null && loadedState.ownPurchases > 0) {
        AlertDialog(
            onDismissRequest = { confirmDeleteMine = false },
            title = { Text(stringResource(R.string.product_delete_mine_title, loaded.entry.product.displayName, loadedState.ownPurchases)) },
            text = {
                Text(
                    listOfNotNull(
                        stringResource(R.string.purchase_delete_message),
                        // ほかのメンバーの購入は消せないので、残ることを伝える
                        loadedState.othersPurchases.takeIf { it > 0 }?.let { stringResource(R.string.product_delete_mine_others, it) },
                    ).joinToString("\n"),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteMine = false
                    viewModel.deleteMyPurchases()
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteMine = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
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

/** 統合先の商品の画面（docs/design/screens/item_detail-no_zip。まとめている商品の欄は item_detail-zip） */
@Composable
private fun TargetDetail(
    entry: ProductMerge.Entry,
    summary: PurchaseHistory.Summary?,
    onUnmerge: (ProductId) -> Unit,
    onOpen: (ProductId) -> Unit,
    onMerge: () -> Unit,
    onDictionaryOptOut: (Boolean) -> Unit,
    onRename: () -> Unit,
    onOpenHistory: () -> Unit,
    onMode: (ProductMerge.SuggestionMode) -> Unit,
    /** 自分がチェックした購入の数。1件以上なら「自分の購入をすべて削除」を出す */
    ownPurchases: Int = 0,
    onDeleteMine: () -> Unit = {},
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionLabel(stringResource(R.string.product_detail_members, entry.members.size))
        SectionCard {
            if (entry.members.isEmpty()) {
                Column(Modifier.padding(CartMateSpacing.large), verticalArrangement = Arrangement.spacedBy(CartMateSpacing.xSmall)) {
                    Text(stringResource(R.string.product_detail_no_members), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.product_detail_no_members_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(CartMateSpacing.small))
                    OutlinedButton(onClick = onMerge, shape = CircleShape, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        MergeButtonLabel()
                    }
                }
            } else {
                entry.members.forEach { member ->
                    MemberRow(member, onUnmerge = { onUnmerge(member.id) }, onOpen = { onOpen(member.id) })
                    CardDivider()
                }
                FilledTonalButton(
                    onClick = onMerge,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth().padding(CartMateSpacing.large).heightIn(min = 48.dp),
                ) {
                    MergeButtonLabel()
                }
            }
        }

        // 辞書の名寄せは、手動でまとめた商品と分けて示す
        val dictionary = entry.dictionary?.takeIf { it.isMeaningful }
        if (dictionary != null || entry.product.dictionaryOptOut) {
            SectionLabel(stringResource(R.string.product_detail_dictionary_section))
            SectionCard {
                Column(Modifier.padding(start = CartMateSpacing.large, end = CartMateSpacing.small, top = CartMateSpacing.large, bottom = CartMateSpacing.xSmall)) {
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
        }

        SectionLabel(stringResource(R.string.product_detail_prediction_section))
        SectionCard { ModeSelector(entry.mode, onMode) }

        SectionLabel(stringResource(R.string.product_detail_history_section))
        SectionCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CartMateSpacing.large),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = onOpenHistory)
                    .padding(CartMateSpacing.large),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Icon(painterResource(R.drawable.ic_history), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.product_detail_history), style = MaterialTheme.typography.titleMedium)
                    summary?.let {
                        val date = HISTORY_DATE.format(it.lastPurchase)
                        Text(
                            it.typicalIntervalDays?.let { days -> stringResource(R.string.product_detail_last_purchase_cycle, date, days) }
                                ?: stringResource(R.string.product_detail_last_purchase, date),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Chevron()
            }
        }

        SectionLabel(stringResource(R.string.product_detail_other_section))
        SectionCard {
            ActionRow(
                icon = R.drawable.ic_edit,
                title = stringResource(R.string.product_detail_rename),
                description = stringResource(R.string.product_detail_rename_description),
                onClick = onRename,
                trailing = { Chevron() },
            )
            // 削除できるのは自分がチェックした購入だけ（ほかのメンバーの購入は残る）
            if (ownPurchases > 0) {
                CardDivider()
                ActionRow(
                    icon = R.drawable.ic_delete,
                    title = stringResource(R.string.product_delete_mine, ownPurchases),
                    description = stringResource(R.string.product_delete_mine_description),
                    onClick = onDeleteMine,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Spacer(Modifier.height(CartMateSpacing.xLarge))
    }
}

@Composable
private fun MergeButtonLabel() {
    Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(CartMateSpacing.small))
    Text(stringResource(R.string.product_detail_merge), style = MaterialTheme.typography.titleSmall)
}

/** 「商品名を変更する」「自分の購入をすべて削除」の行。アイコン、名前、説明 */
@Composable
private fun ActionRow(
    icon: Int,
    title: String,
    description: String,
    onClick: () -> Unit,
    color: Color = Color.Unspecified,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CartMateSpacing.large),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(CartMateSpacing.large),
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color,
            modifier = Modifier.align(Alignment.Top),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CartMateSpacing.xSmall)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = color)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

/** サジェストへの出し方（docs/requirements.md §4.3）。通常どおり表示・一時的に非表示・常に非表示から選ぶ */
@Composable
private fun ModeSelector(mode: ProductMerge.SuggestionMode, onSelect: (ProductMerge.SuggestionMode) -> Unit) {
    val options = listOf(
        Triple(ProductMerge.SuggestionMode.NORMAL, R.string.suggestion_mode_normal, R.string.suggestion_mode_normal_description),
        Triple(ProductMerge.SuggestionMode.TEMPORARILY_HIDDEN, R.string.suggestion_mode_temporary, R.string.suggestion_mode_temporary_description),
        Triple(ProductMerge.SuggestionMode.ALWAYS_HIDDEN, R.string.suggestion_mode_always, R.string.suggestion_mode_always_description),
    )
    Column(Modifier.selectableGroup()) {
        options.forEachIndexed { index, (value, title, description) ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(CartMateSpacing.medium),
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = mode == value, role = Role.RadioButton, onClick = { onSelect(value) })
                    .padding(horizontal = CartMateSpacing.small, vertical = CartMateSpacing.medium),
            ) {
                RadioButton(selected = mode == value, onClick = null, modifier = Modifier.padding(CartMateSpacing.small))
                Column(Modifier.weight(1f).padding(end = CartMateSpacing.small)) {
                    Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (index < options.lastIndex) CardDivider()
        }
    }
}

@Composable
private fun MemberRow(member: Product, onUnmerge: () -> Unit, onOpen: () -> Unit) {
    val description = stringResource(R.string.product_detail_unmerge_description, member.displayName)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onOpen)
            .heightIn(min = 56.dp)
            .padding(start = CartMateSpacing.large, end = CartMateSpacing.small),
    ) {
        Text(member.displayName, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        TextButton(onClick = onUnmerge, modifier = Modifier.semantics { contentDescription = description }) {
            Text(stringResource(R.string.product_detail_unmerge), fontWeight = FontWeight.Bold)
        }
    }
}

/** 統合されている商品の画面。今の統合先と「解除」 */
@Composable
private fun MergedDetail(target: Product, onUnmerge: () -> Unit, onOpenTarget: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        SectionLabel(stringResource(R.string.product_detail_merged_section))
        SectionCard {
            Column(Modifier.padding(CartMateSpacing.large), verticalArrangement = Arrangement.spacedBy(CartMateSpacing.medium)) {
                Text(
                    stringResource(R.string.product_detail_merged_into, target.displayName),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.clickable(role = Role.Button, onClick = onOpenTarget),
                )
                Text(stringResource(R.string.product_detail_merged_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = onUnmerge, shape = CircleShape) { Text(stringResource(R.string.product_detail_unmerge)) }
            }
        }
    }
}

/**
 * 商品をまとめる（docs/design/screens/item_zip）。残す商品、検索欄、候補（今まとめている商品はチェックした状態）、
 * 「すべて解除」、下部の件数と「まとめる」
 */
@Composable
fun MergeProductsScreen(onBack: () -> Unit, onDone: () -> Unit, viewModel: MergeProductsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirming by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                MergeProductsViewModel.Event.Done -> onDone()
            }
        }
    }
    ProductScaffold(
        title = stringResource(R.string.merge_products_title),
        onBack = onBack,
        bottomBar = { state.target?.let { MergeBottomBar(state, it, onSubmit = { confirming = true }) } },
    ) {
        val target = state.target
        if (state.loading) return@ProductScaffold
        if (target == null) {
            Empty(stringResource(R.string.product_detail_not_found))
            return@ProductScaffold
        }
        val shown = state.shownChoices
        LazyColumn(Modifier.fillMaxSize()) {
            item(key = "target") {
                // 統合先は固定して上に出す
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = CartMateSpacing.large, vertical = CartMateSpacing.small),
                ) {
                    Column(Modifier.padding(CartMateSpacing.large + CartMateSpacing.xSmall)) {
                        Text(stringResource(R.string.merge_products_target), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(target.product.displayName, style = MaterialTheme.typography.headlineSmall)
                        Text(
                            stringResource(R.string.merge_products_hint, target.product.displayName),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item(key = "search") {
                SearchField(state.query, viewModel::search, stringResource(R.string.merge_products_search_hint))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(end = CartMateSpacing.small)) {
                    SectionLabel(stringResource(R.string.merge_products_choices), Modifier.weight(1f))
                    TextButton(onClick = viewModel::clearAll, enabled = state.selectedCount > 0 && !state.submitted) {
                        Text(stringResource(R.string.merge_products_clear), fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (shown.isEmpty()) {
                item(key = "empty") {
                    Empty(
                        if (state.query.isBlank()) stringResource(R.string.merge_products_empty)
                        else stringResource(R.string.product_names_no_match, state.query.trim()),
                    )
                }
            }
            itemsIndexed(shown, key = { _, choice -> choice.product.id.value }) { index, choice ->
                val checked = state.isChecked(choice)
                Column(Modifier.listCardSegment(first = index == 0, last = index == shown.lastIndex)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(CartMateSpacing.medium),
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(value = checked, enabled = !state.submitted, role = Role.Checkbox, onValueChange = { viewModel.toggle(choice) })
                            .heightIn(min = 56.dp)
                            .padding(horizontal = CartMateSpacing.large, vertical = CartMateSpacing.small),
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Column(Modifier.weight(1f)) {
                            Text(choice.product.displayName, style = MaterialTheme.typography.bodyLarge)
                            choice.entry?.let { entryDescription(it) }?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (index < shown.lastIndex) CardDivider()
                }
            }
            item(key = "bottom") { Spacer(Modifier.height(CartMateSpacing.xLarge)) }
        }
    }

    if (confirming) {
        val target = state.target
        if (target == null || !state.canSubmit) {
            confirming = false
        } else {
            MergeConfirmDialog(
                state = state,
                target = target,
                onConfirm = {
                    confirming = false
                    viewModel.submit()
                },
                onDismiss = { confirming = false },
            )
        }
    }
}

/** 下部の「N件を選択中」と、何が起きるか（「『テスト1』に統合されます」「1件を追加・1件を解除します」）と「まとめる」 */
@Composable
private fun MergeBottomBar(state: MergeProductsViewModel.State, target: ProductMerge.Entry, onSubmit: () -> Unit) {
    // 背景はナビゲーションバーの下まで伸ばし、件数とボタンはナビゲーションバーの上に置く（画面の端まで描いているため）
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CartMateSpacing.medium),
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(CartMateSpacing.large),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            ) {
                Icon(painterResource(R.drawable.ic_merge), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.merge_products_selected, state.selectedCount), style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        state.removed.isEmpty() -> stringResource(R.string.merge_products_into, target.product.displayName)
                        state.added.isEmpty() -> stringResource(R.string.merge_products_remove_only, state.removed.size)
                        else -> stringResource(R.string.merge_products_add_remove, state.added.size, state.removed.size)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onSubmit, enabled = state.canSubmit, shape = CircleShape, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.merge_products_confirm_action))
            }
        }
    }
}

/** まとめる商品と解除する商品、一緒に移る商品、常に非表示の統合先であることを確かめる */
@Composable
private fun MergeConfirmDialog(
    state: MergeProductsViewModel.State,
    target: ProductMerge.Entry,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val added = state.addedEntries
    val removed = state.removedProducts
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.merge_products_confirm_title, target.product.displayName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(CartMateSpacing.small)) {
                if (added.isNotEmpty()) {
                    Text(stringResource(R.string.merge_products_confirm_add, added.joinToString("、") { it.product.displayName }))
                    // 選んだ商品にまとまっている商品も、一緒に移ることを伝える
                    added.filter { it.members.isNotEmpty() }.forEach {
                        Text(stringResource(R.string.merge_products_confirm_members, it.product.displayName, it.members.size))
                    }
                    // まとめた商品は統合先の設定に従う。統合先が常に非表示なら、そのことを伝える
                    if (target.product.suggestionExcluded) {
                        Text(stringResource(R.string.merge_products_confirm_always_hidden, target.product.displayName))
                    }
                }
                if (removed.isNotEmpty()) {
                    Text(stringResource(R.string.merge_products_confirm_remove, removed.joinToString("、") { it.displayName }))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.merge_products_confirm_action)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
