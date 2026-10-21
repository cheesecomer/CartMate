package com.cheesecomer.cartmate.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val CardShape = RoundedCornerShape(8.dp)
private val CardHorizontalPadding = 16.dp
private val CardGap = 8.dp
private val AccentWidth = 12.dp

/**
 * 一覧の1件分のカードを置く場所。カードの間を空け、角を丸める。
 * スワイプする行では、スワイプ中の背景もカードと同じ形になるよう、SwipeToDismissBox に付ける
 */
fun Modifier.cardSlot(elevation: Dp = 0.dp): Modifier =
    padding(horizontal = CardHorizontalPadding, vertical = CardGap / 2)
        .shadow(elevation, CardShape)
        .clip(CardShape)

/** ドラッグで持ち上げているカードの影 */
val DraggingElevation = 8.dp

/**
 * 左端の帯の色で状態を示すカード（docs/design/ui.md「見た目の基準」）。
 * 帯は、まだ対応が必要なもの（未購入の項目、未購入があるリスト）は primary、済んだものは outlineVariant にする
 */
@Composable
fun AccentCard(
    accent: Color,
    onClickLabel: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    customActions: List<CustomAccessibilityAction> = emptyList(),
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .height(IntrinsicSize.Min)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClickLabel = onClickLabel, onClick = onClick)
            // TalkBack 用の操作（並び替えなど）。clickable と同じ要素に付けないと読み上げの対象にならない
            .semantics { this.customActions = customActions },
    ) {
        Box(Modifier.width(AccentWidth).fillMaxHeight().background(accent))
        Box(Modifier.weight(1f)) { content() }
    }
}

/**
 * カードの中身。1行目に [headline]、2行目に [supporting]、右端に [trailing] を置く。
 * Material 3 の ListItem は中身を独立した読み上げ要素にまとめてしまい、カードに付けた操作（タップ、
 * TalkBack 用のアクション）と別の要素になるため使わない
 */
@Composable
fun CardContent(
    headline: @Composable () -> Unit,
    supporting: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (supporting == null) 56.dp else 72.dp)
            .padding(start = 16.dp, end = if (trailing == null) 16.dp else 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            ProvideTextStyle(MaterialTheme.typography.bodyLarge) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) { headline() }
            }
            if (supporting != null) {
                ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                        supporting()
                    }
                }
            }
        }
        trailing?.invoke()
    }
}
