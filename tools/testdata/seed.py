"""Firebase のエミュレータ（Auth と Firestore）に、予測を画面で確かめるためのテストデータを入れる（docs/testing.md「テストデータ」）。

過去の日付の購入の記録は、ルール（購入日時はサーバーの時刻）があるので、アプリからも本番にも入れられない。
エミュレータに、ルールを通さずに（管理者として）書く。本番のデータには触れない。

入れるもの:
  - テスト用ユーザー taro@example.com（パスワード cartmate-test、表示名 山田 太郎）と、その家族
  - リスト「スーパー」「薬局」と、そこで過去に買った商品の購入の記録（今日を基準にした日付）

標準ライブラリだけを使う（Python 3.9 以上）。

    cd firebase && npx --prefix rules-test firebase emulators:start --only firestore,auth --project cartmate-cheesecomer
    python3 tools/testdata/seed.py            # 別のターミナルで
"""

import argparse
import datetime
import hashlib
import json
import re
import sys
import unicodedata
import urllib.error
import urllib.request

EMAIL = "taro@example.com"
PASSWORD = "cartmate-test"
DISPLAY_NAME = "山田 太郎"
HOUSEHOLD_ID = "test-household"

# 商品名, リスト, 数量, 単位, 備考, 購入日（今日から何日前か）
# 経過率 = 最後の購入からの日数 / 周期。時期が近い商品に出るのは 0.8 以上 3.0 以下（docs/requirements.md §4.3）
PURCHASES = [
    # 7日ごと・最後は6日前（経過率 0.86）→ スーパーで時期が近い商品に出る
    ("牛乳", "スーパー", 1, "本", None, [20, 13, 6]),
    # 4日ごと・最後は4日前（1.0）→ 出る
    ("食パン", "スーパー", 1, "袋", None, [12, 8, 4]),
    # 購入が2日だけ → 周期を予測しない。--third で3回目（9日前）を足すと、10日ごと・最後は9日前（0.9）で出る
    ("卵", "スーパー", 1, "パック", None, [29, 19]),
    # 備考の容量で分けて数える。9日ごと・最後は9日前（1.0）→「麦茶（2L）」として出る
    ("麦茶", "スーパー", 1, "本", "2L", [27, 18, 9]),
    # 30日ごと・最後は10日前（0.33）→ 出ない。入力すると早すぎる再購入の注意が出る
    ("緑茶", "スーパー", 1, "袋", "茶葉", [70, 40, 10]),
    # 20日ごと・最後は70日前（3.5）→ 買うのをやめたとみなして出ない
    ("ティッシュペーパー", "スーパー", 1, "箱", None, [110, 90, 70]),
    # 薬局だけで買う。30日ごと・最後は25日前（0.83）→ 薬局で出る（スーパーでは関連がないので出ない）
    ("洗濯用洗剤", "薬局", 1, "本", "詰め替え", [85, 55, 25]),
    # 40日ごと・最後は20日前（0.5）→ 出ない
    ("歯磨き粉", "薬局", 1, "本", None, [100, 60, 20]),
]

# --third で足す、卵の3回目の購入（3回目の購入から予測が出ることを確かめる）
THIRD = ("卵", "スーパー", 1, "パック", None, [9])

LISTS = [("super", "スーパー", 1.0), ("drugstore", "薬局", 2.0)]


def normalized_name(name):
    """アプリの NameNormalizer.normalizedName と同じ"""
    return re.sub(r"\s+", " ", unicodedata.normalize("NFKC", name).strip()).lower()


def search_key(name):
    """アプリの NameNormalizer.searchKey と同じ（カタカナをひらがなにし、文字と数字だけを残す）"""
    normalized = normalized_name(name)
    key = "".join(
        chr(ord(c) - (ord("ァ") - ord("ぁ"))) if "ァ" <= c <= "ヶ" else c
        for c in normalized
        if c.isalnum()
    )
    return key or normalized


def product_id(name):
    """アプリの NameNormalizer.productId と同じ"""
    return "n_" + hashlib.sha256(normalized_name(name).encode("utf-8")).digest()[:16].hex()


def value(v):
    """Firestore の REST API の値"""
    if v is None:
        return {"nullValue": None}
    if isinstance(v, bool):
        return {"booleanValue": v}
    if isinstance(v, (int, float)):
        return {"doubleValue": float(v)}
    if isinstance(v, datetime.datetime):
        return {"timestampValue": v.astimezone(datetime.timezone.utc).isoformat().replace("+00:00", "Z")}
    return {"stringValue": str(v)}


def request(url, body, headers=None):
    req = urllib.request.Request(
        url,
        data=json.dumps(body).encode("utf-8"),
        headers={"Content-Type": "application/json", **(headers or {})},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=10) as response:
            return json.loads(response.read() or b"{}")
    except urllib.error.HTTPError as error:
        return {"error": json.loads(error.read() or b"{}").get("error", {})}


