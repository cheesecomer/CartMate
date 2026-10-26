# データモデル（Cloud Firestore）

Firestore には、アプリ全体で1つのデータベースがあります。家族ごとのデータは `households/{hid}` 以下にまとめ、その家族のメンバーだけが読み書きできます（[security.md](security.md)）。各端末は Snapshot Listener でこの領域を購読し、変更をリアルタイムに受け取ります。

## コレクション

```
users/{uid}
  householdId: string | null
  updatedAt: timestamp

invites/{code}                               # code = ドキュメントID（推測困難な乱数）
  householdId, createdBy, inviterName, createdAt, expiresAt, maxUses(=1), usedCount

households/{hid}
  name, createdBy, createdAt                   # name は「○○さんの家族」。画面には表示しない

households/{hid}/members/{uid}
  role: "owner" | "member", displayName, photoUrl?, joinedAt, joinedVia: string | null(招待コード)

households/{hid}/lists/{listId}
  name, memo: string | null, visibility: "shared" | "private",
  importedFrom?: string,                     # 合流で写したときだけ。コピー元の家族のID（lists・items・products・purchases に共通）
  sortKey: number, createdBy, createdAt,
  status: "active" | "deleted", deletedAt?, deletedBy?
  clearedAt?: timestamp                      # 前回の買い物のチェック済みを片付けた時刻（端末の時刻）。これより前のチェックは表示しない

households/{hid}/items/{itemId}              # リストの下ではなく家族直下（フラット）
  listId, productId, inputName, sortKey: number,
  quantity: number | null, unit: string | null, memo: string | null,
  visibility: "shared" | "private",          # プライベートのリストでは常に private
  status: "open" | "completed",
  createdBy, createdAt, completedAt: timestamp | null, completedBy: string | null
  checkedAt: timestamp | null              # 端末でチェックした時刻。リスト詳細の購読に使う（completedAt はサーバーに届くまで端末の中で値がないため）

households/{hid}/products/{productId}        # productId = 正規化名から決まる決定的ID
  displayName, normalizedName, searchKey,
  createdBy, createdAt,
  mergedInto: string | null, mergedAt?, mergedBy?

households/{hid}/purchases/{purchaseId}      # purchaseId = itemId
  itemId, listId, listNameSnapshot, productId, inputName, quantity, unit, memo,
  visibility,                                # 項目と同じ。private は purchasedBy（＝項目を作った本人）だけが読める
  checkedAt,                                 # 端末でチェックした時刻（オフラインでチェックした場合は送られた時刻より前）
  purchasedAt, purchasedBy, active: bool, revokedAt: timestamp | null, revokedBy: string | null
```

## 設計判断

