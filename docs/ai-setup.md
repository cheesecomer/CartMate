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

OpenAI の API は、ChatGPT とは別のサービス（OpenAI Platform）で、料金も別です。ChatGPT の有料プランを使っていても、API の料金は含まれません。

1. https://platform.openai.com/ にログインします（ChatGPT と同じアカウントで入れます）。初めてなら、組織（Organization）の名前を聞かれるので、自分の名前などを入れます。
2. 支払いを設定します。API は前払いのクレジットで動き、残高がないと呼び出しが失敗します。
   - 右上の歯車（Settings）から「Billing」を開き、カードを登録して、最小額（5ドル）のクレジットを買います。
   - 自動で買い足す設定（auto recharge）はオフのままにします。
3. CartMate 専用のプロジェクトを作ります。プロジェクトは、キーと費用と上限をまとめる単位です。ほかの用途のキーと費用を混ぜず、上限を CartMate だけにかけるために分けます。
   - 画面の左上に、組織の名前とプロジェクトの名前（最初は「Default project」）が並んでいます。プロジェクトの名前を押すと、一覧の下に「Create project」があります。
   - 名前は `CartMate` にします。作ったら、左上の表示が `CartMate` に切り替わっていることを確かめます。以降の操作は、すべてこのプロジェクトで行います。
4. プロジェクトの月の上限を1ドルにします（関数の上限と同じ。関数の数え方に漏れがあっても、ここで止まります）。
   - Settings の「Project」の「Limits」を開き、「Spend」の「Edit spend limit」を押します。
   - 「Monthly spend limit」に `1` を入れ、「Enforce a hard limit」をオンにして「Save」します。オフのままだと知らせが届くだけで、呼び出しは止まりません。
   - 同じ「Limits」に、使えるモデルを選ぶ欄があれば、`gpt-6-luna` だけを許します。欄がなければ飛ばします（キーが使うモデルは関数で決めているため）。
5. 「API keys」で、キーを作ります（「Create new secret key」）。
   - 「Owned by」は「Service account」を選びます。自分のアカウントに紐づかないキーになり、関数用であることが分かります。名前は `judgeName` にします。
   - プロジェクトは `CartMate` を選びます。
   - 権限（Permissions）は「Restricted」にし、「Responses API」だけを「Write」にします。ほかは「None」のままにします。
   - 期限を選べるなら、1年にします。期限が来たら、キーを作り直して次の節の手順で登録し直します。
6. 作ったキー（`sk-` で始まる文字列）は、画面を閉じると二度と表示されません。すぐに次の節で登録します。メモ帳やチャットなど、ほかの場所には貼りません。分からなくなったら、キーを消して作り直せば済みます。

画面の表示名は変わることがあります。手順と違うところがあれば、教えてください。手順書を直します。

## Secret Manager への API キーの登録

関数が読む秘密情報として登録します。値はターミナルに貼り付けて入力し、シェルの履歴やファイルに残しません。

```sh
npx --prefix rules-test firebase functions:secrets:set OPENAI_API_KEY
```

- 初回は Secret Manager の API を有効にするか聞かれるので、有効にします。
- 登録できたかは、次のコマンドで確かめます。キーの値は表示されず、版の一覧が出ます。版が1つあり、状態が `ENABLED` なら登録できています。

  ```sh
  npx --prefix rules-test firebase functions:secrets:get OPENAI_API_KEY
  ```

  Google Cloud コンソールの「セキュリティ > Secret Manager」で、`OPENAI_API_KEY` の版を見ても確かめられます。値を表示する操作（`functions:secrets:access` や、コンソールの「シークレットの値を表示」）は、画面やログにキーが残るので使いません。
- キーそのものが使えるかは、デプロイのあとに「完了の確認」の手順で確かめます。
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
