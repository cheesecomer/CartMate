# 商品の同一性とサジェスト

この資料のロジックは、すべて `domain` モジュールの純粋関数として実装します。

## 商品名の正規化

`NameNormalizer` は3つのキーを作ります。

| キー | 処理 | 用途 |
|---|---|---|
| `displayName` | NFKC → 前後の空白除去 → 連続空白を1つに | 表示 |
| `normalizedName` | displayName → 英字を小文字化 | **同一性の判定**（決定的IDの元） |
| `searchKey` | normalizedName → カタカナをひらがなに → 空白・記号を除去 | **補完と類似候補の検索だけ**に使う。自動で同じ商品として扱うことはしない |

NFKC で全角・半角の差（「ｷﾞｭｳﾆｭｳ」→「ギュウニュウ」、「１Ｌ」→「1L」）を吸収します。カナの統一は、意味が変わる可能性がわずかに残るため、同一性の判定には使いません。

## 項目を追加するときの商品の解決

1. 入力名から `normalizedName` を求め、`productId = id(normalizedName)` を計算します。
2. 同じIDの product があればそれを使います（統合されていれば、`ProductGraph` で統合先を解決します）。なければ、バッチで product と item を作成します。
3. 項目の `productId` は、**入力名に対応する product**（統合元）を指します。統合先への置き換えは読み取り時に行います。

## 表記ゆれの紐付け

意味の違う商品を誤ってまとめないよう、自動では確定しません。

- **入力時**: 商品詳細入力画面（新規）で、入力名が新しい product で、かつ `searchKey` が前方一致・部分一致する既存の商品がある場合、「『牛乳』と同じ商品として記録する」チップを表示します。初期状態は未選択です。選択した場合だけ、新しい product を `mergedInto = 牛乳の統合先` で作成します。Enter と候補の「＋」による即追加では表示しません（連続入力を止めないため）。
- **あとから**: 設定の「商品の統合」画面（一覧・統合・統合の解除だけの最小構成）で、商品を選んで「別の商品に統合」または「統合を解除」できます。過去に別々の商品として記録された履歴をまとめるために必要です。
- 「ぎゅうにゅう」と「牛乳」のように読みでしか結び付かないものは、機械的には検出できません。この場合は、あとからの統合で対応します。

### ProductGraph

- `resolve(productId): ProductId` は `mergedInto` をたどって統合先を返します。たどる深さは10までとし、循環を見つけたら自分自身を返します（同時に統合操作が行われた場合の保険）。
- 統合するときは、クライアントで統合先を解決してから、その統合先のIDを書き込みます（ルールでも、統合先が未統合であることを確認します）。

## パラメータ

MVP では定数とし、UI からは変更しません。

```kotlin
data class SuggestionParams(
    val minEvents: Int = 3,              // 周期予測を始める購入回数（購入日の数）
    val maxIntervals: Int = 10,          // 中央値に使う直近の間隔数
    val dueRatio: Double = 0.8,          // 未入力時サジェストの閾値
    val staleRatio: Double = 3.0,        // これを超えたら「買うのをやめた」とみなして出さない
    val earlyRatio: Double = 0.5,        // 早すぎる再購入の閾値
    val maxDispersion: Double = 0.5,     // MAD/中央値 がこれを超えたら周期が不安定
    val minProfileSize: Int = 5,         // 類似度を計算するリストの最小商品数
    val minIntersection: Int = 3,
    val minOverlap: Double = 0.5,        // 重複係数の閾値
    val maxDue: Int = 10,
    val maxCompletions: Int = 8,
)
```

## 周期推定

```kotlin
data class CycleEstimate(
    val eventDates: List<LocalDate>,   // 日付単位でまとめた購入日（昇順）
    val medianIntervalDays: Double,
    val dispersion: Double,            // 中央絶対偏差 / 中央値
) { val lastPurchase: LocalDate get() = eventDates.last() }

fun estimateCycle(purchaseDates: List<LocalDate>, params: SuggestionParams): CycleEstimate?
fun elapsedRatio(estimate: CycleEstimate, today: LocalDate): Double
```

1. 入力は、統合先が同じ product の `active` な購入のうち自分に見えるもの（共有と、自分のプライベート）の `checkedAt` を、端末のタイムゾーンで `LocalDate` に変換したものです。`purchasedAt` ではなく `checkedAt` を使うのは、保留チェックの確定が日をまたいでも、実際にチェックした日を購入日にするためです。家族全体・全リストの履歴を合算します（場所によって周期を分断しない）。
2. **同じ日に複数回買った場合は、1回の購入として数えます。** 家族2人が同じ日に買った、まとめ買いを分けて登録した、などで間隔0が生じて中央値が歪むのを防ぐためです。要件の「購入3回以上」は「購入した日が3日以上」と解釈します。
3. 購入日が `minEvents` 未満なら `null` を返します。
4. 隣り合う購入日の間隔（日数）を求め、直近 `maxIntervals` 個の中央値を推定周期とします（個数が偶数なら中央2値の平均）。
5. `elapsedRatio = (today - lastPurchase の日数) / medianIntervalDays`

