package com.cheesecomer.cartmate.ui.listdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.cheesecomer.cartmate.R
import com.cheesecomer.cartmate.domain.input.formatQuantity
import com.cheesecomer.cartmate.domain.model.Item
import kotlinx.coroutines.launch

/** 行の幅のこの割合を超えてから離すと、スワイプを確定する */
private const val SWIPE_THRESHOLD = 0.4f

/** 未購入の項目。タップで編集、→ でチェック、← で削除 */
@Composable
fun OpenItemRow(
    item: Item,
    editing: Boolean,
    onEdit: () -> Unit,
    onCheck: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val editLabel = stringResource(R.string.detail_edit)
    val checkLabel = stringResource(R.string.detail_check)
    val deleteLabel = stringResource(R.string.action_delete)
    SwipeableRow(
        enableStartToEnd = true,
        onStartToEnd = onCheck,
        onEndToStart = onDelete,
        endToStartIcon = R.drawable.ic_delete,
        endToStartColors = deleteSwipeColors(),
        modifier = modifier,
    ) {
        ListItem(
            // ListItem は商品名と数量を1つの要素にまとめる。TalkBack 用のカスタムアクションはその要素に付ける
            modifier = Modifier
                .clickable(onClickLabel = editLabel, onClick = onEdit)
                .semantics {
                    customActions = listOf(
                        CustomAccessibilityAction(checkLabel) { onCheck(); true },
                        CustomAccessibilityAction(deleteLabel) { onDelete(); true },
                    )
                },
            headlineContent = { Text(item.inputName) },
            trailingContent = item.quantityText()?.let { { Text(it) } },
            // 編集中の項目を示す
            colors = if (editing) {
                ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            } else {
                ListItemDefaults.colors()
            },
        )
    }
}

/** チェック済みの項目。打ち消し線で示す。← でチェックを外す */
@Composable
fun CompletedItemRow(item: Item, onUncheck: () -> Unit, modifier: Modifier = Modifier) {
    val uncheckLabel = stringResource(R.string.detail_uncheck)
    SwipeableRow(
        enableStartToEnd = false,
        onStartToEnd = {},
        onEndToStart = onUncheck,
        endToStartIcon = R.drawable.ic_undo,
        endToStartColors = uncheckSwipeColors(),
        modifier = modifier,
    ) {
        ListItem(
            modifier = Modifier.semantics(mergeDescendants = true) {
                customActions = listOf(CustomAccessibilityAction(uncheckLabel) { onUncheck(); true })
            },
            headlineContent = {
                Text(
                    item.inputName,
                    textDecoration = TextDecoration.LineThrough,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingContent = item.quantityText()?.let {
                { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            },
        )
    }
}

private data class SwipeColors(val container: Color, val content: Color)

@Composable
private fun SwipeableRow(
    enableStartToEnd: Boolean,
    onStartToEnd: () -> Unit,
    onEndToStart: () -> Unit,
    endToStartIcon: Int,
    endToStartColors: SwipeColors,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    // rememberSwipeToDismissBoxState は状態を保存するため、LazyColumn が行の消えたあとも「確定済み」を
    // 保持し、同じ項目が元に戻ったときに onDismiss がもう一度呼ばれてしまう。保存しない state を使う
    val state = remember { SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled, { it * SWIPE_THRESHOLD }) }
    val scope = rememberCoroutineScope()
    val checkColors = checkSwipeColors()
    val currentOnStartToEnd by rememberUpdatedState(onStartToEnd)
    val currentOnEndToStart by rememberUpdatedState(onEndToStart)

    // SwipeToDismissBox は onDismiss が変わるたびに呼び直すため、同じ関数を渡し続ける
    val onDismiss: (SwipeToDismissBoxValue) -> Unit = remember(state, scope) {
        { direction ->
            when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> currentOnStartToEnd()
                SwipeToDismissBoxValue.EndToStart -> currentOnEndToStart()
                SwipeToDismissBoxValue.Settled -> Unit
            }
            // 成功すれば行ごと消える。失敗した場合に元の位置へ戻す
            scope.launch { state.reset() }
        }
    }

    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = enableStartToEnd,
        enableDismissFromEndToStart = true,
        onDismiss = onDismiss,
        backgroundContent = {
            when (state.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> SwipeBackground(state, R.drawable.ic_check, checkColors)
                SwipeToDismissBoxValue.EndToStart -> SwipeBackground(state, endToStartIcon, endToStartColors)
                SwipeToDismissBoxValue.Settled -> Unit
            }
        },
        content = content,
    )
}

@Composable
private fun RowScope.SwipeBackground(state: SwipeToDismissBoxState, icon: Int, colors: SwipeColors) {
    val alignStart = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
    Row(
        modifier = Modifier.fillMaxSize().background(colors.container).padding(horizontal = 24.dp),
        horizontalArrangement = if (alignStart) Arrangement.Start else Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = colors.content)
    }
}

// → は緑、← の削除は赤（docs/design/ui.md「スワイプ操作」）
@Composable
private fun checkSwipeColors(): SwipeColors =
    if (isSystemInDarkTheme()) {
        SwipeColors(Color(0xFF1B5E20), Color(0xFFC8E6C9))
    } else {
        SwipeColors(Color(0xFFC8E6C9), Color(0xFF1B5E20))
    }

@Composable
private fun deleteSwipeColors(): SwipeColors =
    SwipeColors(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)

@Composable
private fun uncheckSwipeColors(): SwipeColors =
    SwipeColors(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)

/** 例: 2本、1.5kg、3（単位なし）、null（数量なし） */
private fun Item.quantityText(): String? {
    val quantityText = quantity?.let(::formatQuantity)
    return when {
        quantityText != null -> quantityText + unit.orEmpty()
        unit != null -> unit
        else -> null
    }
}
