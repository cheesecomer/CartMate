"""外部データ（dictionary/SOURCES.md）から、名寄せの候補となる語彙の確認用の一覧を作る。

外部データは語彙を見つけるための参考資料で、辞書には自動で取り込まない（docs/design/suggestions.md「名寄せ辞書」）。
辞書の行は、この一覧を見て人が dictionary/*.csv に手で足す。

入力:  tools/dictionary/.cache/（fetch_sources.py が取得したもの）、dictionary/items.csv・aliases.csv
出力:  dictionary/review/estat_kakei.csv   家計調査の品目と、辞書の名前に一致するか
       dictionary/review/mext_foods.csv    成分表の食品名の基本の名前と、辞書の名前に一致するか

標準ライブラリだけを使う（Python 3.9 以上）。

    python3 tools/dictionary/fetch_sources.py
    python3 tools/dictionary/review.py
"""

import collections
import csv
import pathlib
import re
import sys
import unicodedata
import xml.etree.ElementTree as ET
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[2]
CACHE = ROOT / "tools" / "dictionary" / ".cache"
DICT = ROOT / "dictionary"
REVIEW = DICT / "review"

# 家計調査で見る範囲（符号）: 食料（外食を除く）、家事用消耗品、医薬品・保健医療用品、理美容用品
KAKEI_RANGES = [("102", "38Y"), ("3X1", "3XX"), ("530", "539"), ("700", "711"), ("713", "713"), ("901", "915")]
NS = {"m": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}


def normalized_name(s):
    """アプリの NameNormalizer.normalizedName と同じ（NFKC、空白の整理、英字の小文字化）"""
    return " ".join(unicodedata.normalize("NFKC", s).split()).lower()


def dictionary_names():
    owner = {}
    with (DICT / "items.csv").open(encoding="utf-8", newline="") as f:
        for row in csv.DictReader(f):
            owner[normalized_name(row["name"])] = row["canonical_id"]
    with (DICT / "aliases.csv").open(encoding="utf-8", newline="") as f:
        for row in csv.DictReader(f):
            owner[normalized_name(row["alias"])] = row["canonical_id"]
    return owner


def write_csv(path, fieldnames, rows):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames, lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)


def kakei_rows(owner):
    with (CACHE / "estat_kakei.csv").open(encoding="utf-8", newline="") as f:
        rows = list(csv.reader(f))
    out = []
    group = ""
    for row in rows[2:]:
        if len(row) < 3:
            continue
        name = re.sub(r"^\S+\s+", "", row[1]).strip()
        code = row[2].strip()
        if code.startswith("("):
            group = name
            continue
        if re.fullmatch(r"[0-9A-Z]{3}", code) and any(tuple(lo) <= tuple(code) <= tuple(hi) for lo, hi in KAKEI_RANGES):
            out.append({"code": code, "name": name, "group": group, "in_dictionary": owner.get(normalized_name(name), "")})
    return out


def mext_rows(owner):
    z = zipfile.ZipFile(CACHE / "mext_foods.xlsx")
    sst = ["".join(t.text or "" for t in si.iter("{%s}t" % NS["m"]))
           for si in ET.fromstring(z.read("xl/sharedStrings.xml")).findall("m:si", NS)]
    sheet = ET.fromstring(z.read("xl/worksheets/sheet1.xml"))
    by_base = collections.OrderedDict()
    for r in sheet.find("m:sheetData", NS):
        cells = {}
        for c in r.findall("m:c", NS):
            v = c.find("m:v", NS)
            value = v.text if v is not None else ""
            if c.get("t") == "s" and value:
                value = sst[int(value)]
            cells[re.sub(r"\d", "", c.get("r"))] = value
        group, number, name = cells.get("A", ""), cells.get("B", ""), " ".join(cells.get("D", "").split())
        if not (re.fullmatch(r"\d{2}", group) and re.fullmatch(r"\d{5}", number) and name):
            continue
        # 「こめ　［水稲穀粒］　精白米」→「こめ」
        parts = [p for p in name.split(" ") if p and not p.startswith(("［", "＜", "（", "("))]
        base = parts[0] if parts else name
        entry = by_base.setdefault(base, {"base_name": base, "group": group, "count": 0, "examples": []})
        entry["count"] += 1
        if len(entry["examples"]) < 3:
            entry["examples"].append(name)
    return [{**e, "examples": " / ".join(e["examples"]), "in_dictionary": owner.get(normalized_name(e["base_name"]), "")}
            for e in by_base.values()]


def main():
    if not (CACHE / "estat_kakei.csv").exists() or not (CACHE / "mext_foods.xlsx").exists():
        print("ソースがない。先に tools/dictionary/fetch_sources.py を実行する", file=sys.stderr)
        return 1
    owner = dictionary_names()
    kakei = kakei_rows(owner)
    mext = mext_rows(owner)
    write_csv(REVIEW / "estat_kakei.csv", ["code", "name", "group", "in_dictionary"], kakei)
    write_csv(REVIEW / "mext_foods.csv", ["base_name", "group", "count", "examples", "in_dictionary"], mext)
    print(f"家計調査: {len(kakei)} 品目（辞書にある名前 {sum(1 for r in kakei if r['in_dictionary'])}）")
    print(f"成分表: 基本の名前 {len(mext)}（辞書にある名前 {sum(1 for r in mext if r['in_dictionary'])}）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
