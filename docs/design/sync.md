# 同期・チェック/Undo・オフライン

## 用語

画面上ではすべて「チェック」「チェック済み」と表記します。チェックは「カゴに入れた時」に行うのが普通ですが、別の場所で買って帰宅後にチェックする場合もあるため、どちらにも当てはまる表現にしています。

コード上の名前（`completed`, `purchases`）は「購入」のままです。予測から見れば購入の記録であることに変わりがないためです。

## チェック（購入完了）

`completeItem(itemId, checkedAt)` は Firestore トランザクションで実行します。トランザクションはオンラインでしか完了しないため、ユーザーの操作はいったん[保留チェック](#保留チェック)に入り、接続があるときにこの処理が呼ばれます。

```
transaction {
  item = get(items/itemId)
  if item.status != "open" -> return AlreadyCompleted(item.completedBy, item.completedAt)  // 他の端末が先に完了した。書き込まない
  list = get(lists/item.listId)
  update(items/itemId, status=completed, completedAt=serverTimestamp, completedBy=me)
  set(purchases/itemId, {itemId, listId, listNameSnapshot=list.name, productId, inputName,
                         quantity, unit, checkedAt, purchasedAt=serverTimestamp, purchasedBy=me,
                         active=true, revokedAt=null, revokedBy=null})
}
```

- **購入履歴が二重にできない理由**: 購入履歴のIDは itemId です。同時にチェックしても、トランザクションの再試行で `status != open` を読み、何もせずに終わります。クライアントが壊れていても、ルールの `resource.data.status == 'open'` で拒否されます。
- **Undo したあとの再チェック**: 同じ `purchases/{itemId}` を `active=true` と新しい `purchasedAt` で上書きします。取り消した購入は残しません。
- 戻り値: `sealed interface CompleteResult { Completed; AlreadyCompleted(by, at); Retry; Rejected(e) }`
  - `Retry`: 通信エラーやタイムアウト。保留のまま残して、あとで再試行する。
  - `Rejected`: ルールによる拒否など、再試行しても成功しないもの。
- `AlreadyCompleted` は、別の場所で二重に買ったことに気づく手がかりになるため、**黙って捨てずに表示します**（[ui.md](ui.md#他のメンバーによるチェックの通知)）。

## Undo

`reopenItem(itemId)`:

```
transaction {
  item = get(items/itemId)
  if item.status != "completed" -> return AlreadyOpen       // 他の端末が先にUndoした
  update(items/itemId, status=open, completedAt=null, completedBy=null)
  update(purchases/itemId, active=false, revokedAt=serverTimestamp, revokedBy=me)
}
```

- 予測には `active == true` の購入だけを使うため、Undo するとそのまま予測の対象から外れます。
- Undo は家族の誰でもできます。
- Undo の入口は2つです。①チェック直後の Snackbar（約5秒）。②リスト詳細の「チェック済み（直近24時間）」セクション（画面を移動したあとでも確認・取り消しができる）。

## 競合の振る舞い

| シナリオ | 結果 |
|---|---|
| A と B が同じ項目を同時にチェック | 片方が `Completed`、もう片方が `AlreadyCompleted`。後者には「牛乳は 花子さんが 10:02 にチェック済みです」と表示する。有効な購入履歴は1件 |
| A がチェック → B が Undo → A が Snackbar から Undo | A は `AlreadyOpen` になり、「すでにチェックが外されています」と短く表示する |
| A が Undo、同時に B が再チェック | トランザクションで直列化され、項目の状態と購入履歴の active は必ず一致する |
| チェックと未購入項目の削除が同時 | 削除のルールが `status == 'open'` を要求するので、先にチェックした方が勝つ |
| A がオフラインでチェック → その間に B がオンラインでチェック → A が接続復帰 | A の確定は `AlreadyCompleted` になり、A に「牛乳は 花子さんが 10:02 にチェック済みです」と表示する。通知は接続が戻るまで遅れる |

## 保留チェック

店内は電波が弱いことが多く、チェックはカゴに入れた時点で行われるのが普通です。そのため、チェックと Undo は **オンラインかどうかに関係なく端末内の保留キューに入れ、接続があるときに上記のトランザクションで確定します**（[要件](../requirements.md) §3.3）。

### 保存する内容

DataStore に `itemId → PendingCheck(target: COMPLETED | OPEN, checkedAt, queuedAt)` を保存します。項目ごとに「最終的にどうしたいか」を1件だけ持ちます。

| 操作 | 保留キューの変化 |
|---|---|
| 未チェックの項目をチェック | `target=COMPLETED, checkedAt=端末の現在時刻` を登録 |
| 保留中のチェックを Undo | 保留を削除する（サーバーには何も書かない） |
| 確定済みのチェックを Undo | `target=OPEN` を登録 |
| サーバーの状態がすでに `target` と一致した | 保留を削除する |

### 確定処理（PendingCheckSyncer）

- オンラインなら、操作のたびにすぐ実行します。1秒程度で確定します。
- 接続が戻ったとき、アプリの起動時、WorkManager（ネットワーク接続を条件にした1回限りのジョブ）からも実行します。アプリを閉じたまま店を出ても、接続が戻れば確定します。
- 項目ごとに順に処理し、トランザクションの結果に応じて次のように扱います。
  - `Completed` / `AlreadyOpen` → 保留を削除する。
  - `AlreadyCompleted(by, at)` で `by != 自分` → 保留を削除し、他のメンバーが先にチェックしていたことを通知する。
  - `Retry` → 保留を残す。WorkManager の再試行（指数バックオフ）に任せる。
  - `Rejected` → 保留を削除し、「牛乳のチェックを反映できませんでした」と表示する（家族から外された場合、7日以上確定できなかった場合など）。
- 確定するときは、保存していた `checkedAt` をそのまま購入履歴に書きます。`purchasedAt` は確定した時刻（サーバー時刻）になりますが、**周期の計算には `checkedAt` を使う**ため、日付をまたいで確定しても購入日はずれません。

### 画面での見え方

- 項目は「サーバーの状態に保留を重ねたもの」として表示します。チェックした項目はその場でチェック済みセクションに移り、確定するまで「未同期」マークを付けます。
- オフラインの間は、他の家族の端末にチェックが伝わりません。そのため、二重購入の通知も接続が戻るまで遅れます。

## オンライン / オフラインの境界

チェックと Undo は保留キューに入れ、接続時にトランザクションで確定します。それ以外の日常的な操作は、オフラインでも Firestore の書き込みキューに積みます。頻度の低い管理操作だけをオンライン限定にします。

| 操作 | オフライン | 方式 | 理由 |
|---|---|---|---|
| リスト・項目・履歴の閲覧 | 可 | 永続キャッシュ | |
| 項目の追加（product の作成を含む） | **可** | バッチ書き込み（キューに積む） | 店内など電波の弱い場所でも入力できるようにする。product ID が決定的なので重複しない |
| 未購入項目の削除 | 可 | delete（キューに積む） | 同期したときにチェック済みなら、ルールで拒否されるだけ |
| リスト作成 | 可 | set（キューに積む） | |
| **チェック / Undo** | **可（保留）** | 保留キュー → 接続時にトランザクション | 電波の弱い店内でもチェックできるようにする。冪等性はトランザクションとルールで保証する。トランザクションは Firestore の書き込みキューに積めないので、独自の保留キューを使う |
| リスト削除 | 不可 | update | 「空である」ことを、キャッシュの古い状態で判定するのを避ける |
| 家族の作成・参加・招待の発行・商品の統合 | 不可 | バッチ / トランザクション | 頻度が低く、結果をすぐに確認したい |

### オンライン判定と表示

- `ConnectivityManager.NetworkCallback` で `NET_CAPABILITY_VALIDATED` を監視し、`StateFlow<Boolean>` で提供します。
- オフラインの間は、画面上部に細いバナー（「オフライン — 変更は接続後に家族へ共有されます」）を表示します。
- オンライン判定が誤っている場合に備えて、トランザクションは10秒でタイムアウトさせ、`Retry` として扱います。
- 未同期のもの（Firestore の書き込みキューにある追加など＝`hasPendingWrites`、および保留中のチェック）には、小さな「未同期」アイコンを付けます。
- オンライン限定の操作をオフラインで行おうとした場合は、Snackbar で理由を伝えます。
- キューに積んだ書き込みがルールで拒否された場合（例: 同期する前に家族から外された）、アプリを開いていれば Snackbar で知らせます。

## リスト削除の競合

- 削除は、UI では「未購入が0件のリスト」に対してだけ提供します。すぐには削除せず、確認ダイアログを出します。
- **表示ルール**: `status == active` **または** そのリストに未購入の項目がある場合、一覧に表示します。
  - 削除と同時に別の端末で項目が追加された場合や、削除済みリストの項目を Undo した場合でも、項目が見えなくなることはありません。項目がすべて片付くと、再び非表示になります。
- 最後の項目をチェックしても、リストは自動では削除しません（Undo との競合を避けるため）。
