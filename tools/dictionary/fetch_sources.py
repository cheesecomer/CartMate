"""名寄せ辞書のソースを、公式の URL から取得する（dictionary/SOURCES.md）。

取得したファイルは tools/dictionary/.cache/ に置き（リポジトリには含めない）、
取得した日時・URL・SHA-256 を dictionary/sources/manifest.csv に記録する。
標準ライブラリだけを使う。

    python3 tools/dictionary/fetch_sources.py
"""

import csv
import datetime
import hashlib
import pathlib
import sys
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[2]
CACHE = ROOT / "tools" / "dictionary" / ".cache"
MANIFEST = ROOT / "dictionary" / "sources" / "manifest.csv"

# 取得するソース。名前は review.py が使う
SOURCES = {
    # 日本食品標準成分表（八訂）増補2023年 本表 第2章（文部科学省）
    "mext_foods.xlsx": "https://www.mext.go.jp/content/20260327-mxt_kagsei-mext-000029402_02.xlsx",
    # 家計調査収支項目分類（2020年1月改定、総務省統計局、e-Stat）。検索語なしで全項目を CSV で得る
    "estat_kakei.csv": (
        "https://www.e-stat.go.jp/term/download?bKbn=70&kaiteiCode=03&charset=UTF-8&bom=0"
        "&searchMethod=keyword&searchWord=&komokuSearchFlg=1&info1SearchFlg=1&info2SearchFlg=1"
        "&info3SearchFlg=&info4SearchFlg=&info5SearchFlg=&info6SearchFlg="
    ),
}


def fetch(name: str, url: str) -> dict:
    request = urllib.request.Request(url, headers={"User-Agent": "cartmate-dictionary-build"})
    with urllib.request.urlopen(request, timeout=60) as response:
        if response.status != 200:
            raise RuntimeError(f"{name}: HTTP {response.status}")
        data = response.read()
    if len(data) < 1024:
        # 取得に失敗したのに小さなエラーページを保存しないように
        raise RuntimeError(f"{name}: too small ({len(data)} bytes)")
    (CACHE / name).write_bytes(data)
    return {
        "name": name,
        "url": url,
        "fetched_at": datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="seconds"),
        "bytes": str(len(data)),
        "sha256": hashlib.sha256(data).hexdigest(),
    }


def main() -> int:
    CACHE.mkdir(parents=True, exist_ok=True)
    rows = []
    for name, url in SOURCES.items():
        row = fetch(name, url)
        print(f"{name}: {row['bytes']} bytes sha256={row['sha256'][:16]}")
        rows.append(row)
    MANIFEST.parent.mkdir(parents=True, exist_ok=True)
    with MANIFEST.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=["name", "url", "fetched_at", "bytes", "sha256"])
        writer.writeheader()
        writer.writerows(rows)
    return 0


if __name__ == "__main__":
    sys.exit(main())
