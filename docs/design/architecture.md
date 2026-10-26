# アーキテクチャ

## アプリの識別子

| 項目 | 値 |
|---|---|
| アプリ名 | CartMate |
| パッケージ名 | `com.cheesecomer.cartmate` |
| 招待リンクのドメイン | `cartmate.cheesecomer.com`（[security.md](security.md#招待リンクapp-links)） |

## 技術スタック

| 項目 | 採用 | 理由 |
|---|---|---|
| 言語/UI | Kotlin, Jetpack Compose (Material 3) | 要件で確定 |
| アーキテクチャ | 単一Activity + MVVM（UDF: `StateFlow<UiState>` + イベント） | Compose の標準的な構成 |
| DI | Hilt | ViewModel や Repository の実装を差し替えやすくする（インメモリ実装 → Firestore 実装） |
| 非同期 | Coroutines / Flow | Snapshot Listener を `callbackFlow` で包む |
| 画面遷移 | Navigation Compose（型安全ルート） | |
| 認証 | Firebase Auth + Credential Manager（`GetGoogleIdOption`） | 旧 GoogleSignIn API は非推奨 |
| DB | Cloud Firestore（永続キャッシュ有効） | 要件で確定。Room は使わない（ローカルキャッシュは Firestore に任せる） |
| 保留チェック | DataStore（保存）+ WorkManager（接続時に確定） | オフライン中のチェックを、アプリを終了しても失わずに確定させる（[sync.md](sync.md#保留チェック)） |
| 時刻 | `java.time` + `Clock` を注入 | 予測ロジックのテストを決定的にする |
| ホスティング | Firebase Hosting | 招待リンクの `assetlinks.json` とフォールバックページ |
| minSdk / targetSdk | 26 / 実装時点の最新安定版 | |
| ビルド | Gradle Kotlin DSL + Version Catalog | |

自前のサーバーは持ちません。

## モジュール構成

```
.
├─ app/                      # Android（UI, ViewModel, Firestore/Auth 実装, DI）
│  └─ src/main/java/com/cheesecomer/cartmate/
│     ├─ data/firestore/     # Repository 実装, DTO↔Model 変換, パス定義
│     ├─ data/auth/
│     ├─ data/connectivity/  # オンライン判定
│     ├─ data/pending/       # 保留チェック（DataStore, Syncer, Worker）
│     └─ ui/{signin,onboarding,lists,listdetail,itementry,settings,theme}/
├─ domain/                   # 純Kotlin（JVM）モジュール。Android/Firebase に依存しない
│  ├─ model/                 # ShoppingList, Item, Product, Purchase, Member …
│  ├─ repository/            # Repository インターフェース
│  ├─ text/                  # NameNormalizer
│  ├─ product/               # ProductGraph（統合の解決）
│  ├─ prediction/            # CycleEstimator, EarlyPurchaseDetector
│  ├─ similarity/            # ListSimilarity
│  └─ suggestion/            # SuggestionEngine
├─ firebase/
│  ├─ firebase.json, firestore.rules, firestore.indexes.json
│  ├─ hosting/               # assetlinks.json、招待リンクのフォールバックページ
│  └─ rules-test/            # @firebase/rules-unit-testing（TypeScript）
└─ docs/
```

`domain` を純粋な JVM モジュールにしているのは、周期推定・類似度・正規化を Android 抜きの高速な単体テストで検証するためです。モジュールはこの2つにとどめ、これ以上は分けません。

## 1ユーザー1家族

1人のユーザーが参加できる家族は1つだけです。`users/{uid}.householdId` で所属家族を引きます。複数の家族への参加は、UI とデータモデルが複雑になるため対象外としています。