購入履歴は直近365日分だけを取得するため（[data-model.md](data-model.md#購入履歴の取得範囲365日)）、周期がおよそ180日を超える商品は予測されません。

## リスト類似度

```kotlin
// listKey = リスト名を NameNormalizer.normalizedName と同じ方法で正規化したもの
fun buildProfiles(purchases: List<PurchaseFact>): Map<ListKey, Set<ProductId>>   // 統合先IDで集計
fun overlap(a: Set<ProductId>, b: Set<ProductId>, p: SuggestionParams): Double?  // null = 判定できない
fun relevance(target: ListKey, profiles: Map<ListKey, Set<ProductId>>, p: SuggestionParams): Map<ProductId, Double>
```

- `overlap` には **重複係数**（|A∩B| / min(|A|,|B|)）を使います。大型スーパーと小さな店のように、リストごとの商品数が大きく違っても、Jaccard 係数のように不当に低くならないためです。少ない件数で過大評価しないよう、`|A|,|B| ≥ minProfileSize` かつ `|A∩B| ≥ minIntersection` を満たさなければ `null`（関連付けない）にします。
- `relevance`: 同じ名前のリストで買ったことがある商品は1.0。それ以外は、類似度が `minOverlap` 以上のリストで買った商品に、その類似度（複数ある場合は最大値）を付けます。どれにも当たらなければ候補にしません。
- **周期の計算には全リストの履歴を使い、そのリストで提示する候補を選ぶときだけ類似度を使います。**

## 未入力時のサジェスト

`SuggestionEngine.due`:

1. 統合先の product ごとに `estimateCycle` を計算します。
2. `dueRatio ≤ ratio ≤ staleRatio` の商品に絞ります。`staleRatio` を設けるのは、「経過率の高い順」だけだと、買うのをやめた商品が常に先頭に来てしまうためです。
3. 家族の **いずれかのリスト** の未購入項目にすでにある商品（統合先で比較）を除きます。
4. `relevance(現在のリスト) > 0` の商品に絞ります。現在のリストと同じ名前の履歴も、類似するリストもない場合は **何も出さず**、「商品名を入力すると候補が出ます」とだけ表示します（場所の推定を過信しない）。
5. `ratio` の大きい順、同じなら `relevance` の大きい順に並べ、最大 `maxDue` 件にします。
6. 各候補には「約14日ごと・最後は16日前」程度の補足を表示します。

## 入力中の補完

`SuggestionEngine.complete`:

- 対象は、自分に見える項目か購入の記録がある product（統合元も含む）の `displayName` です。他のメンバーがプライベートでだけ使った商品名を出さないためです（[data-model.md](data-model.md#設計判断)）。`searchKey` の前方一致を部分一致より優先し、次に直近365日の購入回数、次に `relevance` で並べます。**周期の条件は使いません。**
- 候補には統合元の表記をそのまま表示し、選択されたらその product を使います（統合先は読み取り時に解決）。
- 未購入リストにすでにある商品には「追加済み」ラベルを付けます。追加はできます（追加するたびに新しい項目を作る）。
- 早すぎる再購入（下記）に当たる商品には、補足として「前回 10/18 に購入・いつもは約14日ごと」を表示します。
- 各候補には、「＋」と商品詳細入力画面で使う前回の数量・単位（その product の直近の購入）を持たせます。

## 早すぎる再購入の検知

```kotlin
data class EarlyPurchaseNotice(val lastPurchase: LocalDate, val typicalIntervalDays: Int)
fun detect(estimate: CycleEstimate?, today: LocalDate, p: SuggestionParams): EarlyPurchaseNotice?
```

- 推定が `null`（購入日が3日未満）、または `dispersion > maxDispersion`（周期が不安定）なら `null` を返します。
- `ratio < earlyRatio` のときに通知を返します。補完候補の補足と商品詳細入力画面に「前回 10/18 に購入・いつもは約14日ごと」と控えめに表示するだけで、追加の操作は妨げません。

## 備考の候補

商品詳細入力画面（新規）では、同じ product（統合先で比較）の自分に見える購入の記録から、空でない `memo` を新しい順に重複を除いて最大3件、候補のチップにします。

## 計算の実行場所

`combine(products, purchases, openItems, currentList)` の Flow で入力をまとめ（どれも自分に見えるものだけを購読している）、`Dispatchers.Default` で再計算します。商品ごとの `CycleEstimate` は、購入データが変わったときだけ計算し直します。日付が変わったときも再計算します。
