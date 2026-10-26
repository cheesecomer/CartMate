package com.cheesecomer.cartmate.ui.listdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cheesecomer.cartmate.R
import com.cheesecomer.cartmate.domain.input.formatQuantity
import com.cheesecomer.cartmate.domain.model.Item
import com.cheesecomer.cartmate.domain.model.Visibility
import com.cheesecomer.cartmate.ui.components.DraggingElevation
import com.cheesecomer.cartmate.ui.components.cardFrame
import com.cheesecomer.cartmate.ui.components.cardSlot
import com.cheesecomer.cartmate.ui.theme.CartMateColors
import com.cheesecomer.cartmate.ui.theme.CartMateSpacing
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 行の幅のこの割合を超えてから離すと、スワイプを確定する */
private const val SWIPE_THRESHOLD = 0.4f

private val CompletedTimeFormatter = DateTimeFormatter.ofPattern("H:mm")

/**
 * 並び替えのための行の設定。[handle] はロングタップでドラッグを始める部分に付ける Modifier、
 * [actions] は TalkBack 用の「上へ移動」「下へ移動」
 */
data class RowDrag(
    val dragging: Boolean = false,
    val handle: Modifier = Modifier,
    val actions: List<CustomAccessibilityAction> = emptyList(),
)

/**
 * 未購入の項目のカード。タップか → でチェック、「編集」ボタンで編集、← か「削除」ボタンで削除。
 * [showLock] は共有のリストにあるプライベートな項目に鍵アイコンを付けるか
 */
@Composable
fun OpenItemCard(
    item: Item,
    showLock: Boolean,
    onEdit: () -> Unit,
    onCheck: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    drag: RowDrag = RowDrag(),
) {
    SwipeableCard(
        enableStartToEnd = true,
        onStartToEnd = onCheck,
        onEndToStart = onDelete,
        endToStartIcon = R.drawable.ic_delete,
        endToStartColors = deleteSwipeColors(),
        modifier = modifier,
        dragging = drag.dragging,
    ) {
        CardBody(
            background = MaterialTheme.colorScheme.surfaceContainerLowest,
            onClickLabel = stringResource(R.string.detail_check),
            onClick = onCheck,
            drag = drag,
            headline = {
                Text(item.inputName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false))
                item.quantityText()?.let { QuantityBadge(it, completed = false) }
                if (showLock) LockIcon()
            },
            supporting = item.memo?.let {
                {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            trailing = {
                CardIconButton(R.drawable.ic_edit, stringResource(R.string.detail_edit_item, item.inputName), onEdit)
                CardIconButton(R.drawable.ic_delete, stringResource(R.string.detail_delete_item, item.inputName), onDelete)
            },
        )
    }
}

/**
 * チェック済みの項目のカード。背景を薄い色にし、商品名を打ち消し線と薄い文字で示す。
 * タップか ← か「元に戻す」でチェックを外す。2行目に、チェックした人と時刻（「✓ 花子 10:02」）を表示する
 */
@Composable
fun CompletedItemCard(
    item: Item,
    /** チェックした人の名前（家族にいない人は「退会したメンバー」） */
    checkerName: String,
    showLock: Boolean,
    onUncheck: () -> Unit,
    modifier: Modifier = Modifier,
    drag: RowDrag = RowDrag(),
) {
    SwipeableCard(
        enableStartToEnd = false,
        onStartToEnd = {},
        onEndToStart = onUncheck,
        endToStartIcon = R.drawable.ic_undo,
        endToStartColors = uncheckSwipeColors(),
        modifier = modifier,
        dragging = drag.dragging,
    ) {
        CardBody(
            background = MaterialTheme.colorScheme.surfaceContainerLow,
            onClickLabel = stringResource(R.string.detail_uncheck),
            onClick = onUncheck,
            drag = drag,
            headline = {
                Text(
                    item.inputName,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = TextDecoration.LineThrough,
                    color = CartMateColors.TextDisabled,
                    modifier = Modifier.weight(1f, fill = false),
                )
                item.quantityText()?.let { QuantityBadge(it, completed = true) }
                if (showLock) LockIcon()
            },
            supporting = item.completedAt?.let { completedAt ->
                {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painterResource(R.drawable.ic_check_circle),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            "$checkerName ${completedAt.formatTime()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = CartMateSpacing.small),
                        )
                    }
                }
            },
            trailing = {
                val uncheckLabel = stringResource(R.string.detail_uncheck_item, item.inputName)
                OutlinedButton(
                    onClick = onUncheck,
                    shape = CircleShape,
                    // 読み上げには商品名を含める
                    modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = uncheckLabel },
                ) {
                    Text(stringResource(R.string.action_undo), style = MaterialTheme.typography.labelLarge)
                }
            },
        )
    }
}

