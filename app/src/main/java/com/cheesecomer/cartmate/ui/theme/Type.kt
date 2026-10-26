package com.cheesecomer.cartmate.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// 値は docs/design-tokens.md。フォントは端末の標準（日本語は Noto Sans CJK JP、英数字は Roboto）
private val Base = Typography()

internal val CartMateTypography = Typography(
    // 画面タイトル（TopAppBar）
    headlineMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    // モーダルの見出し、ステッパーの数値
    headlineSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 24.sp),
    // リストカードのタイトル、商品詳細の品名
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp),
    // 商品行の商品名、FAB・CTA ボタン
    titleMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp),
    // 数量のバッジ（「2本」）、チップ
    titleSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = Base.bodyLarge,
    // 備考、入力欄、案内
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    // メタ情報（「前回 10/18」「約7日ごと」）
    bodySmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    // ボタンのラベル
    labelLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = Base.labelMedium,
    // 進捗のバッジ（「のこり 4件 / 2完了」）
    labelSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp),
)
