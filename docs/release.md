# リリースの手順（Play Console）

内部テストのトラックにリリース版を出し、Firestore で App Check を強制するまでの手順です（[ロードマップ](roadmap.md) フェーズ5）。コンソールでの操作は手作業で、リポジトリには鍵もパスワードも含めません。

## アップロード鍵

Play App Signing を使います。Play に届くアプリはアップロード鍵で署名し、ストアから配る版は Google が管理するアプリ署名鍵で署名し直します。アップロード鍵をなくしても、Play Console から再設定できます。

1. 鍵をリポジトリの外に作ります。パスワードはパスワードマネージャーに保存し、鍵のファイルも一緒にバックアップします。

   ```sh
   mkdir -p ~/.android/cartmate
   keytool -genkeypair -v -keystore ~/.android/cartmate/cartmate.jks -alias cartmate \
     -keyalg RSA -keysize 2048 -validity 10000
   ```

2. リポジトリのルートに `keystore.properties` を作ります（`.gitignore` 済み）。

   ```properties
   storeFile=/Users/<ユーザー名>/.android/cartmate/cartmate.jks
   storePassword=<鍵ストアのパスワード>
   keyAlias=cartmate
   keyPassword=<鍵のパスワード>
   ```

   今の keytool が作る PKCS12 形式では、鍵のパスワードはキーストアのパスワードと同じになります（別にするには `-storetype JKS` で作る）。

   `keystore.properties` があればリリース版をこの鍵で署名し、なければ未署名のままビルドします（CI はこちら）。

3. ビルドします。Play にアップロードするたびに `app/build.gradle.kts` の `versionCode` を1つ上げます。

   ```sh
   ./gradlew :app:bundleRelease
   # app/build/outputs/bundle/release/app-release.aab
   ```

## Play Console への登録

1. アプリを作成します（アプリ名「CartMate」、デフォルトの言語は日本語、アプリ、無料）。パッケージ名は最初のアップロードで `com.cheesecomer.cartmate` に決まります。
2. 「テスト > 内部テスト」でテスターのリスト（家族の Google アカウント）を作り、`app-release.aab` をアップロードしてリリースします。Play App Signing は、最初のアップロードで有効になります。
3. 「アプリの完全性 > アプリの署名」で、アプリ署名鍵とアップロード鍵の SHA-1・SHA-256 を控えます。

## 署名鍵の指紋の登録

ストアから入れた版はアプリ署名鍵で署名されているため、デバッグ用の鍵の指紋だけでは Google ログインと App Links が通りません。

| 登録先 | 登録するもの | ないと起きること |
|---|---|---|
| Firebase コンソールのプロジェクトの設定（Android アプリ） | アプリ署名鍵とアップロード鍵の SHA-1・SHA-256 | Google でログインできない |
| `firebase/hosting/.well-known/assetlinks.json` | アプリ署名鍵とアップロード鍵の SHA-256（デバッグ用は残す） | 招待リンクでアプリが開かず、ブラウザのページになる |

`assetlinks.json` を直したら `firebase deploy --only hosting`（`firebase/` で実行）で公開します。Firebase に指紋を足したら、念のため `google-services.json` を取り直して `app/` に置きます。

## アプリのコンテンツ

Play Console の「ポリシーとプログラム > アプリのコンテンツ」で申告します。

| 項目 | 回答 |
|---|---|
| プライバシーポリシー | `https://cartmate.cheesecomer.com/privacy`（`firebase/hosting/privacy/index.html`） |
| 広告 | 含まない |
| アプリのアクセス権 | 一部の機能は制限されている。「任意の Google アカウントでログインできます。ログイン後に『家族と共有する』を選ぶと、すべての機能を使えます」 |
| コンテンツのレーティング | 質問票に答える（暴力・性的表現・ギャンブルなどはすべて「いいえ」。ユーザー間のやり取りは、家族の中でリストを共有するだけ） |
| ターゲット ユーザー | 18歳以上（子ども向けに作っておらず、ファミリー ポリシーの追加の要件を避けるため） |
| アカウントの削除 | アプリ内で削除できる。URL は `https://cartmate.cheesecomer.com/account-deletion` |
| データセーフティ | 次の節 |

## データセーフティ

