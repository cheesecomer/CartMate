package com.cheesecomer.cartmate.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * 端末の設定に関係なく、白を基調にしたブランドカラーの配色を使う。
 * ダークモードにも、端末の壁紙に合わせた配色（Dynamic Color）にも合わせない（docs/design/ui.md「見た目の基準」）。
 */
@Composable
fun CartMateTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColorScheme, content = content)
}
