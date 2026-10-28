# 名寄せ辞書

アプリに同梱する名寄せ辞書の元データです（[docs/design/suggestions.md](../docs/design/suggestions.md#名寄せ辞書)）。アプリのアセットに含め、起動後に読み込みます。照合は端末の中だけで行います。

## ファイル

| ファイル | 列 | 内容 |
|---|---|---|
| `VERSION` | | 辞書の版。中身を変えたら上げる |
| `items.csv` | `canonical_id,name,source` | 品目。名前は前方一致の別名としても使う |
| `aliases.csv` | `alias,canonical_id,match,source` | 別名。`match` は `exact`（完全一致だけ）、`prefix`（購入履歴の前方一致の照合にも使う）、`default`（上書きできる既定の同義語。お茶→緑茶。予測とサジェストの集計でだけ、品目の語がなければまとめる） |
| `cycle_groups.csv` | `cycle_group_id,canonical_id` | 購入周期だけ合わせて数えるグループ。品目とは別の ID を使う |
| `never_merge.csv` | `left_id,right_id,reason` | 自動では同じ商品にしない品目の組 |
| `retired_ids.csv` | `canonical_id,retired_in,reason` | 使わなくなった品目の ID。再利用しない（家族の訂正がずれるため） |
| `attributes.csv` | `term,kind,value,source` | 商品名と備考から属性を取り出す語。`kind` は `item`（品目。値は品目の ID）、`form`、`use`、`variant`、`ignore`（判定に使わない語）。容量は規則で読むので書かない |
| `tests/cases.csv` | `expect,left,right` | 名寄せの正例・負例。`expect` は `equivalent`、`cycle_group`、`never_merge`、`distinct`、`not_prefix` |
| `SOURCES.md` | | データソースとライセンスの調査 |
| `sources/manifest.csv` | | 取得したソースの URL・日時・SHA-256 |
| `review/` | | 外部データから作った確認用の一覧。辞書には入らない |

- 値にカンマは使いません。
- 別名は、アプリの `NameNormalizer.normalizedName`（NFKC、空白の整理、英字の小文字化）で正規化して照合します。正規化だけで一致する表記ゆれ（全角・半角など）は、登録しなくてよいです。
- ひらがな・カタカナだけの2文字以下の別名は `exact` にします（「す」が「れたす」に当たるのを避けるため）。
- `source` には出典を書きます。利用許諾と再配布の条件を確かめられない外部のデータは取り込みません。手作業で作った行は `cartmate-manual` です。

## 辞書の目的と作り方

- 目的は、購入履歴に登録された商品の名寄せです。商品のカタログではないので、件数は目標にしません。実際の買い物で出る表記ゆれ（漢字・かな、送り仮名、単位の書き方）と、誤統合しやすい組（牛乳／豆乳、シャンプー／コンディショナーなど）を優先して足します。
- 外部データ（成分表、家計調査）は語彙を見つける参考資料です。次の手順で確認用の一覧を作り、人が見て手で足します。

```
python3 tools/dictionary/fetch_sources.py   # 公式の URL から取得（tools/dictionary/.cache/。コミットしない）
python3 tools/dictionary/review.py          # dictionary/review/ に確認用の一覧を作る
```

## 検証

`./gradlew :domain:test` で、この元データを検証します（`DictionaryFilesTest`）。別名の重複、正規化したあとの衝突、存在しない品目への参照、使わなくなった ID の再利用、短いかなの別名の照合の条件、属性の語を調べ、`tests/cases.csv` の正例・負例がすべて期待どおりかを確かめます。
