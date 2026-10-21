# CartMate

家族で共有する買い物リストの Android アプリです。買い物先ごとにリストを分けて、必要なものだけを家族全員でリアルタイムに確認できます。購入履歴から「そろそろ買う時期」の商品を提案します。

> **開発状況**: 設計段階です（MVP の実装前）。

## 主な機能（MVP）

- 「スーパー」「薬局」など、好きな名前の買い物リストを家族で共有
- 商品の追加やチェックが、家族の端末にすぐ反映
- 電波の弱い店内でもチェックでき、接続が戻ると自動で同期
- 購入履歴から買い時の近い商品を提案（同じ商品を3回買ったあとから）
- いつもより早く同じ商品を追加すると、前回の購入日を控えめに表示
- 家族が同じ商品をチェックすると通知され、二重購入に気づける
- Google アカウントでログインし、招待リンクで家族に参加

## 技術スタック

- Kotlin / Jetpack Compose / Hilt
- Firebase Authentication（Google ログイン）
- Cloud Firestore（リアルタイム同期・オフラインキャッシュ）
- Firebase Hosting（招待リンク）
- 自前のサーバーは持ちません

## リポジトリ構成（予定）

```
app/        Android アプリ（UI・Firebase 連携）
domain/     Android に依存しないロジック（購入周期の推定・サジェストなど）
firebase/   Firestore Security Rules・インデックス・Hosting
docs/       仕様と設計資料
```

## ドキュメント

- [MVP 要件](docs/requirements.md) — アプリがどう振る舞うか
- [設計資料](docs/README.md) — データモデル、セキュリティ、同期、画面、サジェストの設計
- [ロードマップ](docs/roadmap.md) — 実装フェーズ

## 開発

### 必要なもの

- JDK 17 以上
- Android SDK（compileSdk 37）

### ビルドとテスト

```sh
./gradlew assembleDebug   # デバッグ APK をビルド
./gradlew :domain:test    # domain モジュールの単体テスト
```

Android Studio で開く場合は、リポジトリのルートを開いてください。
