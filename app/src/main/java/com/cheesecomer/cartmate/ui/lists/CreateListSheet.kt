package com.cheesecomer.cartmate.ui.lists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cheesecomer.cartmate.R
import com.cheesecomer.cartmate.domain.model.ShoppingList
import com.cheesecomer.cartmate.domain.model.Visibility
import com.cheesecomer.cartmate.ui.components.KeyboardSheet
import com.cheesecomer.cartmate.ui.components.SelectableOption
import com.cheesecomer.cartmate.ui.components.SheetPreviewFrame
import com.cheesecomer.cartmate.ui.theme.CartMateSpacing
import com.cheesecomer.cartmate.ui.theme.CartMateTheme

/**
 * リスト作成のシート。リスト名（必須）、公開範囲（初期値は共有）、メモ（任意）。
 * キーボードと一緒にせり上がる（docs/design/ui.md「部品」）。呼び出し側の画面全体を覆う Box の中に置く
 */
@Composable
fun CreateListSheet(
    onCreate: (name: String, memo: String, Visibility) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var memo by rememberSaveable { mutableStateOf("") }
    var visibility by rememberSaveable { mutableStateOf(Visibility.SHARED) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    KeyboardSheet(onDismiss = onDismiss, focusRequester = focusRequester) {
        CreateListForm(
            name = name,
            onNameChange = { name = it.take(ShoppingList.MAX_NAME_LENGTH) },
            visibility = visibility,
            onVisibilityChange = { visibility = it },
            memo = memo,
            onMemoChange = { memo = it.take(ShoppingList.MAX_MEMO_LENGTH) },
            onCreate = {
                focusManager.clearFocus()
                onCreate(name, memo, visibility)
            },
            onCancel = {
                focusManager.clearFocus()
                onDismiss()
            },
            focusRequester = focusRequester,
        )
    }
}

/** リスト作成のシートの中身 */
@Composable
private fun CreateListForm(
    name: String,
    onNameChange: (String) -> Unit,
    visibility: Visibility,
    onVisibilityChange: (Visibility) -> Unit,
    memo: String,
    onMemoChange: (String) -> Unit,
    onCreate: () -> Unit,
    onCancel: () -> Unit,
    /** リスト名の欄にフォーカスを移すためのもの（プレビューでは渡さない） */
    focusRequester: FocusRequester = remember { FocusRequester() },
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = CartMateSpacing.xLarge, end = CartMateSpacing.xLarge, bottom = CartMateSpacing.xLarge),
        verticalArrangement = Arrangement.spacedBy(CartMateSpacing.large),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.lists_create_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
        }
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.lists_name_label)) },
            placeholder = { Text(stringResource(R.string.lists_name_placeholder)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
        )
        Column(verticalArrangement = Arrangement.spacedBy(CartMateSpacing.small)) {
            Text(
                stringResource(R.string.lists_visibility_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(CartMateSpacing.small)) {
                SelectableOption(
                    title = stringResource(R.string.lists_visibility_shared),
                    selected = visibility == Visibility.SHARED,
                    onSelect = { onVisibilityChange(Visibility.SHARED) },
                    icon = R.drawable.ic_group,
                    modifier = Modifier.weight(1f),
                )
                SelectableOption(
                    title = stringResource(R.string.lists_visibility_private),
                    selected = visibility == Visibility.PRIVATE,
                    onSelect = { onVisibilityChange(Visibility.PRIVATE) },
                    icon = R.drawable.ic_lock,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        OutlinedTextField(
            value = memo,
            onValueChange = onMemoChange,
            label = { Text(stringResource(R.string.lists_memo_label)) },
            placeholder = { Text(stringResource(R.string.lists_memo_placeholder)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = onCreate,
            enabled = name.isNotBlank(),
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Text(stringResource(R.string.lists_create_confirm), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Preview
@Composable
private fun CreateListSheetPreview() {
    CartMateTheme {
        SheetPreviewFrame(background = { ListsContentPreviewBackground() }) {
            CreateListForm(
                name = "ドラッグストア",
                onNameChange = {},
                visibility = Visibility.SHARED,
                onVisibilityChange = {},
                memo = "",
                onMemoChange = {},
                onCreate = {},
                onCancel = {},
            )
        }
    }
}
