package com.cheesecomer.cartmate.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val CardShape = RoundedCornerShape(8.dp)
private val CardHorizontalPadding = 16.dp
private val CardGap = 8.dp
private val AccentWidth = 12.dp

/**
 * 一覧の1件分のカードを置く場所。カードの間を空け、角を丸める。
 * スワイプする行では、スワイプ中の背景もカードと同じ形になるよう、SwipeToDismissBox に付ける
 */
fun Modifier.cardSlot(): Modifier =
    padding(horizontal = CardHorizontalPadding, vertical = CardGap / 2).clip(CardShape)

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
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .height(IntrinsicSize.Min)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClickLabel = onClickLabel, onClick = onClick),
    ) {
        Box(Modifier.width(AccentWidth).fillMaxHeight().background(accent))
        Box(Modifier.weight(1f)) { content() }
    }
}
