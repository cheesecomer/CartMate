package com.cheesecomer.cartmate.ui.listdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cheesecomer.cartmate.R
import com.cheesecomer.cartmate.domain.input.AddItemEvent
import com.cheesecomer.cartmate.domain.input.AddItemState
import com.cheesecomer.cartmate.domain.model.Item

object AddItemBarTags {
    const val NAME = "add_item_name"
    const val EDIT_NAME = "add_item_edit_name"
    const val QUANTITY = "add_item_quantity"
    const val UNIT = "add_item_unit"
}

/**
 * 画面下部に常に表示する商品追加欄。項目の編集にも使う。状態の遷移は domain の AddItemReducer が決める。
 * サジェストパネルはフェーズ4で、この欄の上に追加する。
 */
@Composable
fun AddItemBar(state: AddItemState, onEvent: (AddItemEvent) -> Unit, modifier: Modifier = Modifier) {
    val nameFocus = remember { FocusRequester() }
    val quantityFocus = remember { FocusRequester() }
    var wasDetail by remember { mutableStateOf(false) }
    val isDetail = state is AddItemState.Detail
    // 編集は項目ごとに始まるので、項目が変わったら数量欄にフォーカスし直す
    val editingItemId = (state as? AddItemState.Editing)?.itemId

    // 数量入力に入ったら数量欄、数量入力から商品名の入力に戻ったら商品名の欄にフォーカスを移す
    LaunchedEffect(isDetail) {
        if (isDetail) quantityFocus.requestFocus() else if (wasDetail) nameFocus.requestFocus()
        wasDetail = isDetail
    }
    LaunchedEffect(editingItemId) {
        if (editingItemId != null) quantityFocus.requestFocus()
    }

    // クリーンな印象にするため、影や色付けは使わず、上辺の細い線でリストと区切る
    Surface(modifier = modifier.fillMaxWidth()) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                when (state) {
                    is AddItemState.Detail -> DetailInput(state, onEvent, quantityFocus)
                    is AddItemState.Editing -> EditInput(state, onEvent, quantityFocus)
                    else -> NameInput(state, onEvent, nameFocus)
                }
            }
        }
    }
}

@Composable
private fun NameInput(state: AddItemState, onEvent: (AddItemEvent) -> Unit, focusRequester: FocusRequester) {
    val text = when (state) {
        is AddItemState.Idle -> state.text
        is AddItemState.Typing -> state.text
        else -> ""
    }
    OutlinedTextField(
        value = text,
        onValueChange = { onEvent(AddItemEvent.NameChanged(it.take(Item.MAX_NAME_LENGTH))) },
        placeholder = { Text(stringResource(R.string.add_item_name_label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { onEvent(AddItemEvent.NameSubmitted) }),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { onEvent(AddItemEvent.NameFocusChanged(it.isFocused)) }
            .testTag(AddItemBarTags.NAME),
    )
}

@Composable
private fun DetailInput(
    state: AddItemState.Detail,
    onEvent: (AddItemEvent) -> Unit,
    quantityFocus: FocusRequester,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            state.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        CancelButton(stringResource(R.string.add_item_cancel)) { onEvent(AddItemEvent.Cancelled) }
    }
    QuantityRow(
        key = state.name,
        quantity = state.quantity,
        unit = state.unit,
        canConfirm = state.canConfirm,
        confirmLabel = stringResource(R.string.add_item_confirm),
        onEvent = onEvent,
        quantityFocus = quantityFocus,
    )
}

@Composable
private fun EditInput(
    state: AddItemState.Editing,
    onEvent: (AddItemEvent) -> Unit,
    quantityFocus: FocusRequester,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = state.name,
            onValueChange = { onEvent(AddItemEvent.NameChanged(it.take(Item.MAX_NAME_LENGTH))) },
            label = { Text(stringResource(R.string.add_item_edit_name_label)) },
            isError = state.name.isBlank(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { quantityFocus.requestFocus() }),
            modifier = Modifier.weight(1f).testTag(AddItemBarTags.EDIT_NAME),
        )
        CancelButton(stringResource(R.string.add_item_cancel_edit)) { onEvent(AddItemEvent.Cancelled) }
    }
    QuantityRow(
        key = state.itemId,
        quantity = state.quantity,
        unit = state.unit,
        canConfirm = state.canConfirm,
        confirmLabel = stringResource(R.string.add_item_update),
        onEvent = onEvent,
        quantityFocus = quantityFocus,
    )
}

@Composable
private fun CancelButton(contentDescription: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(painterResource(R.drawable.ic_close), contentDescription = contentDescription)
    }
}

/** 数量・単位・確定ボタンの行。追加と編集で共通 */
@Composable
private fun QuantityRow(
    key: Any,
    quantity: String,
    unit: String,
    canConfirm: Boolean,
    confirmLabel: String,
    onEvent: (AddItemEvent) -> Unit,
    quantityFocus: FocusRequester,
) {
    // 数量欄は初期値を全選択しておき、そのまま入力すれば置き換わるようにする
    var quantityValue by remember(key) { mutableStateOf(TextFieldValue(quantity, TextRange(0, quantity.length))) }
    val confirm = { onEvent(AddItemEvent.Confirmed) }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = quantityValue,
            onValueChange = {
                quantityValue = it
                onEvent(AddItemEvent.QuantityChanged(it.text))
            },
            label = { Text(stringResource(R.string.add_item_quantity_label)) },
            isError = !canConfirm,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { confirm() }),
            modifier = Modifier.weight(1f).focusRequester(quantityFocus).testTag(AddItemBarTags.QUANTITY),
        )
        OutlinedTextField(
            value = unit,
            onValueChange = { onEvent(AddItemEvent.UnitChanged(it.take(Item.MAX_UNIT_LENGTH))) },
            label = { Text(stringResource(R.string.add_item_unit_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { confirm() }),
            modifier = Modifier.weight(1f).testTag(AddItemBarTags.UNIT),
        )
        Button(onClick = confirm, enabled = canConfirm) { Text(confirmLabel) }
    }
}
