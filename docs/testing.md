# テスト戦略

| 層 | ツール | 主な対象 |
|---|---|---|
| domain の単体テスト | JUnit5 + Truth（JVM） | 正規化、ProductGraph、周期推定、類似度、サジェスト、異常検知 |
| 入力状態の reducer | JUnit5（JVM） | [ui.md](design/ui.md#商品追加欄とサジェストのシート) の全遷移、商品詳細入力の検証と初期値 |
| ViewModel | coroutines-test + Turbine + フェイク Repository | チェック・Undo の結果に応じた UI、オフライン時の挙動 |
| Security Rules | Firebase Emulator + `@firebase/rules-unit-testing`（TypeScript / Vitest） | 下記「Security Rules」 |
| Repository の結合テスト | Android Instrumented Test + Firestore Emulator | 2クライアントでの同時実行（下記「同時実行」） |
| Compose UI | `compose-ui-test` | IME フロー、サジェストのシート、商品詳細入力画面、公開範囲の引き継ぎ、スワイプ |
| 受け入れテスト | 実機2台での手動チェックリスト | [要件](requirements.md) §10 の全項目 |

## domain の主なテストケース

- 正規化: 全角と半角、半角カナ、前後と連続の空白、英字の大小文字が同一になる。「牛乳」と「牛乳 1L」は同一にならない。
- 周期: 購入日2日 → `null`／購入日3日 → 推定あり／同じ日の重複はまとめる／間隔が10個を超えたら直近10個だけ使う／偶数個の中央値／外れ値が1つあっても中央値は動かない。
- 経過率の境界: 0.79 は出さない、0.80 は出す、3.0 は出す、3.01 は出さない。
- 未購入にある商品（統合元の名前で登録されていても、統合先で一致）は出さない。
- 類似度: 件数不足なら `null`／重複係数の境界値／新しいリストで同じ名前の履歴がなければ空。
- 異常検知: 0.49 は通知、0.50 は通知しない、不安定なら通知しない、購入日2日なら通知しない。
- ProductGraph: 多段の統合、循環しても止まる、統合を解除すると履歴が分かれる。
- 公開範囲の引き継ぎ: 初回は共有／詳細入力で「自分のみ」を選んで追加すると次の初期値がプライベート／Enter と「＋」は初期値を変えない／リストごとに独立／プライベートのリストでは常にプライベート。
- 商品詳細入力: 新規の初期値（候補からなら前回の数量・単位、それ以外は 1・「個」）／数量が空・0以下・数値でないと確定できない／単位と備考は空でもよい。
- 一覧カードの状態: のこりあり・登録なし・買い終わったの判定と進捗の割合。自分に見えない項目は数えない。
- 備考の候補: 新しい順・重複なし・最大3件・空は除く。

## Security Rules

- 家族外のユーザーは、家族の領域のどのパスも読み書きできない。
- 招待: 期限切れ・使用済み・別の家族のコードでは参加できない／`list` でコードを列挙できない／usedCount を +1 しない参加は拒否される／参加済みのユーザーは二重に消費できない。
- チェック: 購入履歴を同時に書かないチェックは拒否される／`purchasedAt` がサーバー時刻でなければ拒否される／`checkedAt` が5分以上先、または7日より前なら拒否される／スナップショット値（productId, listName など）が一致しなければ拒否される。
- Undo: 購入履歴を無効化しない Undo は拒否される。購入履歴は削除できない。
- 編集: 未購入の項目の商品名・数量・単位・備考は変更できる／チェック済みの項目は変更できない／それ以外のフィールド（`visibility` を含む）は変更できない。
- 公開範囲: 他のメンバーのプライベートなリスト・項目・購入履歴は読めない・更新できない・削除できない／共有とプライベートを分けたクエリは通り、条件のないクエリは拒否される／プライベートのリストには本人のプライベートな項目だけを追加できる／購入履歴の `visibility` と `memo` が項目と一致しなければ拒否される。
- チェック済みの項目は削除できない。リストは物理削除できない。論理削除できるのは active → deleted だけ。
- リスト名・備考: active なリストの名前（40文字まで、空は不可）と備考（60文字まで）だけを変更できる。削除済みのリストは変更できない。`visibility` は変更できない。
- 並び替え: リスト・項目とも `sortKey` だけを変更できる（数値のみ）。
- product: 同じIDの再作成は可、正規化名の変更は不可、統合先が統合済みなら拒否、自分自身への統合は拒否。

## 同時実行

2つの FirebaseApp インスタンスと、Firebase のエミュレータ（Auth と Firestore）で実行します（`ConcurrencyTest`。実行方法は下記「エミュレータを使う結合テスト」）。

1. 同じ項目を同時にチェック → 有効な購入履歴がちょうど1件。両方とも例外にならない。後からチェックした側には、先にチェックした人の uid と時刻が返る。
2. チェック → もう一方が Undo → 最初の端末も Undo → `AlreadyOpen`。購入履歴は `active=false`。
3. Undo と再チェックを同時に実行 → 項目の状態と購入履歴の active が一致している。
4. リスト削除と項目追加を同時に実行 → 一覧に表示されたまま。削除と追加のどちらが先にサーバーに届いても、追加した項目は消えない。
5. 使用回数1の招待コードで2人が同時に参加 → 1人だけ成功する。
6. オフラインで項目を追加 → 再接続 → もう一方の端末に反映される。
7. オフラインでチェック → アプリのプロセスを終了 → 再接続 → Firestore の書き込みキュー（端末に残る）によって確定し、購入履歴の `checkedAt` がオフライン中の時刻になっている。
8. オフラインでチェック → その間にもう一方がチェック → 再接続 → 有効な購入履歴は1件。オフラインだった側に `AlreadyCompleted` が返る。
9. オフラインでチェック → オフラインのまま Undo → 再接続 → 項目は未購入で、有効な購入履歴はない（チェックと Undo の2つの書き込みが順に送られ、購入履歴は `active=false` で残る。[sync.md](design/sync.md#undo)）。
10. チェック済みの項目をオフラインで Undo → 再接続 → 購入履歴が `active=false` になる。

## Compose UI

- 商品追加欄にフォーカスするまで、サジェストのシートは表示されない。シートを閉じても入力途中の商品名は残る。
- 商品追加欄をタップしたとき、「＋ リスト作成」を押したときのどちらも、シートはキーボードと一緒にせり上がり、キーボードより先に出ない（シートが見えた時点でキーボードがすでに出ている）。（下記「キーボードのタイミングに依存するテスト」）
- シートで空欄なら時期の近い商品、文字を入力すると補完と「数量を設定して追加」に切り替わる。
- 名前を入力 → Enter で、数量1・単位「個」で登録され、シートが開いたまま入力欄が空になり、フォーカスを保つ。続けて次の名前を入力できる。
- 候補の「＋」で前回の数量・単位で登録される。候補の名前のタップでは項目は増えず、商品詳細入力画面に進む。
- 商品詳細入力画面で数量・単位・公開範囲・備考を指定して「リストに追加する」と、その内容で末尾に追加されてリスト詳細に戻る。✕ でシートに戻り、入力した名前が残っている。
- 共有のリストで「自分のみ」を選んで追加したあとの Enter による追加はプライベートになり、鍵アイコンが付く。プライベートのリストでは公開範囲の選択が表示されない。
- 早すぎる再購入の注意が出ても追加できる。
- `AlreadyCompleted` になったとき、チェックした人と時刻が表示される。
- 他のメンバーのチェックが3秒以内に複数あったとき、通知が1件にまとまる。画面を開いた時点ですでにチェック済みだった項目は通知されない。
- 未チェックの項目を → でチェック、← で削除できる。削除後の「元に戻す」で項目が復活し、そのまま残る（もう一度削除されない）。チェック後の「元に戻す」も同様。チェック済みの項目は ← でチェックを外せる。
- 「編集」ボタンで商品詳細入力画面が編集モードで開き、「更新する」で内容が変わる。公開範囲は変更できない。
- 項目のタップでチェックを切り替えられる（未購入はチェック、チェック済みはチェックを外す）。タップでは編集は始まらない。
- チェック済みの項目の「元に戻す」でチェックが外れる。
- TalkBack のカスタムアクションで、チェック・削除・チェックを外すこと、リストと項目の並べ替えができる。
- 招待リンクの URL でアプリを起動すると参加確認の画面が表示され、承認するまで参加しない。

## CI

GitHub Actions で次を実行します。

- `./gradlew :domain:test :app:testDebugUnitTest lint`
- Firestore Emulator を起動しての rules-test

Instrumented テストは、MVP ではローカルで実行します。

## rules-test

Firestore のルール（`firebase/firestore.rules`）を、Firestore Emulator で検証します。テストは `firebase/rules-test/tests/` にあり、アプリの Repository と同じ書き込み（バッチの組み合わせ）で、通るべき操作が通り、拒否すべき操作が拒否されることを確かめます。

```
cd firebase/rules-test
npm ci
npm test
```

- Firebase CLI は rules-test の依存に含めているので、グローバルに入れる必要はありません。エミュレータには Java が必要です。
- エミュレータはポート 8085 を使い、実際のプロジェクトには接続しません（`demo-` で始まるプロジェクトID）。

## エミュレータを使う結合テスト

同時実行（上記）と、合流で購入の記録まで写ること（`FamilyMergeTest`）は、Android のテストから Firebase のエミュレータ（Auth と Firestore）につないで確かめます。アプリと同じリポジトリの実装を、端末ごとに別の FirebaseApp で動かします。これらのテストには `@EmulatorTest` を付けています。通常の `connectedDebugAndroidTest` では飛ばします（エミュレータのホストが渡されないため）。

Android Emulator を起動しておき、次のコマンドで実行します。Firebase のエミュレータを起動し、テストが終わると止めます。

```
cd firebase/rules-test
npx firebase emulators:exec --only firestore,auth --project demo-cartmate \
  "cd ../.. && ./gradlew :app:connectedDebugAndroidTest -PemulatorTests"
```

- 端末からは、ホストの PC を `10.0.2.2` で参照します（Android Emulator）。実機で実行するときは `adb reverse tcp:8085 tcp:8085` と `adb reverse tcp:9099 tcp:9099` を実行し、`-PemulatorHost=127.0.0.1` を付けます。
- Auth のエミュレータは HTTP なので、debug 版だけ、エミュレータのホストへの平文の通信を許しています（`app/src/debug/res/xml/network_security_config.xml`）。

## キーボードのタイミングに依存するテスト

シートとソフトキーボードの表示の順番を確かめるテスト（`AddItemSheetKeyboardTest`、`CreateListSheetKeyboardTest`）には `@KeyboardTimingTest` を付け、通常の `connectedDebugAndroidTest` からは外しています。キーボードの起動が遅い端末（起動したばかりのエミュレータや、テストを続けて実行して遅くなったエミュレータ）では、アプリがキーボードを待つ上限を過ぎてシートを先に出すため、アプリに問題がなくても失敗することがあるためです。

シートやキーボードまわりを変更したときは、エミュレータを起動して落ち着かせてから、次のコマンドでこのテストだけを実行します。

```
./gradlew :app:connectedDebugAndroidTest -PkeyboardTimingTests
```

## 名寄せ辞書の計測

辞書を大きく増やすときは、実機で次を実行し、Logcat の `DictionaryBenchmark` の結果を [suggestions.md](design/suggestions.md#計測) の表と比べます。通常のテストの実行では飛ばします。

```
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.cheesecomer.cartmate.data.dictionary.DictionaryBenchmark \
  -Pandroid.testInstrumentationRunnerArguments.dictionaryBenchmark=true
adb logcat -d -s DictionaryBenchmark:I
```

辞書の元データ（`dictionary/`）の検証は、`./gradlew :domain:test`（`DictionaryFilesTest`）で行います。
