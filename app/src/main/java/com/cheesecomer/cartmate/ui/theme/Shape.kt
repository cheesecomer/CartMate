package com.cheesecomer.cartmate.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// 値は docs/design-tokens.md。FAB・主要ボタン・数量のバッジは完全な丸（CircleShape / RoundedCornerShape(50)）
internal val CartMateShapes = Shapes(
    // チップ、小さなバッジ
    small = RoundedCornerShape(8.dp),
    // 入力欄、トレイ
    medium = RoundedCornerShape(12.dp),
    // リストカード、商品カード
    large = RoundedCornerShape(16.dp),
    // ボトムシートの上部、ダイアログ
    extraLarge = RoundedCornerShape(28.dp),
)

/** 余白（docs/design-tokens.md）。8dp を基本単位にする */
object CartMateSpacing {
    val xSmall = 4.dp
    val small = 8.dp
    val medium = 12.dp
    val large = 16.dp
    val xLarge = 24.dp
}