def sign_up(host):
    """テスト用ユーザーを作る。すでにあれば、ログインして ID を得る"""
    base = f"http://{host}:9099/identitytoolkit.googleapis.com/v1"
    body = {"email": EMAIL, "password": PASSWORD, "returnSecureToken": True}
    result = request(f"{base}/accounts:signUp?key=fake-api-key", {**body, "displayName": DISPLAY_NAME})
    if "error" in result:
        result = request(f"{base}/accounts:signInWithPassword?key=fake-api-key", body)
    if "localId" not in result:
        sys.exit(f"Auth エミュレータにユーザーを作れない: {result}")
    return result["localId"]


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--host", default="127.0.0.1", help="エミュレータのホスト（このスクリプトを動かす PC から見たもの）")
    parser.add_argument("--project", default="cartmate-cheesecomer", help="エミュレータを起動したプロジェクト ID")
    parser.add_argument("--third", action="store_true", help="卵の3回目の購入だけを足す（最初の投入のあとで実行する）")
    args = parser.parse_args()

    uid = sign_up(args.host)
    if not args.third:
        # 最初の投入では、前に入れたデータを残さない（エミュレータの Firestore を空にする。本番には関係しない）
        clear = urllib.request.Request(
            f"http://{args.host}:8085/emulator/v1/projects/{args.project}/databases/(default)/documents", method="DELETE",
        )
        urllib.request.urlopen(clear, timeout=10).close()
    now = datetime.datetime.now().astimezone()
    noon = now.replace(hour=12, minute=0, second=0, microsecond=0)
    root = f"projects/{args.project}/databases/(default)/documents"
    writes = []

    def put(path, fields):
        writes.append({"update": {"name": f"{root}/{path}", "fields": {k: value(v) for k, v in fields.items()}}})

    household = f"households/{HOUSEHOLD_ID}"
    put(household, {"name": f"{DISPLAY_NAME}さんの家族", "createdBy": uid, "createdAt": now})
    put(f"{household}/members/{uid}", {
        "role": "owner", "displayName": DISPLAY_NAME, "photoUrl": None, "joinedAt": now, "joinedVia": None,
    })
    put(f"users/{uid}", {"householdId": HOUSEHOLD_ID, "updatedAt": now})
    list_ids = {}
    for list_id, name, sort_key in LISTS:
        list_ids[name] = list_id
        put(f"{household}/lists/{list_id}", {
            "name": name, "memo": None, "visibility": "shared", "sortKey": sort_key,
            "createdBy": uid, "createdAt": now - datetime.timedelta(days=120), "status": "active",
        })

    count = 0
    for name, list_name, quantity, unit, memo, days in ([THIRD] if args.third else PURCHASES):
        pid = product_id(name)
        first = noon - datetime.timedelta(days=max(days))
        put(f"{household}/products/{pid}", {
            "displayName": unicodedata.normalize("NFKC", name).strip(), "normalizedName": normalized_name(name),
            "searchKey": search_key(name), "createdBy": uid, "createdAt": first, "mergedInto": None,
        })
        for day in days:
            at = noon - datetime.timedelta(days=day)
            item_id = f"seed-{pid[2:10]}-{day}"
            put(f"{household}/items/{item_id}", {
                "listId": list_ids[list_name], "productId": pid, "inputName": name, "sortKey": float(day),
                "quantity": quantity, "unit": unit, "memo": memo, "visibility": "shared", "status": "completed",
                "createdBy": uid, "createdAt": at, "completedAt": at, "completedBy": uid, "checkedAt": at,
            })
            put(f"{household}/purchases/{item_id}", {
                "itemId": item_id, "listId": list_ids[list_name], "listNameSnapshot": list_name, "productId": pid,
                "inputName": name, "quantity": quantity, "unit": unit, "memo": memo, "visibility": "shared",
                "checkedAt": at, "purchasedAt": at, "purchasedBy": uid, "active": True,
                "revokedAt": None, "revokedBy": None,
            })
            count += 1

    # 管理者として書く（ルールを通さない）。エミュレータだけが受け付ける
    result = request(
        f"http://{args.host}:8085/v1/{root}:commit",
        {"writes": writes},
        headers={"Authorization": "Bearer owner"},
    )
    if "error" in result:
        sys.exit(f"Firestore エミュレータに書けない: {result['error']}")
    print(f"ユーザー {EMAIL}（{uid}）の家族に、リスト {len(LISTS)} 件・商品 {len(PURCHASES)} 件・購入の記録 {count} 件を入れた")
    return 0


if __name__ == "__main__":
    sys.exit(main())
