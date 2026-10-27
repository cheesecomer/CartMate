# 名寄せ辞書

アプリに同梱する名寄せ辞書の元データです（[docs/design/suggestions.md](../docs/design/suggestions.md#名寄せ辞書)）。アプリのアセットに含め、起動後に読み込みます。照合は端末の中だけで行います。

## ファイル

| ファイル | 列 | 内容 |
|---|---|---|
| `VERSION` | | 辞書の版。中身を変えたら上げる |
| `items.csv` | `canonical_id,name,source` | 品目。名前は前方一致の別名としても使う |
| `aliases.csv` | `alias,canonical_id,match,source` | 別名。`match` は `exact`（完全一致だけ）か `prefix`（補完の前方一致にも使う） |
| `cycle_groups.csv` | `cycle_group_id,canonical_id` | 購入周期だけ合わせて数えるグループ。品目とは別の ID を使う |
| `never_merge.csv` | `left_id,right_id,reason` | 自動では同じ商品にしない品目の組 |
| `retired_ids.csv` | `canonical_id,retired_in,reason` | 使わなくなった品目の ID。再利用しない（家族の訂正がずれるため） |

- 値にカンマは使いません。
- 別名は、アプリの `NameNormalizer.normalizedName`（NFKC、空白の整理、英字の小文字化）で正規化して照合します。正規化だけで一致する表記ゆれ（全角・半角など）は、登録しなくてよいです。
- ひらがな・カタカナだけの2文字以下の別名は `exact` にします（「す」が「れたす」に当たるのを避けるため）。
- `source` には出典を書きます。利用許諾と再配布の条件を確かめられない外部のデータは取り込みません。手作業で作った行は `cartmate-manual` です。

## 検証

`./gradlew :domain:test` で、この元データを検証します（`DictionaryFilesTest`）。別名の重複、正規化したあとの衝突、存在しない品目への参照、使わなくなった ID の再利用、短いかなの別名の照合の条件を調べます。
