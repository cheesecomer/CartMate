package com.cheesecomer.cartmate.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cheesecomer.cartmate.ui.theme.CartMateSpacing

/** ドラッグで持ち上げているカードの影 */
val DraggingElevation = 8.dp

/**
 * カードの形と枠。影は付けず、outlineVariant の 1dp の線で区切る（docs/design/ui.md「形と余白」）。
 * スワイプする行では、スワイプ中の背景もカードと同じ形になるよう、SwipeToDismissBox に付ける
 */
@Composable
fun Modifier.cardFrame(elevation: Dp = 0.dp): Modifier {
    val shape = MaterialTheme.shapes.large
    return shadow(elevation, shape)
        .clip(shape)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
}

/** 縦に並べるカードの外側の余白（画面の左右 16dp、カードの間 12dp） */
fun Modifier.cardSlot(): Modifier =
    padding(horizontal = CartMateSpacing.large, vertical = CartMateSpacing.medium / 2)
