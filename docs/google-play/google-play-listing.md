# Google Play の掲載文

Play Console の「ストアの掲載情報」に入れる文章と、スクリーンショットの構成です。宣伝に使う機能は、すべて実装を確かめたもの（[宣伝の内容と実装の根拠](#宣伝の内容と実装の根拠)）だけにしています。機能を変えたら、この表とあわせて直します。

## 基本情報

| 項目 | 内容 |
|---|---|
| アプリ名 | CartMate |
| 言語 | 日本語のみ（アプリも日本語だけ） |
| 対応 | Android 8.0 以上（`minSdk 26`）。スマートフォン向け（タブレット・横向きの専用の画面はない） |
| 料金 | 無料。広告なし・アプリ内課金なし |
| 利用条件 | Google アカウントでのログインが必要。使い始めに家族を作るか、招待で参加する（1人の家族でも使える） |

## 短い説明（80文字以内）

採用は A です。文字数は空白・句読点を含めた数です。

| 案 | 文 | 文字数 |
|---|---|---|
| A（採用） | スーパーも薬局も、お店ごとに買い物リストを整理。家族と共有して、買い忘れを減らそう。 | 42 |
| B | お店ごとの買い物リストを家族で共有。だれが何を買ったか、その場でひと目で分かります。 | 42 |
| C | 買い物リストをお店ごとに分けて家族で共有。いつも買う物は、買う時期が近づくと候補に出ます。 | 45 |

## 詳しい説明（4,000文字以内）

お店ごとのリスト → 家族で共有 → 買い物中の使いやすさ → 買う時期の予測 → 自分だけのリスト、の順に並べています。商品名をまとめる機能は、最後に短く添えるだけにしています。

```text
スーパーも薬局も、行くお店ごとに買い物リストを分けて、家族みんなで使える買い物リストアプリです。

■ お店ごとにリストを分けられる
「スーパー」「薬局」「誕生日の準備」など、好きな名前でリストを作れます。一覧でリストごとの残りの件数が分かるので、今日どのお店に寄ればいいかがすぐに分かります。

■ 家族と共有して、買い物を手分け
家族を招待すると、同じリストを全員で見られます。追加やチェックは、家族のスマホにもすぐに反映。チェックした商品には「だれが・何時に」買ったかが出るので、同じ物を買ってしまうことを防げます。
招待は、リンクを LINE やメールで送るだけです。

■ 買い物中も、さっと使える
商品名を入れて決定すれば、すぐに追加して続けて入力できます。数量や単位、メモも付けられます。
買った物はタップしてチェック。間違えたときは「元に戻す」で戻せます。電波の届きにくい店内でもチェックでき、つながったときに家族へ共有されます。

■ そろそろ買う時期を教えてくれる
同じ商品を3回買うと、買う間隔を覚えて、時期が近づいた商品を候補に出します。いつもより早く同じ物を追加しようとしたときは、前回いつ買ったかを表示します。

■ 自分だけのリストや商品も
家族に見せたくないリストや商品は「自分のみ」にできます。

「牛乳」と「ミルク」のように、呼び方が違う商品をまとめることもできます。

ご利用には Google アカウントでのログインが必要です。家族を招待せずに、1人で使うこともできます。
```

上の本文は約660文字です。

> **公開前の確認**: 「電波の届きにくい店内でもチェックでき、つながったときに家族へ共有されます」は、オフラインのチェックが接続後に確定する結合テスト（`ConcurrencyTest`。Firebase のエミュレータで通信を切って確かめる）を根拠にしています。利用者の期待に直結するので、公開の前に実機で、通信を切ってチェック・追加 → アプリを閉じる → 通信を戻す → もう1台に反映、までを確かめます。確かめられなかった場合は、この一文を外します。

## スクリーンショット（5枚）

画像は `tools/screenshots/take.sh` で撮った `docs/screenshots/` のものを使います（1080×1920。テストデータは `tools/testdata/seed.py --showcase`）。見出しと補足は、画像の上に重ねる文字です。

| 順 | 見出し | 補足 | 画面 | 画像 |
|---|---|---|---|---|
| 1 | お店ごとにリストを分けて | スーパーも薬局も、必要なものだけをすっきり管理 | リスト一覧（4つのリストのカード、件数と進み具合） | `02-lists.png` |
| 2 | 家族と買い物を手分け | だれが買ったか分かるから、重複買いを防げます | リスト詳細（未チェック、自分のチェック、家族のチェックの順） | `04-list-detail.png` |
| 3 | そろそろ買うものを提案 | いつもの買い物のタイミングを覚えて、候補に表示 | 商品の追加のシート（時期が近い商品と、よく買う商品） | `05-suggestions-no-keyboard.png` |
| 4 | 数量も単位も、かんたん入力 | 前回の買い物を参考に、必要な分だけ追加 | 商品の詳細入力（「前回: 1袋…」、数量、単位） | `07-item-entry.png` |
| 5 | 自分だけのリストも作れる | 家族と共有しない買い物も、ひとつのアプリで管理 | プライベートのリスト（鍵の付いた「誕生日の準備」） | `09-list-detail-private.png` |

- 4枚目の画像には、メモの欄が写っていません（画面の下にある）。そのため見出しは「数量もメモも」ではなく「数量も単位も」にしています。
- 家族の画面（`10-family.png`）は、ストアの画像には使いません。使う場合も、メールアドレス（`taro@example.com`）と招待コードはテストデータのものです。ストアの画像に実在の人の名前・メールアドレス・招待コードを写さないよう、撮り直しは必ず `seed.py --showcase` のデータで行います。

## 宣伝の内容と実装の根拠

パスの `A` は `app/src/main/java/com/cheesecomer/cartmate`、`D` は `domain/src/main/kotlin/com/cheesecomer/cartmate/domain` です。

| 宣伝の内容 | 要件 | 実装 | テスト |
|---|---|---|---|
| 好きな名前でお店ごとのリストを作れる | §3.1 | `A/ui/lists/CreateListSheet.kt`、`A/ui/lists/ListsViewModel.kt` | `app/src/test/.../ui/lists/ListsViewModelTest.kt` |
| 残りの件数と進み具合の表示 | §3.1 | `A/ui/lists/ListCard.kt`、`A/ui/lists/ListsViewModel.kt` | `ListsViewModelTest.kt` |
| 長押しで並び替え | §3.1 | `A/ui/lists/ListsScreen.kt`（長押しのドラッグ） | — |
| 家族を招待（リンク・コード）、同じリストを共有 | §3.5 | `A/ui/family/FamilyScreen.kt`（共有シート）、`A/ui/join/`、`app/src/main/AndroidManifest.xml`（App Links） | `domain/src/test/.../family/InviteCodeTest.kt`、`firebase/rules-test/tests/family.test.ts` |
| 追加・チェックがほかの家族にすぐ反映 | §3.3 | `A/data/firestore/FirestoreShoppingStore.kt`（Snapshot Listener） | `app/src/androidTest/.../data/firestore/ConcurrencyTest.kt` |
| だれが何時にチェックしたかを表示 | §3.2 | `A/ui/listdetail/ItemCard.kt` | `ListDetailViewModelTest.kt` |
| 入力して決定するとすぐ追加、続けて入力 | §3.2 | `D/input/AddItemReducer.kt` | `domain/src/test/.../input/AddItemReducerTest.kt` |
| 数量・単位・メモ | §3.2 | `A/ui/itementry/ItemEntryScreen.kt` | `app/src/androidTest/.../ui/itementry/ItemEntryFlowTest.kt` |
| タップでチェック、「元に戻す」 | §3.2 | `A/ui/listdetail/ListDetailScreen.kt`、`A/ui/listdetail/ListDetailViewModel.kt` | `ListDetailViewModelTest.kt` |
| 電波がなくてもチェックでき、つながると共有 | §3.3 | `A/di/FirebaseModule.kt`（端末のキャッシュ）、`FirestoreShoppingStore.kt`（オフラインの書き込み） | `ConcurrencyTest.kt`、`RecentListItemsOfflineTest.kt` |
| 3回買うと、時期が近い商品を候補に出す | §4.3〜4.5 | `D/suggest/CycleEstimator.kt`（3回から）、`D/suggest/SuggestionEngine.kt` | `CycleEstimatorTest.kt`、`SuggestionEngineTest.kt` |
| 前回の数量と単位を使える | §3.2 | `A/ui/listdetail/SheetSuggestions.kt`、`D/input/AddItemReducer.kt` | `AddItemReducerTest.kt` |
| いつもより早い追加で、前回の購入日を表示 | §4.5 | `D/suggest/EarlyPurchaseDetector.kt`、`A/ui/itementry/ItemEntryScreen.kt` | `EarlyPurchaseDetectorTest.kt` |
| 自分のみのリスト・商品 | §3.4 | `A/ui/lists/CreateListSheet.kt`、`A/ui/listdetail/AddItemBar.kt`、`firebase/firestore.rules` | `firebase/rules-test/tests/lists.test.ts` |
| 呼び方の違う商品をまとめる | §4.2 | `A/ui/products/ProductScreens.kt`、`D/product/ProductMerge.kt` | `MergeProductsScreenTest.kt`、`ProductMergeConcurrencyTest.kt` |
| 1人でも使える | §3.5 | `A/data/firestore/FirestoreHouseholdRepository.kt`（家族の作成） | — |
| 広告なし | — | 広告・解析のライブラリを含まない（`app/build.gradle.kts`） | — |

## 宣伝しないこと

実装していない、または条件付きのため、掲載文では触れません。

- **プッシュ通知**: ありません。家族がチェックしたときのお知らせは、アプリでそのリストを開いている間だけ画面の下に出ます。
- **AI による名寄せ**: 計画中です（[ロードマップ](roadmap.md) フェーズ6）。今は、辞書と手動でまとめる操作だけです。
- **「未同期」の表示**: 要件（§3.3）にはありますが、まだ表示していません。オフラインの表示は、リスト一覧の上部だけに出ます。
- **ウィジェット、タブレット向けの画面、英語などほかの言語、ダークモード**: ありません（画面はいつも明るい配色）。
- **チェック済みの表示期間**: チェックした商品は24時間たつとリストから消えます（購入の記録は残る）。掲載文には書きません。利用者が戸惑うおそれは、アプリ内の説明の問題として別に扱います。
