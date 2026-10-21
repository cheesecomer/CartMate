# データモデル（Cloud Firestore）

Firestore には、アプリ全体で1つのデータベースがあります。家族ごとのデータは `households/{hid}` 以下にまとめ、その家族のメンバーだけが読み書きできます（[security.md](security.md)）。各端末は Snapshot Listener でこの領域を購読し、変更をリアルタイムに受け取ります。

## コレクション

```
users/{uid}
  householdId: string | null
  updatedAt: timestamp

invites/{code}                               # code = ドキュメントID（推測困難な乱数）
  householdId, createdBy, createdAt, expiresAt, maxUses(=1), usedCount

households/{hid}
  name, createdBy, createdAt

households/{hid}/members/{uid}
  role: "owner" | "member", displayName, photoUrl?, joinedAt, joinedVia: string | null(招待コード)

households/{hid}/lists/{listId}
  name, sortKey: number, createdBy, createdAt,
  status: "active" | "deleted", deletedAt?, deletedBy?

households/{hid}/items/{itemId}              # リストの下ではなく家族直下（フラット）
  listId, productId, inputName, sortKey: number,
  quantity: number | null, unit: string | null,
  status: "open" | "completed",
  createdBy, createdAt, completedAt: timestamp | null, completedBy: string | null

households/{hid}/products/{productId}        # productId = 正規化名から決まる決定的ID
  displayName, normalizedName, searchKey,
  createdBy, createdAt,
  mergedInto: string | null, mergedAt?, mergedBy?

households/{hid}/purchases/{purchaseId}      # purchaseId = itemId
  itemId, listId, listNameSnapshot, productId, inputName, quantity, unit,
  checkedAt,                                 # 端末でチェックした時刻（保留チェックの場合は確定より前）
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
| **`users/{uid}` と `invites/{code}` を追加する** | 起動時に自分の所属家族を引くため。また、まだ家族に参加していないユーザーが招待コードを検証するため（家族の領域はメンバーしか読めない）。 |

## 購読とクエリ

読み取り数が増えすぎないよう、購読の範囲を次のように決めています。

| 用途 | クエリ | 種別 |
|---|---|---|
| 所属家族 | `users/{uid}` | listener |
| メンバー | `households/{hid}/members` | listener（数件） |
| リスト | `households/{hid}/lists`（全件。表示するかどうかはクライアントで判定） | listener（数十件） |
| 未購入項目 | `items where status == "open"` | listener |
| リスト詳細の項目 | `items where listId == X and (status == "open" or completedAt >= now-24h)`（OR クエリ。並び順は sortKey でクライアント側） | 画面を表示している間だけ listener |
| 商品 | `households/{hid}/products`（全件） | listener |
| 購入履歴 | `purchases where purchasedAt >= now-365d` | listener。`active` の絞り込みはクライアント側 |

複合インデックスは、リスト詳細のクエリ（`listId` と、`status` / `completedAt` の OR）に必要なものだけを作ります。具体的な定義は、フェーズ2でクエリを実装するときに `firestore.indexes.json` で確定させます。

### 購入履歴の取得範囲（365日）

周期の推定には、直近11回分の購入日があれば足ります。取得範囲を365日に区切ることで、取得件数が「1年あたりの購入数」で頭打ちになります。

その代わり、周期がおよそ180日を超える商品は予測の対象外になります。これは MVP の制約として受け入れます。件数が問題になった場合は、キャッシュ済みの履歴に `updatedAt` 以降の差分だけを足す方式に移行できます（MVP では実装しません）。
