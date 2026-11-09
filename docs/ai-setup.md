# AI の名寄せの準備（Firebase・Google Cloud・OpenAI）

AI による名寄せの候補（[ロードマップ](roadmap.md) フェーズ6）を本番で動かすまでの手順です。課金と本番の設定を変えるので、コンソールでの操作は手作業で行います。API キーはリポジトリにもアプリにも含めず、Secret Manager にだけ置きます（[security.md](design/security.md#ai-の名寄せの関数)）。

コマンドは `firebase/` で実行します。Firebase CLI は rules-test の依存に入っているもの（`npx --prefix rules-test firebase ...`）を使えます。

## Blaze プランへの切り替え

Cloud Functions と Secret Manager を使うには、Blaze（従量課金）プランが必要です。無料枠に収まる見込みで、関数の呼び出しは月に数百回程度です。

1. Firebase コンソールで `cartmate-cheesecomer` を開き、左下の「アップグレード」から Blaze を選びます。
2. 請求先アカウントを作るかつなぎます。予算を設定する画面が出たら、次の節の内容で作ります。

## 予算アラート

少しでも課金されたら、すぐに知らせが届くようにします。予算アラートは知らせるだけで、利用を止めません。止めるのは関数の上限（月1ドル）と止める設定（`config/ai`）、OpenAI 側の上限の役目です。

1. Google Cloud コンソールの「お支払い > 予算とアラート」で、予算を作ります。

   | 項目 | 値 |
   |---|---|
   | 名前 | CartMate |
   | 範囲 | プロジェクト `cartmate-cheesecomer`、すべてのサービス |
   | 金額 | 指定額 100円（毎月） |
   | しきい値 | 実績の 1%・50%・100%（1円の課金で最初の知らせが届く） |
   | 通知 | 請求先アカウントの管理者と、プロジェクトのユーザーにメールで通知する |

2. OpenAI の費用は Google の請求には載りません。OpenAI 側の上限は「OpenAI の API キー」の節で設定します。

## OpenAI の API キー

1. OpenAI Platform（https://platform.openai.com/）で、CartMate 専用のプロジェクトを作ります。ほかの用途のキーと費用を混ぜないためです。
2. プロジェクトの「Limits」で、使えるモデルを `gpt-6-luna` だけにし、月の予算を 1ドルにします（関数の上限と同じ。関数の数え方が漏れても、ここで止まる）。
3. 「API keys」で、権限を「Restricted」にしたキーを作ります。許すのは「Responses」（Write）だけです。キーは画面を閉じると二度と表示されないので、すぐに次の節で登録し、ほかには保存しません。

## Secret Manager への API キーの登録

関数が読む秘密情報として登録します。値はターミナルに貼り付けて入力し、シェルの履歴やファイルに残しません。

```sh
npx --prefix rules-test firebase functions:secrets:set OPENAI_API_KEY
```

- 初回は Secret Manager の API を有効にするか聞かれるので、有効にします。
- 登録したキーは関数（`judgeName`）だけが読めます。デプロイのときに、関数のサービス アカウントに読み取りの権限が付きます。
- キーを作り直したら、同じコマンドで新しい版を登録し、関数をデプロイし直します（関数は、デプロイしたときの版を読むため）。古い版は `firebase functions:secrets:prune` で消します。

## Artifact Registry の古いイメージを消す設定

関数をデプロイするたびに、コンテナのイメージが Artifact Registry にたまり、保存の料金がかかります。古いものを自動で消す設定をします。

```sh
npx --prefix rules-test firebase functions:artifacts:setpolicy --location asia-northeast1 --days 1
```

- 最初のデプロイの前に実行すると、リポジトリがまだないと言われることがあります。そのときは、最初のデプロイのあとにもう一度実行します（デプロイのときに設定を勧められたら、それに答えても同じです）。
- 確かめるには、Google Cloud コンソールの「Artifact Registry > gcf-artifacts（asia-northeast1）> クリーンアップ ポリシー」を見ます。

## 止める設定と上限（`config/ai`）

関数は `config/ai` がない、または `enabled` が false のとき AI を呼びません（[data-model.md](design/data-model.md#ai-の名寄せフェーズ6)）。デプロイの前に、Firestore のコンソールでドキュメントを作ります。

| フィールド | 型 | 値 |
|---|---|---|
| `enabled` | boolean | 最初は `false`（確認が済んでから `true`） |
| `monthlyBudgetMicroUsd` | number | `1000000`（月1ドル） |
| `perHouseholdMonthlyCalls` | number | `200` |

AI を止めたいときは、`enabled` を `false` にします。すぐに効き、デプロイし直す必要はありません。

## 上限の80%と100%の知らせ

関数は、月の使用量が上限の80%と100%に達したとき、それぞれ一度だけ警告のログ（`aiBudgetThreshold`）を書きます。このログでメールが届くようにします。

1. Google Cloud コンソールの「ロギング > ログ エクスプローラ」で、次のクエリを入力します。

   ```
   resource.type="cloud_run_revision"
   jsonPayload.aiBudgetThreshold>=80
   ```

2. 「アラートを作成」で、名前を「CartMate AI の上限」、通知チャンネルを自分のメールにして保存します。

## 関数とルールのデプロイ

```sh
npx --prefix rules-test firebase deploy --only functions,firestore:rules
```

- 関数は呼び出しに App Check を求めます（コードで強制しているので、コンソールでの設定はいりません）。開発に使う端末のデバッグ用のトークンは、Firestore と同じものがそのまま使えます（[release.md](release.md#app-check-の強制)）。
- 商品名を外部の AI に送るようになるので、同じタイミングでプライバシーポリシーを公開します（`firebase deploy --only hosting`）。Play Console のデータセーフティの申告も見直します（[release.md](release.md#データセーフティ)）。

## 完了の確認

1. `config/ai.enabled` を `true` にします。
2. 共有のリストに、辞書にない名前（「胡麻ドレ」など）を追加してチェックします。
3. Firestore のコンソールで、`households/{家族のID}/nameJudgements` に `status: "done"` の判定ができていることを確かめます。`aiUsage/{yyyymm}` の `calls` と `costMicroUsd` が増えていることも確かめます。
4. 同じ品目の別の名前（「ごまドレッシング」）を追加してチェックし、商品名の管理に AI の候補が出ることを確かめます。
5. `config/ai.enabled` を `false` にして、新しい名前をチェックします。判定が `failed` になり、アプリはそのまま使えることを確かめます。確かめたら `true` に戻します。
6. OpenAI Platform の「Usage」で、使ったモデルが `gpt-6-luna` だけであることを確かめます。
