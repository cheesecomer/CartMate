package com.cheesecomer.cartmate.ui.listdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ListItemDefaults
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.cheesecomer.cartmate.R
import com.cheesecomer.cartmate.ui.components.AccentCard
import com.cheesecomer.cartmate.ui.components.cardSlot
import com.cheesecomer.cartmate.domain.input.formatQuantity
import com.cheesecomer.cartmate.domain.model.Item
import kotlinx.coroutines.launch

/** 行の幅のこの割合を超えてから離すと、スワイプを確定する */
private const val SWIPE_THRESHOLD = 0.4f

/** チェック済みの項目の文字の透明度 */
private const val COMPLETED_CONTENT_ALPHA = 0.38f

/** 未購入の項目。タップか → でチェック、編集ボタンで編集、← か削除ボタンで削除 */
@Composable
fun OpenItemRow(
    item: Item,
    editing: Boolean,
    onEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onCheck: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SwipeableRow(
        enableStartToEnd = true,
        onStartToEnd = onCheck,
        onEndToStart = onDelete,
        endToStartIcon = R.drawable.ic_delete,
        endToStartColors = deleteSwipeColors(),
        modifier = modifier,
    ) {
        AccentCard(
            accent = MaterialTheme.colorScheme.primary,
            onClickLabel = stringResource(R.string.detail_check),
            onClick = onCheck,
        ) {
            ListItem(
                headlineContent = { Text(item.inputName) },
                supportingContent = item.quantityText()?.let { { Text(it) } },
                trailingContent = {
                    Row {
                        // 編集中はボタンを選択状態にして示す。もう一度押すと編集をやめる
                        IconToggleButton(
                            checked = editing,
                            onCheckedChange = { checked -> if (checked) onEdit() else onCancelEdit() },
                            colors = IconButtonDefaults.iconToggleButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                checkedContainerColor = MaterialTheme.colorScheme.primary,
                                checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_edit),
                                contentDescription = stringResource(R.string.detail_edit_item, item.inputName),
                            )
                        }
                        RowIconButton(
                            R.drawable.ic_delete,
                            stringResource(R.string.detail_delete_item, item.inputName),
                            onDelete,
                        )
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
    }
}

/** チェック済みの項目。打ち消し線とグレーアウトで示す。タップか ← かボタンでチェックを外す */
@Composable
fun CompletedItemRow(item: Item, onUncheck: () -> Unit, modifier: Modifier = Modifier) {
    // Material 3 で「使えない状態」を表すときと同じ透明度
    val grayedOut = MaterialTheme.colorScheme.onSurface.copy(alpha = COMPLETED_CONTENT_ALPHA)
    SwipeableRow(
        enableStartToEnd = false,
        onStartToEnd = {},
        onEndToStart = onUncheck,
        endToStartIcon = R.drawable.ic_undo,
        endToStartColors = uncheckSwipeColors(),
        modifier = modifier,
    ) {
        AccentCard(
            accent = MaterialTheme.colorScheme.outlineVariant,
            onClickLabel = stringResource(R.string.detail_uncheck),
            onClick = onUncheck,
        ) {
            ListItem(
                headlineContent = {
                    Text(item.inputName, textDecoration = TextDecoration.LineThrough, color = grayedOut)
                },
                supportingContent = item.quantityText()?.let { { Text(it, color = grayedOut) } },
                trailingContent = {
                    RowIconButton(
                        R.drawable.ic_undo,
                        stringResource(R.string.detail_uncheck_item, item.inputName),
                        onUncheck,
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
    }
}

@Composable
private fun RowIconButton(icon: Int, contentDescription: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            painterResource(icon),
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
        // カードの間を空け、スワイプ中の背景もカードと同じ角丸にする
        modifier = modifier
            .cardSlot(),
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

// → はブランドカラーの緑、← の削除は赤（docs/design/ui.md「スワイプ操作」）
@Composable
private fun checkSwipeColors(): SwipeColors =
    SwipeColors(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)

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
