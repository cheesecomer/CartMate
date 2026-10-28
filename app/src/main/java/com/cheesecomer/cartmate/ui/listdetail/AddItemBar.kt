package com.cheesecomer.cartmate.ui.listdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.FilledIconButton
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import java.time.format.DateTimeFormatter
import com.cheesecomer.cartmate.R
import com.cheesecomer.cartmate.domain.input.AddItemEvent
import com.cheesecomer.cartmate.domain.input.AddItemState
import com.cheesecomer.cartmate.domain.model.Item
import com.cheesecomer.cartmate.ui.components.KeyboardSheet
import com.cheesecomer.cartmate.ui.theme.CartMateSpacing
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.Arrangement
import com.cheesecomer.cartmate.domain.model.Visibility

object AddItemTags {
    const val FIELD = "add_item_field"
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
    Surface(modifier = modifier.fillMaxWidth(), color = colors.surface) {
        Column {
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
 * 未入力なら時期が近い商品、入力中なら補完を出す
 */
@Composable
fun AddItemSheet(
    state: AddItemState.Open,
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

    KeyboardSheet(onDismiss = { onEvent(AddItemEvent.Dismissed) }, focusRequester = focusRequester) {
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
                // シートがキーボードより上に伸びすぎないよう、一覧の高さを抑えて中でスクロールする
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = SUGGESTIONS_MAX_HEIGHT)) {
                    itemsIndexed(suggestions.rows, key = { index, row -> "$index:${row.label}" }) { _, row ->
                        SuggestionRowView(
                            row = row,
                            onOpen = { onEvent(AddItemEvent.SuggestionOpened(row.suggestion)) },
                            onAdd = { onEvent(AddItemEvent.SuggestionAdded(row.suggestion)) },
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

/** 候補の1行。名前のタップで商品詳細入力画面、右端の「＋」で前回の数量・単位のまますぐ追加する */
@Composable
private fun SuggestionRowView(row: SuggestionRow, onOpen: () -> Unit, onAdd: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(horizontal = CartMateSpacing.large)
            .testTag(AddItemTags.SUGGESTION),
    ) {
        Column(
            Modifier
                .weight(1f)
                .clip(MaterialTheme.shapes.medium)
                .clickable(role = Role.Button, onClick = onOpen)
                .padding(horizontal = CartMateSpacing.small, vertical = CartMateSpacing.small),
        ) {
            Text(row.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            noteText(row.note)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
        val description = stringResource(R.string.add_item_add_suggestion, row.label)
        FilledIconButton(
            onClick = onAdd,
            shape = CircleShape,
            modifier = Modifier.size(48.dp).semantics { contentDescription = description },
        ) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = null)
        }
    }
}

/** 候補の補足。時期が近い商品は周期と前回からの日数、補完は「追加済み」と早すぎる再購入 */
@Composable
private fun noteText(note: SuggestionNote): String? = when (note) {
    is SuggestionNote.Due -> stringResource(R.string.add_item_note_due, note.intervalDays, note.daysSinceLast.toInt())
    is SuggestionNote.Completion -> listOfNotNull(
        stringResource(R.string.add_item_note_added).takeIf { note.alreadyAdded },
        note.early?.let { stringResource(R.string.add_item_note_early, NOTE_DATE.format(it.lastPurchase), it.typicalIntervalDays) },
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
