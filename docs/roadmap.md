# ロードマップ

MVP は5つのフェーズに分けて実装します。各フェーズの終わりに動作とコードをレビューしてから、次に進みます。

## フェーズ1: 基盤とローカル状態の UI

- Gradle 構成（`app`, `domain`）、Version Catalog、Hilt、テーマ、Navigation。
- `domain` のモデルと Repository インターフェースを **最終形で** 定義する（フェーズ2で実装を差し替えるだけで済むように）。
- インメモリ Repository（チェック・Undo は [sync.md](design/sync.md) と同じ意味論。購入履歴の記録も含める）。
- 画面: リスト一覧（作成、未購入件数、空リストの削除）、リスト詳細（追加、数量・単位、チェック、Undo Snackbar、チェック済みセクション、スワイプ操作と項目の削除）。
- 入力状態の reducer と IME フロー（サジェストの中身は空でよい）。
- **完了条件**: エミュレータ上で、ログインなしでリスト作成・連続追加・チェック・Undo・削除ができる。reducer のテストが通る。

## フェーズ2: 認証・家族グループ・同期

- Firebase プロジェクトの設定（`google-services.json` の配置、SHA-1/SHA-256 の登録）。
- Credential Manager による Google ログイン、`users/{uid}`。
- 家族の作成、招待（リンクとコードの発行、共有シートでの送信、リンクからの参加、コード入力での参加）、メンバー一覧、オーナーによるメンバー削除。
- App Links と Firebase Hosting（`cartmate.cheesecomer.com` の DNS 設定を含む）。App Check（Play Integrity）。
- Firestore Repository（lists, items, products の最小構成）、Snapshot Listener、オンライン判定とオフラインバナー。
- `firestore.rules` と `firestore.indexes.json`、rules-test（家族・招待・リスト・項目作成の部分）。
- **完了条件**: 2つのアカウントが同じ家族に参加し、追加した内容が相互に反映される。rules-test が通る。

## フェーズ3: 購入履歴と商品

- チェック・Undo のトランザクションと、対応するルール。
- 保留チェック（DataStore, PendingCheckSyncer, WorkManager）と、サーバーの状態に保留を重ねた表示。
- `NameNormalizer`、決定的な productId、ProductGraph、表記ゆれのチップ、商品の統合画面。
- リスト削除の表示ルール。
- 他のメンバーによるチェックの通知と、チェック済みセクションでのチェックした人・時刻の表示。
- rules-test（チェック・Undo・購入履歴・product）、同時実行テスト（[testing.md](testing.md#同時実行) の 1〜4, 7〜10）。
- **完了条件**: 同時にチェックしても購入履歴が重複しない。Undo で無効化される。オフラインでのチェックが接続後に確定する。リストを削除しても購入履歴が残る。

## フェーズ4: 予測とサジェスト

- CycleEstimator、ListSimilarity、SuggestionEngine、EarlyPurchaseDetector と、その単体テスト。
- サジェストパネル（時期が近い商品・補完）、Detail の注意表示、補足テキスト。
- 購入履歴の購読（365日分）。
- テストデータの投入スクリプト。
- **完了条件**: domain のテストが通る。テストデータで、3回目の購入から予測が出ることを画面で確認できる。

## フェーズ5: 受け入れ

- 同時実行テストの残り（5〜6）、Compose UI テスト。
- 実機2台での受け入れチェックリスト（[要件](requirements.md) §10 の全項目、再接続、権限、誤操作、IME、TalkBack、文字サイズ200%）。
- 読み取り数の計測（Firebase コンソール）。
- **完了条件**: チェックリストがすべて合格。
