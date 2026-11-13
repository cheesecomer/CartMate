package com.cheesecomer.cartmate.ui.listdetail

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import java.time.format.DateTimeFormatter
import com.cheesecomer.cartmate.R
import com.cheesecomer.cartmate.domain.input.AddItemEvent
import com.cheesecomer.cartmate.domain.input.AddItemState
import com.cheesecomer.cartmate.domain.model.Item
import com.cheesecomer.cartmate.domain.suggest.DueStatus
import androidx.compose.ui.text.font.FontWeight
import com.cheesecomer.cartmate.ui.components.KeyboardSheet
import com.cheesecomer.cartmate.ui.theme.CartMateSpacing
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.Arrangement
import com.cheesecomer.cartmate.domain.model.Visibility

object AddItemTags {
    const val FIELD = "add_item_field"
    /** 画面下部の商品追加欄の背景 */
    const val BAR = "add_item_bar"
    const val SHEET_INPUT = "add_item_sheet_input"
    const val SUGGESTION = "add_item_suggestion"
}

/**
 * 画面下部に常に表示する商品追加欄。タップするとサジェストのシートを開く（docs/design/ui.md「商品の追加」）。
 * シートを閉じている間は、入力途中の商品名を表示する
 */
@Composable
fun AddItemField(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    // 背景はナビゲーションバーの下まで伸ばし、入力欄はナビゲーションバーの上に置く（画面の端まで描いているので、
    // 余白を背景の外に付けると、そこから一覧のカードが透けて見える）
    Surface(modifier = modifier.fillMaxWidth().testTag(AddItemTags.BAR), color = colors.surface) {
        Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
            HorizontalDivider(color = colors.outlineVariant)
            Row(
                modifier = Modifier
                    .padding(horizontal = CartMateSpacing.large, vertical = CartMateSpacing.medium)
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceContainerLow)
                    .border(1.dp, colors.outlineVariant, CircleShape)
                    .clickable(role = Role.Button, onClick = onClick)
                    .padding(horizontal = CartMateSpacing.large)
                    .testTag(AddItemTags.FIELD),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null, tint = colors.primary)
                Spacer(Modifier.width(CartMateSpacing.large))
                Text(
                    text.ifEmpty { stringResource(R.string.add_item_placeholder) },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (text.isEmpty()) colors.onSurfaceVariant else colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(painterResource(R.drawable.ic_search), contentDescription = null, tint = colors.onSurfaceVariant)
            }
        }
    }
}

/**
 * サジェストのシート。キーボードと一緒にせり上がる（docs/design/ui.md「商品追加欄とサジェストのシート」）。
 * 未入力なら時期が近い商品、入力中なら補完を出す。[state] は Open か、商品の画面へ移っている間の OpeningProduct
 */
@Composable
fun AddItemSheet(
    state: AddItemState,
    onEvent: (AddItemEvent) -> Unit,
    /** 追加するときの公開範囲。null なら表示しない（プライベートのリスト） */
    visibility: Visibility? = null,
    onVisibilityChange: (Visibility) -> Unit = {},
    suggestions: SheetSuggestions = SheetSuggestions.Loading,
) {
    val focusRequester = remember { FocusRequester() }
    // カーソル位置を保つため TextFieldValue で持ち、外から変わったとき（Enter で空にした など）だけ置き換える
    var value by remember { mutableStateOf(TextFieldValue(state.text, TextRange(state.text.length))) }
    if (value.text != state.text) value = TextFieldValue(state.text, TextRange(state.text.length))

    KeyboardSheet(
        onDismiss = { onEvent(AddItemEvent.Dismissed) },
        focusRequester = focusRequester,
        // 商品の画面へ移っている間の戻るキーは、商品の画面に渡す
        backEnabled = state is AddItemState.Open,
    ) {
        AddItemSheetContent(
            value = value,
            onValueChange = {
                value = it.copy(text = it.text.take(Item.MAX_NAME_LENGTH))
                onEvent(AddItemEvent.TextChanged(value.text))
            },
            onEvent = onEvent,
            focusRequester = focusRequester,
            visibility = visibility,
            onVisibilityChange = onVisibilityChange,
            suggestions = suggestions,
        )
    }
}