アプリの依存は Firebase Authentication・Cloud Firestore・App Check（Play Integrity）だけで、広告・解析・Firebase Installations は入っていません（`./gradlew :app:dependencies --configuration releaseRuntimeClasspath` で確かめる）。

- データを収集する: はい。第三者と共有する: いいえ（Firebase は処理の委託先で、共有に当たらない）。
- 送信中のデータの暗号化: はい（HTTPS）。
- データの削除をリクエストできる: はい（アプリ内と、アカウント削除の案内ページ）。

| データの種類 | 収集するもの | 必須か | 目的 |
|---|---|---|---|
| 個人情報 > 名前 | Google アカウントの名前、家族での表示名 | 必須 | アプリの機能、アカウント管理 |
| 個人情報 > メールアドレス | Google アカウントのメールアドレス（Firebase Authentication） | 必須 | アカウント管理 |
| 個人情報 > ユーザー ID | Firebase Authentication の ID | 必須 | アプリの機能、アカウント管理、不正行為の防止 |
| 金融情報 > 購入履歴 | チェックした商品と日時（購入の記録） | 必須 | アプリの機能、カスタマイズ（買う時期の提案） |
| アプリのアクティビティ > その他のユーザー作成コンテンツ | リスト名、商品名、数量・単位・備考、呼び名 | 必須 | アプリの機能 |

申告しないと判断したもの:

- 写真: プロフィール画像は保存せず、Google の画像の URL を家族のメンバー情報に持って表示するだけのため。
- おおよその位置情報: Firebase Authentication は IP アドレスを不正防止に使うが、位置の推定には使わないため。
- デバイスまたはその他の ID: Firebase Installations を含まず、Play Integrity のトークンは端末の識別子ではないため。

取得する情報を変えたら、プライバシーポリシーと一緒に直します。フェーズ6で商品名を外部の AI API に送るようになったら、両方に加えます。

## App Check の強制

[security.md](design/security.md#app-check) のとおり、Play Integrity を Firebase とつないでから強制します。

1. Play Console の「アプリの完全性 > Play Integrity API」で、Firebase プロジェクト（`cartmate-cheesecomer`）の Google Cloud プロジェクトをリンクします。
2. Firebase コンソールの App Check で、Android アプリに Play Integrity を登録します（アプリ署名鍵の SHA-256。プロジェクトの設定に登録済みなら選ぶだけ）。
3. 開発に使う端末のデバッグ版のトークンを、App Check の「アプリ」タブで Android アプリの「デバッグ トークンを管理」に登録します。トークンは、デバッグ版を起動すると Logcat に出ます（タグは完全なクラス名なので、文字列で探す）。

   ```sh
   adb logcat -d | grep 'App Check debug token'
   ```

   本番につなぐデバッグ版は実機だけにします。Android Emulator は Firebase のエミュレータ（`-PfirebaseEmulator`）につなぐ用途に限り、トークンは登録しません。

   トークンは端末ごとに、アプリのデータに保存されます。上から入れ直しても変わりませんが、アンインストールやデータの消去で新しくなるので、そのときは登録し直します。Play から入れた版はアプリ署名鍵で署名されていて、デバッグ版を上から入れられないため、先にアンインストールします。
4. 内部テストから入れた版を使い、App Check の指標で Firestore へのリクエストが「確認済み」になっていることを確かめます。「確認されていない」リクエストが残っていれば、その出どころ（未登録のデバッグ版など）を先になくします。
5. App Check の「API」タブで Cloud Firestore の行を開き、「適用」を押します。反映には最大で15分ほどかかります。強制するのは Cloud Firestore だけです（Authentication などはそのまま）。

強制したあとは、トークンを登録していないデバッグ版は Firestore を読み書きできません。Firebase のエミュレータ（rules-test、`-PfirebaseEmulator`）は App Check を求めないので、影響を受けません。

## 完了の確認

- 内部テストのリンクからリリース版を入れ、Google でログインできる。
- App Check を強制した状態で、リストの作成・項目の追加・チェック・Undo が反映され、もう1台の端末にも届く。
- 招待リンクをタップするとアプリが開く（App Links）。
- リリース版は R8 で縮小しているため、上の操作で落ちないことも合わせて確かめる。
