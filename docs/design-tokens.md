# CartMate デザイントークン仕様書 (`docs/design-tokens.md`)

本作書は、Android ネイティブ買い物リスト共有アプリ「**CartMate**」における Material Design 3 デザイントークン定義書です。
Jetpack Compose (`Color.kt`, `Type.kt`, `Shape.kt`, `Theme.kt`) への直接マッピングを目的としています。

---

## 1. カラートークン（Color Tokens: Lilac Material Grocery）

ライラック・パープル系統一パレット。完了・FAB・進捗を含め、緑を完全に排除した統一配色。

```kotlin
package com.cartmate.app.ui.theme

import androidx.compose.ui.graphics.Color

// Brand Primary Tokens
val Primary = Color(0xFF6B4E8C)               // 主要CTA、確定ボタン、進捗バー、チェックマーク、FAB
val OnPrimary = Color(0xFFFFFFFF)             // Primary要素上のテキスト・アイコン
val PrimaryContainer = Color(0xFFF2E7FE)      // 数量ピル、完了バッジ、強調背景
val OnPrimaryContainer = Color(0xFF260E43)    // PrimaryContainer上のテキスト・アイコン
val BadgeVariant = Color(0xFFE8DEF8)          // 補助バッジ、補助チップ背景

// Surface & Background Tokens (Android 12+ SplashScreen 完全一致)
val Surface = Color(0xFFFFF7FF)               // アプリ背景、アプリアイコン背景、スプラッシュ背景
val OnSurface = Color(0xFF1D1B20)             // 主要テキスト、見出し、商品名
val SurfaceContainerLowest = Color(0xFFFFFFFF)// 最前面カード背景（リストカード、未完了商品カード）
val SurfaceContainerLow = Color(0xFFFBF0FF)   // 階層の低いコンテナ、入力トレイ、完了商品カード背景
val SurfaceContainer = Color(0xFFF5EBF9)      // プログレスバートラック、区切り面
val SurfaceContainerHigh = Color(0xFFE1D6E7) // ボトムシート背景、ダイアログ背景
val SurfaceVariant = Color(0xFFE7E0EB)        // 薄いボーダー、無効化要素の面

// Outline Tokens
val Outline = Color(0xFF7A757F)               // テキスト入力枠線（未フォーカス時）
val OutlineVariant = Color(0xFFCAC4CF)        // カード境界線（1dp）

// Content & Secondary Tokens
val OnSurfaceVariant = Color(0xFF49454F)      // 備考、メタ情報、補足テキスト
val TextDisabled = Color(0x661D1B20)          // 完了商品の打消し線・減光テキスト（40%不透明度）

// Status & Semantic Tokens
val WarningBannerBackground = Color(0xFFFCEEE8) // オフライン警告バナー背景
val WarningBannerText = Color(0xFF6E3917)       // オフライン警告テキスト・アイコン
```

---

## 2. タイポグラフィトークン（Typography Tokens: Type Scale）

```kotlin
package com.cartmate.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val CartMateTypography = Typography(
    // 画面タイトル (TopAppBar)
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    // モーダル見出し、ステッパー数値
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 24.sp
    ),
    // リストカードタイトル、商品詳細品名
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    // 商品行商品名、FAB・CTAボタン
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    // 数量ピル（「2本」「1パック」）、チップ
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    // 備考テキスト、入力欄、案内テキスト
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    // メタ情報（「前回 10/18」「約7日ごと」）
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    // ボタンラベル
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    // 進捗バッジ（「のこり 4件 / 2完了」）
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp
    )
)
```

---

## 3. 余白・グリッドトークン（Spacing Tokens）

```kotlin
package com.cartmate.app.ui.theme

import androidx.compose.ui.unit.dp

object CartMateSpacing {
    val xSmall = 4.dp     // バッジ内パディング、アイコン-テキスト間
    val small = 8.dp      // チップ間隔、行内要素間隔
    val medium = 12.dp    // フォーム行垂直間隔、コンパクトパディング
    val large = 16.dp     // 画面マージン、カード内パディング、リスト項目間隔
    val xLarge = 24.dp    // セクション間隔、FABマージン
    val minTouchTarget = 48.dp // 最小タップ保証領域
}
```

---

## 4. 角丸トークン（Shape Tokens）

```kotlin
package com.cartmate.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val CartMateShapes = Shapes(
    small = RoundedCornerShape(8.dp),      // チップ、小バッジ
    medium = RoundedCornerShape(12.dp),    // 入力フィールド、トレイ
    large = RoundedCornerShape(16.dp),     // リストカード、商品カード
    extraLarge = RoundedCornerShape(28.dp) // ボトムシート上部角丸、ダイアログ
    // Pill型 / 完全丸 (999dp): FAB、ステッパーボタン、確定CTA
)
```