/** サジェストのシートの中身 */
@Composable
internal fun AddItemSheetContent(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onEvent: (AddItemEvent) -> Unit,
    /** 入力欄にフォーカスを移すためのもの（プレビューでは渡さない） */
    focusRequester: FocusRequester = remember { FocusRequester() },
    visibility: Visibility? = null,
    onVisibilityChange: (Visibility) -> Unit = {},
    suggestions: SheetSuggestions = SheetSuggestions.Loading,
) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(bottom = CartMateSpacing.large)) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(stringResource(R.string.add_item_sheet_label)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null, tint = colors.primary) },
            trailingIcon = if (value.text.isNotEmpty()) {
                {
                    IconButton(onClick = { onEvent(AddItemEvent.TextChanged("")) }) {
                        Icon(painterResource(R.drawable.ic_close), stringResource(R.string.action_clear))
                    }
                }
            } else {
                null
            },
            singleLine = true,
            // Enter で追加しても、キーボードは閉じずに次の商品を入力できるようにする
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onEvent(AddItemEvent.Submitted) }),
            shape = CircleShape,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = colors.surfaceContainer,
                unfocusedContainerColor = colors.surfaceContainer,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CartMateSpacing.large)
                .focusRequester(focusRequester)
                .testTag(AddItemTags.SHEET_INPUT),
        )
        if (visibility != null) VisibilityChips(visibility, onVisibilityChange)
        Spacer(Modifier.size(CartMateSpacing.medium))
        if (value.text.isNotBlank()) DetailRow(onClick = { onEvent(AddItemEvent.DetailRequested) })
        when (suggestions) {
            SheetSuggestions.Loading -> Unit
            SheetSuggestions.Hint -> if (value.text.isBlank()) {
                Text(
                    stringResource(R.string.add_item_suggestion_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = CartMateSpacing.xLarge, vertical = CartMateSpacing.large),
                )
            }
            is SheetSuggestions.Items -> if (suggestions.rows.isNotEmpty()) {
                // 長押しで商品の画面を開けることに気づけるよう、一覧の上に案内を出す（フィードバック #014）。
                // 商品のない候補（まだ買ったことのない名前）は長押しできないので、長押しできる候補があるときだけ出す。
                // 入力中は補完の候補を多く見せるため、未入力のときだけ出す（フィードバック #022）
                if (value.text.isBlank() && suggestions.rows.any { it.suggestion.productId != null }) {
                    Text(
                        stringResource(R.string.add_item_long_press_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = CartMateSpacing.xLarge).padding(bottom = CartMateSpacing.xSmall),
                    )
                }
                // シートがキーボードより上に伸びすぎないよう、一覧の高さを抑えて中でスクロールする
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = SUGGESTIONS_MAX_HEIGHT)) {
                    itemsIndexed(suggestions.rows, key = { index, row -> "$index:${row.label}" }) { _, row ->
                        SuggestionRowView(
                            row = row,
                            onOpen = { onEvent(AddItemEvent.SuggestionOpened(row.suggestion)) },
                            onAdd = { onEvent(AddItemEvent.SuggestionAdded(row.suggestion)) },
                            onLongPress = { onEvent(AddItemEvent.SuggestionLongPressed(row.suggestion)) },
                        )
                    }
                }
            }
        }
    }
}

/** 候補の一覧の最大の高さ。4行ほどが見える */
private val SUGGESTIONS_MAX_HEIGHT = 300.dp

/** 「10/18」 */
private val NOTE_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d")

/**
 * 候補の1行。名前のタップで商品詳細入力画面、右端の「＋」で前回の数量・単位のまますぐ追加する。
 * 名前の長押しで商品の画面（まとめる・非表示など）を開く
 */
@Composable
private fun SuggestionRowView(row: SuggestionRow, onOpen: () -> Unit, onAdd: () -> Unit, onLongPress: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = CartMateSpacing.large)
            .testTag(AddItemTags.SUGGESTION),
    ) {
        Column(
            Modifier
                .weight(1f)
                .clip(MaterialTheme.shapes.medium)
                .combinedClickable(
                    role = Role.Button,
                    onClick = onOpen,
                    onLongClick = onLongPress.takeIf { row.suggestion.productId != null },
                    onLongClickLabel = stringResource(R.string.add_item_open_product),
                )
                .padding(horizontal = CartMateSpacing.small, vertical = CartMateSpacing.small),
        ) {
            val status = statusChip(row.note)
            if (status == null) {
                Text(row.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                // 未入力の候補は、名前の横に買い時の状態（「あと1日」「3日過ぎ」）か「よく買う」のチップを出す
                // （デザインは docs/design/screens/product-often-suggestions-sheet）
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        row.label,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(CartMateSpacing.small))
                    StatusChip(status)
                }
            }
            noteText(row.note)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
        val description = stringResource(R.string.add_item_add_suggestion, row.label)
        // 押せる範囲は 48dp のまま、見える丸は小さくして、候補の名前より目立たせない（UI レビュー UI001-3）
        IconButton(onClick = onAdd, modifier = Modifier.size(48.dp).semantics { contentDescription = description }) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(32.dp).background(colors.primary, CircleShape),
            ) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** 候補の名前の横に出すチップ。[emphasized] は時期が近い商品（primary の系統）、そうでなければよく買う商品（控えめな灰色の系統） */
