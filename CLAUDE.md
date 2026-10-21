# CLAUDE.md

CartMate（家族共有の買い物リスト Android アプリ）の開発で、Claude Code が従う作業方針です。

## ドキュメント

- 要件は [docs/requirements.md](docs/requirements.md)、設計は [docs/design/](docs/design/) にあります。目次は [docs/README.md](docs/README.md)。
- requirements.md だけを読めばシステムの振る舞いが分かる状態を保つ。振る舞いに関わる設計を決めたら、requirements.md にも反映する。
- 実装が設計資料と食い違う場合は、黙って実装を変えずに、どちらを正とするか確認する。
- ドキュメントは日本語で書く。

## 進め方

- [docs/roadmap.md](docs/roadmap.md) のフェーズ単位で実装し、各フェーズの終わりでレビューを受けてから次に進む。
- 要件にない機能を勝手に追加しない。必要だと思う場合は、推奨案と理由を示して確認する。
- 要件で決まっていない細部は、推奨案と理由を明示して決める。

## 実装の原則

- 予測・類似度・正規化・入力状態の遷移などのロジックは、`domain` モジュールの純粋関数として実装し、JVM の単体テストで検証する。
- 時刻は `Clock` を注入して扱う。
- Firestore の Security Rules を変更したら、rules-test も更新する。

## リポジトリに含めないもの

- `google-services.json`、署名鍵（keystore）とそのパスワード。
