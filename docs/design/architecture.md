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
| 画像の読み込み | Coil | メンバーのアバターに Google アカウントの写真を表示する |
| DB | Cloud Firestore（永続キャッシュ有効） | 要件で確定。Room は使わない（ローカルキャッシュは Firestore に任せる） |
| 名寄せ辞書 | アプリに同梱したテキスト（TSV）を、起動後に別スレッドでメモリに読み込む | 照合は完全一致が中心なので、HashMap で十分速い。辞書は読み取り専用で、Room の利点がない。数万件を超えるか、メモリの計測で問題が出たら、作成済みの SQLite を読み取り専用で開く方式に移る（[suggestions.md](suggestions.md#名寄せ辞書)） |
| オフラインの書き込み | Firestore の書き込みキュー（永続キャッシュ） | チェックと Undo もバッチ書き込みにし、オフラインでもアプリを閉じても失わずに、接続時に送る（[sync.md](sync.md#オフラインの書き込み)） |
| 時刻 | `java.time` + `Clock` を注入 | 予測ロジックのテストを決定的にする |
| ホスティング | Firebase Hosting | 招待リンクの `assetlinks.json` とフォールバックページ |
| minSdk / targetSdk | 26 / 実装時点の最新安定版 | |
| ビルド | Gradle Kotlin DSL + Version Catalog | |

常時動く自前のサーバーは持ちません。AI による名寄せの候補の判定だけ、Cloud Functions（サーバーレス）から外部の AI API を呼びます（フェーズ6。[suggestions.md](suggestions.md#ai-による名寄せの候補フェーズ6)、[security.md](security.md#ai-の名寄せの関数)）。

## Firebase の設定

| 項目 | 値 |
|---|---|
| プロジェクトID | `cartmate-cheesecomer` |
| Firestore のロケーション | `asia-northeast1`（東京） |
| 認証のプロバイダ | Google |

`app/google-services.json` はリポジトリに含めません。各自 Firebase コンソールか `firebase apps:sdkconfig ANDROID <App ID> --out app/google-services.json` で取得して置きます。ビルドにはこのファイルが必要です（google-services プラグイン）。Google ログインには、このファイルに含まれる Web クライアントID を使うため、Google ログインを有効にしたあとで取得したものを使います。デバッグ用の鍵で動かすには、その SHA-1 と SHA-256 を Firebase に登録します。

## モジュール構成

```
.
├─ app/                      # Android（UI, ViewModel, Firestore/Auth 実装, DI）
│  └─ src/main/java/com/cheesecomer/cartmate/
│     ├─ data/firestore/     # Repository 実装, DTO↔Model 変換, パス定義
│     ├─ data/auth/
│     ├─ data/connectivity/  # オンライン判定
│     └─ ui/{signin,onboarding,family,lists,listdetail,itementry,theme}/
├─ domain/                   # 純Kotlin（JVM）モジュール。Android/Firebase に依存しない
│  ├─ model/                 # ShoppingList, Item, Product, Purchase, Member …
│  ├─ repository/            # Repository インターフェース
│  ├─ family/                # 招待コード、参加・合流の判定、オーナーの引き継ぎ、最初に表示する画面
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