private data class ChipLabel(val text: String, val emphasized: Boolean)

/** 時期が近い商品は買い時の状態、よく買う商品は「よく買う」。補完の候補には出さない */
@Composable
private fun statusChip(note: SuggestionNote): ChipLabel? = when (note) {
    is SuggestionNote.Due -> ChipLabel(
        when (val status = DueStatus.of(note.intervalDays, note.daysSinceLast)) {
            is DueStatus.Remaining -> stringResource(R.string.add_item_status_remaining, status.days.toInt())
            DueStatus.Today -> stringResource(R.string.add_item_status_today)
            is DueStatus.Overdue -> stringResource(R.string.add_item_status_overdue, status.days.toInt())
        },
        emphasized = true,
    )
    is SuggestionNote.Frequent -> ChipLabel(stringResource(R.string.add_item_status_frequent), emphasized = false)
    is SuggestionNote.Completion -> null
}

/**
 * 状態のチップ。押せないので、Material の Chip ではなく文字に背景を付けただけにする。
 * 遅れを煽らないよう、過ぎていても色は変えない（大きく過ぎた商品は、わざと順位を下げているため）
 */
@Composable
private fun StatusChip(label: ChipLabel) {
    val colors = MaterialTheme.colorScheme
    Text(
        label.text,
        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
        color = if (label.emphasized) colors.primary else colors.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier
            .background(if (label.emphasized) colors.primaryContainer else colors.surfaceContainer, CircleShape)
            .padding(horizontal = CartMateSpacing.small, vertical = 2.dp),
    )
}

/** 候補の補足。時期が近い商品は周期と前回からの日数、よく買う商品は買った日の数と前回からの日数、補完は「追加済み」と早すぎる再購入 */
@Composable
private fun noteText(note: SuggestionNote): String? = when (note) {
    is SuggestionNote.Due -> stringResource(R.string.add_item_note_due, note.intervalDays, note.daysSinceLast.toInt())
    is SuggestionNote.Frequent -> stringResource(R.string.add_item_note_frequent, note.purchaseDays, note.daysSinceLast.toInt())
    is SuggestionNote.Completion -> listOfNotNull(
        // どのリストに入っているか分かるように、リストの名前を添える
        when {
            !note.alreadyAdded -> null
            note.addedLists.isEmpty() -> stringResource(R.string.add_item_note_added)
            else -> stringResource(R.string.add_item_note_added_in, note.addedLists.joinToString("・"))
        },
        note.early?.let { early ->
            val date = NOTE_DATE.format(early.lastPurchase)
            early.typicalIntervalDays
                ?.let { stringResource(R.string.add_item_note_early, date, it) }
                ?: stringResource(R.string.add_item_note_recent, date)
        },
    ).joinToString("・").ifEmpty { null }
}

/**
 * Enter と「＋」で追加するときの公開範囲（docs/requirements.md §3.4）。直前の公開範囲を引き継ぐので、
 * 時間がたっても今どちらで追加されるか分かるように表示し、ここでも切り替えられるようにする
 */
@Composable
private fun VisibilityChips(visibility: Visibility, onChange: (Visibility) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CartMateSpacing.small),
        modifier = Modifier.padding(start = CartMateSpacing.xLarge, end = CartMateSpacing.large, top = CartMateSpacing.small),
    ) {
        Text(
            stringResource(R.string.entry_visibility),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FilterChip(
            selected = visibility == Visibility.SHARED,
            onClick = { onChange(Visibility.SHARED) },
            label = { Text(stringResource(R.string.entry_visibility_shared)) },
        )
        FilterChip(
            selected = visibility == Visibility.PRIVATE,
            onClick = { onChange(Visibility.PRIVATE) },
            label = { Text(stringResource(R.string.entry_visibility_private)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_lock), contentDescription = null, modifier = Modifier.size(16.dp)) },
        )
    }
}

/** 「数量を設定して追加」。商品名は含めない（長い名前で崩れないように） */
@Composable
private fun DetailRow(onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .padding(horizontal = CartMateSpacing.large)
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(MaterialTheme.shapes.large)
            .background(colors.primaryContainer)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = CartMateSpacing.large),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_tune), contentDescription = null, tint = colors.primary)
        Spacer(Modifier.width(CartMateSpacing.medium))
        Text(
            stringResource(R.string.add_item_detail),
            style = MaterialTheme.typography.titleSmall,
            color = colors.primary,
            modifier = Modifier.weight(1f),
        )
        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = colors.primary)
    }
}
