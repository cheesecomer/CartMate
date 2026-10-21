package com.cheesecomer.cartmate.ui.theme

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ブランドカラー（グリーン #2E7D4F）から Material Color Utilities（TonalSpot、2021年版の仕様）で生成した配色。
// クリーンな印象にするため、background / surface / surfaceBright だけは白にしている。
// 端末がダークモードでも白を基調にするため、ダーク用の配色は持たない（docs/design/ui.md「見た目の基準」）。

internal val LightColorScheme = lightColorScheme(
    primary = Color(0xFF2D6A45),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB0F1C2),
    onPrimaryContainer = Color(0xFF10512F),
    secondary = Color(0xFF4F6353),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD2E8D4),
    onSecondaryContainer = Color(0xFF384B3D),
    tertiary = Color(0xFF3A646F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBEEAF7),
    onTertiaryContainer = Color(0xFF214C57),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF181D19),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF181D19),
    surfaceVariant = Color(0xFFDCE5DB),
    onSurfaceVariant = Color(0xFF414942),
    outline = Color(0xFF717971),
    outlineVariant = Color(0xFFC0C9BF),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF2C322D),
    inverseOnSurface = Color(0xFFEDF2EB),
    inversePrimary = Color(0xFF95D5A7),
    surfaceDim = Color(0xFFD6DBD4),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F5EE),
    surfaceContainer = Color(0xFFEAEFE8),
    surfaceContainerHigh = Color(0xFFE5EAE2),
    surfaceContainerHighest = Color(0xFFDFE4DD),
)
