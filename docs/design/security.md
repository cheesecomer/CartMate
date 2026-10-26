# セキュリティ

## Security Rules の方針

- 家族の領域（`households/{hid}` 以下）のドキュメントは、`members/{request.auth.uid}` が存在するときだけ読み書きを許可する。
- **公開範囲**: `visibility == "private"` のリスト・項目は `createdBy`、購入履歴は `purchasedBy` が本人のときだけ読み書きを許可する。プライベートのリストには、本人がプライベートの項目だけを追加できる。`visibility` はあとから変更できない（[要件](../requirements.md) §3.4）。
- **状態遷移をルールで強制する**。項目の open→completed は、同じ書き込みの中で `purchases/{itemId}` が `active == true` になる場合に限って許可する（`getAfter`）。逆方向（Undo）も同じ扱いにする。クライアントにバグがあっても、「チェック済みなのに購入履歴がない」「Undo したのに履歴が有効なまま」という状態は作れない。
- サーバー時刻を強制する（`createdAt/completedAt/purchasedAt == request.time`）。購入日時の改ざんや、端末の時計のずれを防ぐ。端末時刻の `checkedAt` だけは、許容範囲（未来5分〜過去7日）を検証して受け付ける（[sync.md](sync.md#保留チェック)）。
- フィールドを `keys().hasOnly(...)` で制限し、型と長さを検証する。
- 編集: 未購入の項目の商品名・数量・単位・備考と、リストの名前・備考だけを変更できる。チェック済みの項目は、購入の記録と食い違わないよう変更できない。
- 削除: 家族・購入履歴・商品は削除できない。リストは論理削除のみ。物理削除できるのは未購入の項目だけ（誤って追加したものの取り消し。[ui.md](ui.md#スワイプ操作)）。例外として、アカウントの削除では本人のプライベートなリスト・項目・購入の記録を物理削除できる。
- 招待コードは `get`（IDを指定した読み取り）だけを許可し、一覧の取得は同じ家族のメンバーに限る。参加処理は、「members の作成」と「invite の usedCount +1」が同じバッチに含まれる場合だけ成立させる。

## ルール（ドラフト）

> 実装前のドラフトです。実装後は `firebase/firestore.rules` を正とし、この節は削除して方針だけを残します。

```
rules_version = '2';
service cloud.firestore {
  match /databases/{db}/documents {

    function signedIn() { return request.auth != null; }
    function me() { return request.auth.uid; }
    function hhPath(hid) { return /databases/$(db)/documents/households/$(hid); }
    function memberPath(hid, uid) { return /databases/$(db)/documents/households/$(hid)/members/$(uid); }
    function invitePath(code) { return /databases/$(db)/documents/invites/$(code); }
    function isMember(hid) { return signedIn() && exists(memberPath(hid, me())); }
    function isOwner(hid) { return isMember(hid) && get(memberPath(hid, me())).data.role == 'owner'; }
    function changed() { return request.resource.data.diff(resource.data).affectedKeys(); }
    function str(v, max) { return v is string && v.size() > 0 && v.size() <= max; }
    function optStr(v, max) { return v == null || str(v, max); }
    function validVisibility(v) { return v in ['shared', 'private']; }
    // 公開範囲。owner は lists/items では createdBy、purchases では purchasedBy
    function canSee(d, owner) { return d.visibility == 'shared' || owner == me(); }

    match /users/{uid} {
      allow read: if signedIn() && me() == uid;
      allow create, update: if signedIn() && me() == uid
        && request.resource.data.keys().hasOnly(['householdId', 'updatedAt'])
        && (request.resource.data.householdId == null
            || existsAfter(memberPath(request.resource.data.householdId, uid)));
      allow delete: if signedIn() && me() == uid;   // アカウントの削除
    }

    match /invites/{code} {
      allow get: if signedIn();
      allow list: if isMember(resource.data.householdId);
      allow create: if isMember(request.resource.data.householdId)
        && code.size() >= 10
        && request.resource.data.keys().hasOnly(['householdId','createdBy','inviterName','createdAt','expiresAt','maxUses','usedCount'])
        && request.resource.data.createdBy == me()
        && str(request.resource.data.inviterName, 60)
        && request.resource.data.createdAt == request.time
        && request.resource.data.expiresAt <= request.time + duration.value(24, 'h')
        && request.resource.data.maxUses == 1
        && request.resource.data.usedCount == 0;
      // 参加時の消費。members 作成と同じバッチ内でのみ成立する
      allow update: if signedIn()
        && changed().hasOnly(['usedCount'])
        && request.resource.data.usedCount == resource.data.usedCount + 1
        && resource.data.usedCount < resource.data.maxUses
        && request.time < resource.data.expiresAt
        && !exists(memberPath(resource.data.householdId, me()))
        && existsAfter(memberPath(resource.data.householdId, me()));
      allow delete: if isMember(resource.data.householdId);
    }

    match /households/{hid} {
      allow read: if isMember(hid);
      allow create: if signedIn()
        && request.resource.data.keys().hasOnly(['name','createdBy','createdAt'])
        && str(request.resource.data.name, 40)
        && request.resource.data.createdBy == me()
        && request.resource.data.createdAt == request.time
        && getAfter(memberPath(hid, me())).data.role == 'owner';
      allow update, delete: if false;

      function isFounding() {
        return !exists(hhPath(hid))
          && getAfter(hhPath(hid)).data.createdBy == me()
          && request.resource.data.role == 'owner'
          && request.resource.data.joinedVia == null;
      }
      function isJoiningByInvite() {
        let code = request.resource.data.joinedVia;
        let inv = get(invitePath(code)).data;
        return request.resource.data.role == 'member'
          && inv.householdId == hid
          && getAfter(invitePath(code)).data.usedCount == inv.usedCount + 1;
      }

      match /members/{uid} {
        allow read: if isMember(hid);
        allow create: if signedIn() && uid == me()
          && request.resource.data.keys().hasOnly(['role','displayName','photoUrl','joinedAt','joinedVia'])
          && request.resource.data.joinedAt == request.time
          && (isFounding() || isJoiningByInvite());
        // オーナーの引き継ぎ。オーナーが自分の members を消すのと同じバッチでだけ成立する
        allow update: if isOwner(hid) && uid != me()
          && changed().hasOnly(['role'])
          && resource.data.role == 'member' && request.resource.data.role == 'owner'
          && !existsAfter(memberPath(hid, me()));
        allow delete: if (isOwner(hid) && uid != me())   // オーナーによるメンバー削除
          || (isMember(hid) && uid == me());             // 離脱・アカウントの削除
      }

      match /lists/{listId} {
        allow read: if isMember(hid) && canSee(resource.data, resource.data.createdBy);
        allow create: if isMember(hid)
          && request.resource.data.keys().hasOnly(['name','memo','visibility','sortKey','createdBy','createdAt','status'])
          && request.resource.data.sortKey is number
          && str(request.resource.data.name, 40)
          && optStr(request.resource.data.memo, 60)
          && validVisibility(request.resource.data.visibility)
          && request.resource.data.createdBy == me()
          && request.resource.data.createdAt == request.time
          && request.resource.data.status == 'active';
        allow update: if isMember(hid) && canSee(resource.data, resource.data.createdBy)
          && resource.data.status == 'active' && (
          // 論理削除
          (changed().hasOnly(['status','deletedAt','deletedBy'])
            && request.resource.data.status == 'deleted'
            && request.resource.data.deletedBy == me()
            && request.resource.data.deletedAt == request.time)
          // リスト名と備考の変更。購入の記録の listNameSnapshot は書き換えない
          || (changed().hasOnly(['name','memo'])
              && str(request.resource.data.name, 40) && optStr(request.resource.data.memo, 60))
          // 並び替え
          || (changed().hasOnly(['sortKey']) && request.resource.data.sortKey is number)
        );
        allow delete: if isMember(hid) && isOwnPrivate(resource.data, resource.data.createdBy);   // アカウントの削除
        allow create: if isImport('lists', listId, request.resource.data.createdBy, ['sortKey']);   // 合流
      }

      function isOwnPrivate(d, owner) { return d.visibility == 'private' && owner == me(); }
      // 合流: コピー元の家族にある同じIDのドキュメントと、extra 以外が同じ内容なら作成できる
      function isImport(coll, id, owner, extra) {
        let d = request.resource.data;
        let src = d.importedFrom;
        return isMember(hid) && src is string && src != hid && isMember(src)
          && canSee(d, owner)
          && d.diff(get(/databases/$(db)/documents/households/$(src)/$(coll)/$(id)).data)
               .affectedKeys().hasOnly(['importedFrom'].concat(extra));
      }
      function purchasePath(id) { return /databases/$(db)/documents/households/$(hid)/purchases/$(id); }
      function itemPath(id) { return /databases/$(db)/documents/households/$(hid)/items/$(id); }
      function listPath(id) { return /databases/$(db)/documents/households/$(hid)/lists/$(id); }

      match /items/{itemId} {
        allow read: if isMember(hid) && canSee(resource.data, resource.data.createdBy);
        allow create: if isImport('items', itemId, request.resource.data.createdBy, []);   // 合流
        allow create: if isMember(hid)
          && request.resource.data.keys().hasOnly(['listId','productId','inputName','sortKey','quantity','unit','memo',
                'visibility','status','createdBy','createdAt','completedAt','completedBy'])
          && str(request.resource.data.inputName, 60)
          && optStr(request.resource.data.memo, 100)
          && validVisibility(request.resource.data.visibility)
          && canAddToList(get(listPath(request.resource.data.listId)).data, request.resource.data.visibility)
          && request.resource.data.sortKey is number
          && (request.resource.data.quantity == null
              || (request.resource.data.quantity is number
                  && request.resource.data.quantity > 0 && request.resource.data.quantity <= 9999))
          && optStr(request.resource.data.unit, 10)
          && request.resource.data.status == 'open'
          && request.resource.data.completedAt == null
          && request.resource.data.completedBy == null
          && request.resource.data.createdBy == me()
          && request.resource.data.createdAt == request.time
          && existsAfter(/databases/$(db)/documents/households/$(hid)/products/$(request.resource.data.productId));
        allow update: if isMember(hid) && canSee(resource.data, resource.data.createdBy)
          && (isCompleting() || isReopening() || isEditing() || isReordering());
        allow delete: if isMember(hid) && canSee(resource.data, resource.data.createdBy)
          && (resource.data.status == 'open'                              // 誤って追加した項目の削除
              || isOwnPrivate(resource.data, resource.data.createdBy));  // アカウントの削除

        // 共有のリストには共有・プライベートのどちらも、プライベートのリストには本人がプライベートだけを追加できる
        function canAddToList(list, visibility) {
          return list.visibility == 'shared'
            || (list.createdBy == me() && visibility == 'private');
        }

        function isCompleting() {
          let p = getAfter(purchasePath(itemId)).data;
          return resource.data.status == 'open'
            && request.resource.data.status == 'completed'
            && changed().hasOnly(['status','completedAt','completedBy'])
            && request.resource.data.completedBy == me()
            && request.resource.data.completedAt == request.time
            && p.active == true && p.purchasedAt == request.time;
        }
        // 未購入の項目の商品名・数量・単位・備考の変更。チェック済みの項目は変更できない
        function isEditing() {
          return resource.data.status == 'open'
            && changed().hasOnly(['inputName','quantity','unit','memo'])
            && optStr(request.resource.data.memo, 100)
            && str(request.resource.data.inputName, 60)
            && (request.resource.data.quantity == null
                || (request.resource.data.quantity is number
                    && request.resource.data.quantity > 0 && request.resource.data.quantity <= 9999))
            && optStr(request.resource.data.unit, 10);
        }
        // 並び替え。未購入・チェック済みのどちらも動かせる
        function isReordering() {
          return changed().hasOnly(['sortKey']) && request.resource.data.sortKey is number;
        }
        function isReopening() {
          return resource.data.status == 'completed'
            && request.resource.data.status == 'open'
            && changed().hasOnly(['status','completedAt','completedBy'])
            && request.resource.data.completedAt == null
            && request.resource.data.completedBy == null
            && getAfter(purchasePath(itemId)).data.active == false;
        }
      }

      match /purchases/{pid} {
        allow read: if isMember(hid) && canSee(resource.data, resource.data.purchasedBy);
        allow create: if isImport('purchases', pid, request.resource.data.purchasedBy, []);   // 合流（フェーズ3）
        allow create: if isMember(hid) && isRecording();
        allow update: if isMember(hid) && canSee(resource.data, resource.data.purchasedBy)
          && ((resource.data.active == false && isRecording()) || isRevoking());
        allow delete: if isMember(hid) && isOwnPrivate(resource.data, resource.data.purchasedBy);   // アカウントの削除

        // 購入完了時（初回、またはUndo後の再完了）。スナップショット値は item と list に一致させる
        function isRecording() {
          let d = request.resource.data;
          let item = getAfter(itemPath(pid)).data;
          return d.keys().hasOnly(['itemId','listId','listNameSnapshot','productId','inputName','quantity','unit','memo',
                   'visibility','checkedAt','purchasedAt','purchasedBy','active','revokedAt','revokedBy'])
            && d.itemId == pid && d.active == true
            && d.purchasedBy == me() && d.purchasedAt == request.time
            // 端末時刻。時計のずれを5分まで許容し、7日より古い保留は受け付けない
            && d.checkedAt is timestamp
            && d.checkedAt <= request.time + duration.value(5, 'm')
            && d.checkedAt >= request.time - duration.value(7, 'd')
            && d.revokedAt == null && d.revokedBy == null
            && item.status == 'completed' && item.completedAt == request.time
            && d.productId == item.productId && d.listId == item.listId
            && d.inputName == item.inputName && d.quantity == item.quantity && d.unit == item.unit
            && d.memo == item.memo && d.visibility == item.visibility
            && d.listNameSnapshot == get(/databases/$(db)/documents/households/$(hid)/lists/$(item.listId)).data.name;
        }
        function isRevoking() {
          return resource.data.active == true
            && request.resource.data.active == false
            && changed().hasOnly(['active','revokedAt','revokedBy'])
            && request.resource.data.revokedBy == me()
            && request.resource.data.revokedAt == request.time
            && getAfter(itemPath(pid)).data.status == 'open';
        }
      }

      match /products/{pid} {
        allow read: if isMember(hid);
        allow create: if isImport('products', pid, me(), []);   // 合流
        allow create: if isMember(hid)
          && request.resource.data.keys().hasOnly(['displayName','normalizedName','searchKey','createdBy','createdAt','mergedInto'])
          && str(request.resource.data.displayName, 60)
          && str(request.resource.data.normalizedName, 60)
          && str(request.resource.data.searchKey, 60)
          && request.resource.data.mergedInto == null;
        allow update: if isMember(hid) && (isRecreate() || isMerge() || isUnmerge());
        allow delete: if false;

        // 2端末が同じ決定的IDで同時に作成した場合の上書きを許す（正規化名は不変）
        function isRecreate() {
          return request.resource.data.normalizedName == resource.data.normalizedName
            && changed().hasOnly(['displayName','searchKey','createdBy','createdAt']);
        }
        function isMerge() {
          let target = request.resource.data.mergedInto;
          return changed().hasOnly(['mergedInto','mergedAt','mergedBy'])
            && target is string && target != pid
            && get(/databases/$(db)/documents/households/$(hid)/products/$(target)).data.mergedInto == null
            && request.resource.data.mergedBy == me()
            && request.resource.data.mergedAt == request.time;
        }
        function isUnmerge() {
          return changed().hasOnly(['mergedInto','mergedAt','mergedBy'])
            && request.resource.data.mergedInto == null;
        }
      }
    }
  }
}
```

1回の書き込みで評価される `get/exists` は、購入完了のトランザクションで最大4件程度です（上限は20件）。

オーナーの引き継ぎで「参加がいちばん早いメンバー」を選ぶことは、ルールでは検証できません（コレクションを数えたり並べたりできないため）。ルールが保証するのは「オーナーが抜けるバッチでだけ、ほかのメンバーをオーナーにできる」ことまでで、相手の選び方はクライアントに任せます。オーナーが残りのメンバーを引き継がずに抜けることもルールでは防げませんが、その場合もメンバー削除ができなくなるだけで、データは守られます。

プライベートな項目は、作った本人しか読めない（＝チェックできない）ため、その購入履歴の `purchasedBy` は必ず項目を作った本人になります。そのため、購入履歴の公開範囲の判定には `purchasedBy` を使えます。

## 招待

### 招待コード

- 10文字、Crockford Base32（読み間違えやすい文字を含まない）。表示するときは `7KQ2-M9XP-HA` のように区切る。
- 有効期限は24時間、使えるのは1回だけ。1人につき1つ発行する。
- 招待を発行できるのは家族のメンバー全員。発行したコードは、同じ家族のメンバーなら取り消せる。
- 招待には発行した人の表示名（`inviterName`）を写す。参加前のユーザーは `members` を読めないため、参加の確認画面の「○○さんの家族」はここから表示する。

### 招待リンク（App Links）

リンクならタップするだけで参加できるため、コードと併用します。コードは、アプリが未インストールのときなどの予備として残します。

- **URL**: `https://cartmate.cheesecomer.com/join/{code}`
- 招待を発行すると、リンクとコードの両方を含む文面を共有シートで送ります。
  - 例: 「CartMate の家族に招待されました。https://cartmate.cheesecomer.com/join/7KQ2M9XPHA （コード: 7KQ2-M9XP-HA、24時間有効）」
- **アプリがインストール済みの場合**: App Links（`autoVerify`）でアプリが直接開きます。参加確認の画面（「○○さんの家族に参加しますか？」）を表示し、承認すると参加処理を実行します。未ログインなら、ログインしてからこの画面に戻ります。すでに家族に参加している場合は、参加せずにその旨を表示します。
- **アプリが未インストールの場合**: Firebase Hosting の静的なフォールバックページを表示します。ストアへの案内と、「アプリを入れたらこのコードを入力してください」というコードを表示します。Play Install Referrer でコードを引き継ぐ方法は使いません（ストアで公開する前は機能しないため）。
- **リンクの安全性**: リンクを開いただけでは参加しません（必ず確認画面を挟む）。LINE などのリンクプレビューが URL を読み込んでも、コードは消費されません。コードの安全性（推測しにくさ、期限、使用回数）は、コードを直接入力する場合と同じです。

**ホスティング**: サブドメイン `cartmate.cheesecomer.com` を Firebase Hosting に割り当てます。`assetlinks.json` とフォールバックページを `firebase/` 配下で一緒に管理でき、デプロイも Firebase CLI だけで済みます。`cheesecomer.com` 本体のサイト構成にも影響しません。

| ファイル | 内容 |
|---|---|
| `firebase/hosting/.well-known/assetlinks.json` | パッケージ名と署名証明書の SHA-256 フィンガープリント（デバッグ用、アップロード鍵、Play App Signing の鍵） |
| `firebase/hosting/join/index.html` | フォールバックページ（`/join/**` をこのページに rewrite する） |
| `firebase/hosting/account-deletion/index.html` | アカウント削除の案内ページ（Play Console のデータ削除の URL に登録する） |

## メンバー管理

- オーナーは、自分以外のメンバーを家族から削除できます。招待コードが意図しない人に渡った場合の、最低限の対処です。
- メンバーは自分で家族から抜けられます。オーナーが抜けるときは、`joinedAt` がいちばん早いメンバーにオーナーを引き継ぎます（同じバッチ）。引き継ぐ相手を選んでの譲渡は MVP の対象外です。

## ほかの家族への合流

家族に自分しかいない人が、ほかの家族に移る機能です（[要件](../requirements.md) §3.5）。

- 手順: ①招待コードで合流先に参加する（通常の参加と同じバッチ。`users/{uid}.householdId` も合流先にする） → ②コピー元の products → lists → items → purchases の順に、同じIDで合流先に写す（`importedFrom` を付ける） → ③元の家族の自分の `members` を削除する。
- 「自分しかいない」ことはルールでは確かめられないため、クライアントで `members` の件数を見て判定します。ほかのメンバーがいる家族から写しても、写せるのは本人がもともと読めるデータだけなので、データは漏れません。
- ①のあとで中断した場合、`users.householdId` はすでに合流先なので、起動時に元の家族の `members` が残っていれば②から再開します。すでに写したドキュメントは飛ばします。
- ルールの `get` は1回の書き込み（バッチ）で20件までです。合流では、2つの家族のメンバー確認（2件）とコピー元の読み取り（1件ずつ）を使うので、1つのバッチで写せるのは18件までです。購入の記録が多いと書き込みの回数が増えるため、進み具合を表示します。
- 購入の記録を写す処理は、Firestore に購入の記録を書くフェーズ3で加えます。

## アカウントの削除

Google Play は、アカウントを作るアプリに、アプリ内とアプリ外（Web）の両方から削除できる手段を求めます。

- アプリ内: 家族画面の「アカウントを削除」。Firebase Auth の削除には直近のログインが必要なため、先に Google で再ログインしてもらいます。
- 削除の順番: ①本人のプライベートなリスト・項目・購入の記録を削除 → ②離脱（オーナーなら引き継ぎ） → ③`users/{uid}` を削除 → ④Firebase Auth のユーザーを削除。途中で失敗した場合は、もう一度実行すれば続きから削除できるようにします（どの手順も、すでに消えていれば何もしない）。
- Cloud Functions は使わず、アプリから削除します（専用サーバーの運用は対象外。[要件](../requirements.md) §8）。
- アプリ外: Hosting の案内ページに、アプリからの削除手順と、アプリを使えない場合の連絡先を載せます。

## App Check

Firebase App Check（Play Integrity）を導入します。招待コードの総当たりや、改造したクライアントからのアクセスを、少ない手間で抑えられます。

## リポジトリに含めないもの

- `google-services.json`: 秘密情報ではありませんが、公開リポジトリには含めず、各自の Firebase プロジェクトから取得します。
- 署名鍵（keystore）とそのパスワード。
