# ドキュメント

| 資料 | 内容 |
|---|---|
| [requirements.md](requirements.md) | MVP の要件。システムがどう振る舞うか |
| [design/architecture.md](design/architecture.md) | 技術スタックとモジュール構成 |
| [design/data-model.md](design/data-model.md) | Firestore のデータ構造と購読の範囲 |
| [design/security.md](design/security.md) | Security Rules、招待（コード・リンク）、App Check |
| [design/sync.md](design/sync.md) | チェック/Undo の冪等性、保留チェック、オフラインの扱い |
| [design/ui.md](design/ui.md) | 見た目の基準、画面構成、入力の状態遷移、通知、スワイプ |
| [design-tokens.md](design-tokens.md) | 配色・文字・余白・角丸の値（Compose への対応付け） |
| [implementation-spec.md](implementation-spec.md) | 画面ごとのデザイン仕様（承認済みの最終版） |
| [ui-design-decisions.md](ui-design-decisions.md) | デザインの決定事項と、変更の経緯 |
| [design/screens/](design/screens/) | 画面のモック（HTML と画像）。見た目の参考 |
| [design/DESIGN.md](design/DESIGN.md) | デザインシステムの元の定義（生成ツールの出力。値は design-tokens.md を優先） |
| [design/suggestions.md](design/suggestions.md) | 商品の同一性、購入周期の推定、リスト類似度、サジェスト |
| [testing.md](testing.md) | テスト戦略 |
| [roadmap.md](roadmap.md) | 実装フェーズ |

振る舞いを変える場合は、まず requirements.md を更新し、それから該当する設計資料を更新します。デザインの資料どうしで食い違う場合の優先順位は [design/ui.md](design/ui.md#見た目の基準) にあります。

`stitch-prompt-*.md` は、デザインを検討したときの生成ツールへの指示文です。仕様としては参照しません。`stitch-prompt-lists-redesign.md` と `stitch-prompt-detail-redesign.md` は前提が古い（グリーン、3案の比較）ものです。`stitch-prompt-family.md` と `stitch-prompt-family-final.md` は、フェーズ2の家族とアカウントの画面をデザインし、最終稿として書き出すための指示文です。
