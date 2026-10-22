# ドキュメント

| 資料 | 内容 |
|---|---|
| [requirements.md](requirements.md) | MVP の要件。システムがどう振る舞うか |
| [design/architecture.md](design/architecture.md) | 技術スタックとモジュール構成 |
| [design/data-model.md](design/data-model.md) | Firestore のデータ構造と購読の範囲 |
| [design/security.md](design/security.md) | Security Rules、招待（コード・リンク）、App Check |
| [design/sync.md](design/sync.md) | チェック/Undo の冪等性、保留チェック、オフラインの扱い |
| [design/ui.md](design/ui.md) | 画面構成、入力の状態遷移、通知、スワイプ |
| [design/suggestions.md](design/suggestions.md) | 商品の同一性、購入周期の推定、リスト類似度、サジェスト |
| [testing.md](testing.md) | テスト戦略 |
| [roadmap.md](roadmap.md) | 実装フェーズ |
| [proposals/visibility.md](proposals/visibility.md) | 追加要件: 公開範囲（確認待ち） |

振る舞いを変える場合は、まず requirements.md を更新し、それから該当する設計資料を更新します。