| 判断 | 理由 |
|---|---|
| **items をフラットにする**（`lists/{id}/items` ではなく `households/{hid}/items` + `listId`） | ①リスト一覧の未購入件数、②「家族の未購入リストにある商品は重複サジェストしない」の判定、③リスト詳細、を **`status == "open"` の購読1本** でまかなえる。サブコレクションにすると、リストの数だけ購読するか collection group クエリが必要になる。また、リストを論理削除しても items が孤立しない。 |
| **ProductAlias のサブコレクションを使わず、`products.mergedInto` で表す** | 別表記も1つの product ドキュメントとして持ち、統合するときは `mergedInto` に統合先を入れるだけにする。購入履歴を書き換えずに済み（追記専用を保てる）、統合の解除も `null` に戻すだけで済む。家族単位の product は数百件程度と見込まれるので全件を購読でき、統合の解決はクライアント（`ProductGraph`）で行う。 |
| **productId を正規化名のハッシュから決める**（`"n_" + sha256(normalizedName)` の先頭128bitを hex 化） | オフライン中に2台の端末が同じ「牛乳」を追加しても、product が重複しない。機械的な正規化で一致する名前は自動で同じ商品になる（[要件](../requirements.md) §4.2）。 |
| **purchaseId = itemId** | 冪等性の要。1つの item から購入履歴が2件できることが、構造上起きない（[sync.md](sync.md)）。 |
| **lists は論理削除する**（`status: deleted`） | 削除の直後に他の端末で項目が追加されるような競合でも、データが失われない（[sync.md](sync.md#リスト削除の競合)）。 |
| **並び順は `sortKey`（小数）で持つ** | 並び替えでは、動かした1件だけを、前後の項目の `sortKey` の中間の値に書き換える。動かした1件だけを書き込めばよく、オフラインでもキューに積める。別々の項目を同時に動かしても競合しない。同じ項目を同時に動かした場合は、後から反映された位置になる。新しいリスト・項目は、その時点の最大値 + 1 にして末尾に置く。中間の値が取れなくなったとき（小数の精度の限界）は、その一覧の `sortKey` を振り直す（まれな一括書き込み）。計算は domain の `SortKeys` が行う。 |
| **`version` フィールドを持たない** | 状態遷移（open↔completed）を Security Rules とトランザクションで守るため、楽観ロック用の番号は要らない。 |
| **購入履歴に `checkedAt` を持つ** | オフライン中のチェックは確定が遅れるため、実際にチェックした時刻を別に残す。周期の計算にはこちらを使う（[suggestions.md](suggestions.md#周期推定)）。 |
| **公開範囲を各ドキュメントに持つ**（`visibility`） | Firestore のルールはフィルタにならないため、クエリ側で `visibility` と `createdBy` / `purchasedBy` を条件にして、ルールが必ず満たされる範囲だけを購読する（下記「購読とクエリ」）。リストの公開範囲を項目にも写しておくと、項目のルールでリストを読まずに判定できる。 |
| **公開範囲はあとから変えない** | 変えられるようにすると、共有からプライベートに変えた時点で他の端末のキャッシュに残る、購入履歴の公開範囲も書き換える必要がある、など扱いが複雑になる。要件でも登録時だけ選べる（[要件](../requirements.md) §3.4）。 |
| **product は公開範囲を持たない** | product は決定的ID（正規化名）で家族に1つなので、プライベートと共有で同じ名前の商品を分けられない。代わりに、補完の候補には「自分に見える項目か購入の記録がある product」だけを出す（[suggestions.md](suggestions.md#入力中の補完)）。そのため、プライベートだけで使った商品名は他のメンバーの画面には出ないが、データベース上の product の名前は家族のメンバーが読める。家族内のサプライズを画面に出さない程度の秘匿として受け入れる。 |
| **`users/{uid}` と `invites/{code}` を追加する** | 起動時に自分の所属家族を引くため。また、まだ家族に参加していないユーザーが招待コードを検証するため（家族の領域はメンバーしか読めない）。 |
| **招待に `inviterName` を持つ** | 参加の確認画面の「○○さんの家族」を表示するため。参加前のユーザーは `members` を読めないので、発行した人の表示名を招待に写しておく。 |
| **オーナーは `members.role` で表し、離脱時に引き継ぐ** | オーナーが抜けるときは、自分の `members` の削除と同じバッチで、`joinedAt` がいちばん早いメンバーの `role` を `owner` にする。 |
| **合流では同じIDで写し、`importedFrom` を付ける** | 自分しかいない家族からほかの家族に合流するとき、lists・items・products・purchases を同じドキュメントIDのまま合流先に写し、コピー元の家族のID（`importedFrom`）を加える。ルールはコピー元と同じ内容であることを確かめて書き込みを認めるので、作成日時や購入日時を写しても偽装にならない。同じIDなので、中断してもやり直せば続きから写せる（すでにあるものは飛ばす）。products は決定的IDなので、同じ商品名はそのまま同じ商品になる。lists の `sortKey` だけは、合流先の末尾に並ぶよう付け直す。 |
| **抜けたメンバーのデータは消さない** | 共有のデータは家族のもの。プライベートのデータは、同じ家族にもう一度参加したときに戻るよう残す。作った人の `members` がないときは「退会したメンバー」と表示する。アカウントの削除だけは、本人のプライベートなリスト・項目・購入の記録と `users/{uid}` を消す。 |

## 購読とクエリ

読み取り数が増えすぎないよう、購読の範囲を次のように決めています。

| 用途 | クエリ | 種別 |
|---|---|---|
| 所属家族 | `users/{uid}` | listener |
| メンバー | `households/{hid}/members` | listener（数件） |
| リスト | `lists where visibility == "shared"` と `lists where visibility == "private" and createdBy == 自分`（全件。表示するかどうかはクライアントで判定） | listener 2本（数十件） |
| 未購入項目 | `items where status == "open" and visibility == "shared"` と、同じ条件で `visibility == "private" and createdBy == 自分` | listener 2本 |
| リスト詳細の項目 | `items where listId == X and visibility == "shared" and status == "open"` と `items where listId == X and visibility == "shared" and status == "completed" and checkedAt >= now-24h`、プライベートの同じ2本（範囲の条件はフィールドのないドキュメントを除くので、OR クエリにしない。並び順は sortKey でクライアント側） | 画面を表示している間だけ listener 4本 |
| 商品 | `households/{hid}/products`（全件） | listener |
| 購入履歴 | `purchases where visibility == "shared" and purchasedAt >= now-365d` と `purchases where visibility == "private" and purchasedBy == 自分 and purchasedAt >= now-365d` | listener 2本。`active` の絞り込みはクライアント側 |

共有とプライベートを2本に分けるのは、ルールで読めない可能性のあるドキュメントを含むクエリが拒否されるためです。Firestore の OR クエリ（`visibility == "shared" or createdBy == 自分`）がルールで許可されることを実装時に確認できれば、1本にまとめます。

複合インデックスは、上のクエリ（`visibility` と `createdBy` / `purchasedBy`、リスト詳細の `listId`・`status`・`checkedAt`）に必要なものだけを作ります。定義は `firebase/firestore.indexes.json` にあります。等号の条件のフィールドはアルファベット順に並べ、範囲の条件のフィールドを最後に置きます（Firestore が求める並びと合わせるため）。

### 購入履歴の取得範囲（365日）

周期の推定には、直近11回分の購入日があれば足ります。取得範囲を365日に区切ることで、取得件数が「1年あたりの購入数」で頭打ちになります。

その代わり、周期がおよそ180日を超える商品は予測の対象外になります。これは MVP の制約として受け入れます。件数が問題になった場合は、キャッシュ済みの履歴に `updatedAt` 以降の差分だけを足す方式に移行できます（MVP では実装しません）。
