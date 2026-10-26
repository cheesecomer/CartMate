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
import com.cheesecomer.cartmate.R
import com.cheesecomer.cartmate.domain.input.AddItemEvent
import com.cheesecomer.cartmate.domain.input.AddItemState
import com.cheesecomer.cartmate.domain.model.Item
import com.cheesecomer.cartmate.ui.components.KeyboardSheet
import com.cheesecomer.cartmate.ui.theme.CartMateSpacing

object AddItemTags {
    const val FIELD = "add_item_field"
    const val SHEET_INPUT = "add_item_sheet_input"
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
 * 候補（時期が近い商品・補完）はフェーズ4で追加する
 */
@Composable
fun AddItemSheet(state: AddItemState.Open, onEvent: (AddItemEvent) -> Unit) {
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
        Spacer(Modifier.size(CartMateSpacing.medium))
        if (value.text.isNotBlank()) {
            DetailRow(onClick = { onEvent(AddItemEvent.DetailRequested) })
        } else {
            Text(
                stringResource(R.string.add_item_suggestion_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = CartMateSpacing.xLarge, vertical = CartMateSpacing.large),
            )
        }
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