/**
 * カードの中身。1行目に [headline]、2行目に [supporting]、右端に [trailing] を置く。
 * Material 3 の ListItem は中身を独立した読み上げ要素にまとめてしまい、カードに付けた操作（タップ、
 * TalkBack 用のアクション）と別の要素になるため使わない
 */
@Composable
private fun CardBody(
    background: Color,
    onClickLabel: String,
    onClick: () -> Unit,
    drag: RowDrag,
    headline: @Composable RowScope.() -> Unit,
    supporting: (@Composable () -> Unit)?,
    trailing: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(drag.handle)
            .background(background)
            .clickable(onClickLabel = onClickLabel, onClick = onClick)
            // TalkBack 用の操作（並び替え）。clickable と同じ要素に付けないと読み上げの対象にならない
            .semantics { customActions = drag.actions }
            .heightIn(min = 72.dp)
            .padding(start = CartMateSpacing.large, end = CartMateSpacing.small, top = CartMateSpacing.medium, bottom = CartMateSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CartMateSpacing.xSmall)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CartMateSpacing.small),
                content = headline,
            )
            supporting?.invoke()
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
    }
}

/** 数量・単位のバッジ（例: 「2本」） */
@Composable
private fun QuantityBadge(text: String, completed: Boolean) {
    val colors = MaterialTheme.colorScheme
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = if (completed) colors.onSurfaceVariant else colors.primary,
        maxLines = 1,
        modifier = Modifier
            .background(if (completed) colors.surfaceVariant else colors.primaryContainer, CircleShape)
            .padding(horizontal = CartMateSpacing.small + 2.dp, vertical = 2.dp),
    )
}

@Composable
private fun LockIcon() {
    Icon(
        painterResource(R.drawable.ic_lock),
        contentDescription = stringResource(R.string.private_label),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(18.dp),
    )
}

@Composable
private fun CardIconButton(icon: Int, contentDescription: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(painterResource(icon), contentDescription = contentDescription, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private data class SwipeColors(val container: Color, val content: Color)

@Composable
private fun SwipeableCard(
    enableStartToEnd: Boolean,
    onStartToEnd: () -> Unit,
    onEndToStart: () -> Unit,
    endToStartIcon: Int,
    endToStartColors: SwipeColors,
    modifier: Modifier = Modifier,
    dragging: Boolean = false,
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
        // スワイプ中の背景もカードと同じ角丸にする
        modifier = modifier.cardSlot().cardFrame(elevation = if (dragging) DraggingElevation else 0.dp),
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
        modifier = Modifier.fillMaxSize().background(colors.container).padding(horizontal = CartMateSpacing.xLarge),
        horizontalArrangement = if (alignStart) Arrangement.Start else Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = colors.content)
    }
}

// → のチェックとチェック済みの ← はブランドカラー、未購入の ← の削除は赤。緑は使わない（docs/design/ui.md「スワイプ操作」）
@Composable
private fun checkSwipeColors(): SwipeColors =
    SwipeColors(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)

@Composable
private fun deleteSwipeColors(): SwipeColors =
    SwipeColors(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)

@Composable
private fun uncheckSwipeColors(): SwipeColors =
    SwipeColors(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)

internal fun Instant.formatTime(): String = CompletedTimeFormatter.format(atZone(ZoneId.systemDefault()))

/** 例: 2本、1.5kg、3（単位なし）、null（数量なし） */
private fun Item.quantityText(): String? {
    val quantityText = quantity?.let(::formatQuantity)
    return when {
        quantityText != null -> quantityText + unit.orEmpty()
        unit != null -> unit
        else -> null
    }
}

/** 共有のリストにあるプライベートな項目だけ、鍵アイコンを付ける */
fun Item.showsLock(listVisibility: Visibility): Boolean =
    listVisibility == Visibility.SHARED && visibility == Visibility.PRIVATE
