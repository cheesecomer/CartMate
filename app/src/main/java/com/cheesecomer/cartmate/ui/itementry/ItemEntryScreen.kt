package com.cheesecomer.cartmate.ui.itementry

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.AssistChip
import com.cheesecomer.cartmate.domain.suggest.SuggestionEngine
import java.time.format.DateTimeFormatter
import com.cheesecomer.cartmate.R
import com.cheesecomer.cartmate.domain.input.ItemEntry
import com.cheesecomer.cartmate.domain.input.formatQuantity
import com.cheesecomer.cartmate.domain.model.Visibility
import com.cheesecomer.cartmate.ui.components.SelectableOption
import com.cheesecomer.cartmate.ui.components.cardFrame
import com.cheesecomer.cartmate.ui.theme.CartMateSpacing
import com.cheesecomer.cartmate.ui.theme.CartMateTheme

object ItemEntryTags {
    const val NAME = "entry_name"
    const val QUANTITY = "entry_quantity"
    const val UNIT = "entry_unit"
    const val MEMO = "entry_memo"
}

/** 数量のチップ（1〜6） */
private val QuantityChips = (1..6).map(Int::toDouble)

/** 単位のチップ */
private val UnitChips = listOf("個", "本", "パック", "袋", "箱", "g")

/** @param onClose 画面を閉じる。追加・更新した場合も、やめた場合も呼ばれる */
@Composable
fun ItemEntryScreen(
    onClose: (ItemEntryResult) -> Unit,
    viewModel: ItemEntryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val memberCount by viewModel.memberCount.collectAsStateWithLifecycle()
    val mergeChip by viewModel.mergeChip.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.results.collect(onClose) }
    ItemEntryContent(
        state = state,
        memberCount = memberCount,
        mergeChip = mergeChip,
        history = history,
        onSelectMemo = viewModel::selectMemo,
        onToggleMerge = viewModel::toggleMerge,
        onClose = { onClose(ItemEntryResult.CANCELLED) },
        onNameChange = viewModel::onNameChange,
        onQuantityChange = viewModel::onQuantityChange,
        onIncrement = viewModel::increment,
        onDecrement = viewModel::decrement,
        onUnitChange = viewModel::onUnitChange,
        onMemoChange = viewModel::onMemoChange,
        onVisibilityChange = viewModel::onVisibilityChange,
        onConfirm = viewModel::confirm,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemEntryContent(
    state: ItemEntryUiState,
    onClose: () -> Unit,
    onNameChange: (String) -> Unit,
    onQuantityChange: (String) -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onUnitChange: (String) -> Unit,
    onMemoChange: (String) -> Unit,
    onVisibilityChange: (Visibility) -> Unit,
    onConfirm: () -> Unit,
    /** 家族の人数。「追加先: スーパー（共有リスト 3人）」に表示する（読めるまでは 0） */
    memberCount: Int = 0,
    /** 表記ゆれのチップ。候補がなければ null */
    mergeChip: MergeChip? = null,
    onToggleMerge: () -> Unit = {},
    /** 前回の購入と備考の候補。新しい商品の追加だけ */
    history: EntryHistory? = null,
    onSelectMemo: (String) -> Unit = {},
) {
    val entry = state.entry
    val editing = entry?.editing != null
    // 戻るキーでも、やめたことを呼び出し元に伝える（新規ならサジェストのシートに戻る）
    BackHandler(onBack = onClose)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (editing) R.string.entry_title_edit else R.string.entry_title_new)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(painterResource(R.drawable.ic_close), stringResource(R.string.action_close))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Button(
                    onClick = onConfirm,
                    enabled = entry?.canConfirm == true,
                    shape = CircleShape,
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                        .padding(horizontal = CartMateSpacing.large, vertical = CartMateSpacing.medium)
                        .fillMaxWidth()
                        .height(56.dp),
                ) {
                    Text(
                        stringResource(if (editing) R.string.entry_update else R.string.entry_add),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (!editing) {
                        Spacer(Modifier.width(CartMateSpacing.small))
                        Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                }
            }
        },
    ) { innerPadding ->
        if (entry == null) return@Scaffold
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CartMateSpacing.large, vertical = CartMateSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(CartMateSpacing.medium),
        ) {
            Destination(state.listName, state.listVisibility, memberCount)
            Section {
                OutlinedTextField(
                    value = entry.name,
                    onValueChange = onNameChange,
                    label = { Text(stringResource(R.string.entry_name_label)) },
                    textStyle = MaterialTheme.typography.titleLarge,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().testTag(ItemEntryTags.NAME),
                )
                history?.previous?.let { PreviousPurchaseText(it) }
                // 似た名前の商品があれば「『牛乳』と同じ商品として記録する」。初期は未選択（docs/design/suggestions.md）
                mergeChip?.let { chip ->
                    FilterChip(
                        selected = chip.selected,
                        onClick = onToggleMerge,
                        label = { Text(stringResource(R.string.entry_merge_chip, chip.candidateName)) },
                        leadingIcon = if (chip.selected) {
                            { Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else {
                            null
                        },
                        modifier = Modifier.padding(top = CartMateSpacing.small),
                    )
                }
            }
            QuantitySection(entry, onQuantityChange, onIncrement, onDecrement)
            UnitSection(entry, onUnitChange)
            // プライベートのリストでは、すべてプライベートになるので表示しない
            if (state.listVisibility == Visibility.SHARED) VisibilitySection(entry, onVisibilityChange)
            MemoSection(entry, onMemoChange, history?.memoCandidates.orEmpty(), onSelectMemo)
        }
    }
}

