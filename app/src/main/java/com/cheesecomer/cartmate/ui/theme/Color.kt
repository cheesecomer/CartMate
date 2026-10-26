package com.cheesecomer.cartmate.ui.theme

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// 値は docs/design-tokens.md。トークンにない役割（secondary、inverse など）は、ブランドカラー（#6B4E8C）から
// Material Color Utilities で生成した値（docs/design/DESIGN.md の定義）を使う。
// 緑を使わないため、tertiary も secondary と同じライラックにする（生成した値は緑になる）。
// 端末がダークモードでも明るい配色のままにするため、ダーク用の配色は持たない（docs/design/ui.md「見た目の基準」）。

internal val LightColorScheme = lightColorScheme(
    primary = Color(0xFF6B4E8C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF2E7FE),
    onPrimaryContainer = Color(0xFF260E43),
    secondary = Color(0xFF6E528D),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF563A74),
    tertiary = Color(0xFF6E528D),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE8DEF8),
    onTertiaryContainer = Color(0xFF563A74),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFFFF7FF),
    onBackground = Color(0xFF1D1B20),
    surface = Color(0xFFFFF7FF),
    onSurface = Color(0xFF1D1B20),
    surfaceVariant = Color(0xFFE7E0EB),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF7A757F),
    outlineVariant = Color(0xFFCAC4CF),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF342E3A),
    inverseOnSurface = Color(0xFFF9EDFE),
    inversePrimary = Color(0xFFDBB8FF),
    surfaceTint = Color(0xFF6B4E8C),
    surfaceDim = Color(0xFFE1D6E7),
    surfaceBright = Color(0xFFFFF7FF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBF0FF),
    surfaceContainer = Color(0xFFF5EBF9),
    surfaceContainerHigh = Color(0xFFE1D6E7),
    surfaceContainerHighest = Color(0xFFEADFEF),
)

/** Material 3 の役割にない色（docs/design-tokens.md） */
object CartMateColors {
    /** オフラインバナーの背景 */
    val WarningBannerBackground = Color(0xFFFCEEE8)

    /** オフラインバナーの文字とアイコン */
    val WarningBannerText = Color(0xFF6E3917)

    /** 完了した項目の文字と打ち消し線（onSurface の 40%） */
    val TextDisabled = Color(0x661D1B20)
}