@Composable
private fun Destination(listName: String, listVisibility: Visibility, memberCount: Int) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceContainerLow, CircleShape)
            .padding(horizontal = CartMateSpacing.large, vertical = CartMateSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CartMateSpacing.small),
    ) {
        Icon(painterResource(R.drawable.ic_store), contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
        Text(stringResource(R.string.entry_destination), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        Text(listName, style = MaterialTheme.typography.titleSmall)
        Text(
            when {
                listVisibility == Visibility.PRIVATE -> stringResource(R.string.entry_destination_private)
                memberCount > 0 -> stringResource(R.string.entry_destination_shared_members, memberCount)
                else -> stringResource(R.string.entry_destination_shared)
            },
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun Section(
    title: String? = null,
    trailing: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .cardFrame()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(CartMateSpacing.large),
        verticalArrangement = Arrangement.spacedBy(CartMateSpacing.medium),
    ) {
        if (title != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (trailing != null) {
                    Text(trailing, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        content()
    }
}

@Composable
private fun QuantitySection(
    entry: ItemEntry,
    onQuantityChange: (String) -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val quantityLabel = stringResource(R.string.entry_quantity)
    Section(title = quantityLabel) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            FilledTonalIconButton(
                onClick = onDecrement,
                modifier = Modifier.size(56.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = colors.primaryContainer),
            ) {
                Icon(painterResource(R.drawable.ic_remove), stringResource(R.string.entry_quantity_decrement))
            }
            Row(verticalAlignment = Alignment.Bottom) {
                // 数字をタップして直接入力もできる
                BasicTextField(
                    value = entry.quantity,
                    onValueChange = onQuantityChange,
                    textStyle = TextStyle(
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface,
                        textAlign = TextAlign.Center,
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    cursorBrush = SolidColor(colors.primary),
                    // 数字の幅に合わせ、単位との間が空きすぎないようにする
                    modifier = Modifier
                        .width(IntrinsicSize.Min)
                        .widthIn(min = 32.dp, max = 120.dp)
                        .semantics { contentDescription = quantityLabel }
                        .testTag(ItemEntryTags.QUANTITY),
                )
                if (entry.unit.isNotBlank()) {
                    Text(
                        entry.unit,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(start = CartMateSpacing.xSmall, bottom = 6.dp),
                    )
                }
            }
            FilledIconButton(onClick = onIncrement, modifier = Modifier.size(56.dp)) {
                Icon(painterResource(R.drawable.ic_add), stringResource(R.string.entry_quantity_increment))
            }
        }
        ChipRow(
            labels = QuantityChips.map(::formatQuantity),
            selected = entry.quantityValue?.let(::formatQuantity),
            onSelect = onQuantityChange,
        )
    }
}

@Composable
private fun UnitSection(entry: ItemEntry, onUnitChange: (String) -> Unit) {
    Section(title = stringResource(R.string.entry_unit)) {
        OutlinedTextField(
            value = entry.unit,
            onValueChange = onUnitChange,
            singleLine = true,
            shape = CircleShape,
            trailingIcon = if (entry.unit.isNotEmpty()) {
                {
                    IconButton(onClick = { onUnitChange("") }) {
                        Icon(painterResource(R.drawable.ic_close), stringResource(R.string.action_clear))
                    }
                }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth().testTag(ItemEntryTags.UNIT),
        )
        ChipRow(labels = UnitChips, selected = entry.unit.trim(), onSelect = onUnitChange)
    }
}

@Composable
private fun VisibilitySection(entry: ItemEntry, onVisibilityChange: (Visibility) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Section(title = stringResource(R.string.entry_visibility)) {
        Text(
            stringResource(R.string.entry_visibility_description),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(CartMateSpacing.small)) {
            SelectableOption(
                title = stringResource(R.string.entry_visibility_shared),
                description = stringResource(R.string.entry_visibility_shared_description),
                selected = entry.visibility == Visibility.SHARED,
                onSelect = { onVisibilityChange(Visibility.SHARED) },
                icon = R.drawable.ic_group,
                enabled = entry.visibilitySelectable,
                modifier = Modifier.weight(1f),
            )
            SelectableOption(
                title = stringResource(R.string.entry_visibility_private),
                description = stringResource(R.string.entry_visibility_private_description),
                selected = entry.visibility == Visibility.PRIVATE,
                onSelect = { onVisibilityChange(Visibility.PRIVATE) },
                icon = R.drawable.ic_lock,
                enabled = entry.visibilitySelectable,
                modifier = Modifier.weight(1f),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_info), contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(16.dp))
            Text(
                stringResource(R.string.entry_visibility_note),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(start = CartMateSpacing.small),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemoSection(entry: ItemEntry, onMemoChange: (String) -> Unit, candidates: List<String>, onSelect: (String) -> Unit) {
    Section(title = stringResource(R.string.entry_memo), trailing = stringResource(R.string.entry_memo_optional)) {
        OutlinedTextField(
            value = entry.memo,
            onValueChange = onMemoChange,
            placeholder = { Text(stringResource(R.string.entry_memo_placeholder)) },
            minLines = 3,
            modifier = Modifier.fillMaxWidth().testTag(ItemEntryTags.MEMO),
        )
        // 過去にその商品に付けた備考（新しい順に3件まで）。選ぶと備考をその文にする
        if (candidates.isNotEmpty()) {
            FlowRow(
                verticalArrangement = Arrangement.Center,
                horizontalArrangement = Arrangement.spacedBy(CartMateSpacing.small),
                modifier = Modifier.padding(top = CartMateSpacing.small),
            ) {
                Text(
                    stringResource(R.string.entry_memo_candidates),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
                candidates.forEach { memo ->
                    AssistChip(onClick = { onSelect(memo) }, label = { Text(memo) }, shape = CircleShape)
                }
            }
        }
    }
}

/** 「前回: 1パック（10/18 購入・約14日ごと）」。早すぎる再購入なら注意を添える（追加は妨げない） */
@Composable
private fun PreviousPurchaseText(previous: SuggestionEngine.PreviousPurchase) {
    val amount = listOfNotNull(previous.quantity?.let(::formatQuantity), previous.unit).joinToString("")
    val date = PREVIOUS_DATE.format(previous.date)
    val text = previous.typicalIntervalDays
        ?.let { stringResource(R.string.entry_previous_with_cycle, amount, date, it) }
        ?: stringResource(R.string.entry_previous, amount, date)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = CartMateSpacing.small)) {
        Icon(
            painterResource(R.drawable.ic_history),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(CartMateSpacing.xSmall))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    previous.early?.let {
        Text(
            stringResource(R.string.entry_early, it.typicalIntervalDays),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.padding(top = CartMateSpacing.xSmall),
        )
    }
}

/** 「10/18」 */
private val PREVIOUS_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(labels: List<String>, selected: String?, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(CartMateSpacing.small)) {
        labels.forEach { label ->
            FilterChip(
                selected = label == selected,
                onClick = { onSelect(label) },
                label = { Text(label, style = MaterialTheme.typography.titleSmall) },
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}

@Preview
@Composable
private fun ItemEntryContentPreview() {
    CartMateTheme {
        ItemEntryContent(
            state = ItemEntryUiState(
                listName = "スーパー",
                listVisibility = Visibility.SHARED,
                entry = ItemEntry.forNew("たまご", 1.0, "パック", Visibility.SHARED, null),
            ),
            onClose = {},
            onNameChange = {},
            onQuantityChange = {},
            onIncrement = {},
            onDecrement = {},
            onUnitChange = {},
            onMemoChange = {},
            onVisibilityChange = {},
            onConfirm = {},
        )
    }
}
