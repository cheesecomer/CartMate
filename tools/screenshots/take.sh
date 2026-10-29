#!/usr/bin/env bash
# アプリの画面のスクリーンショットを、テストデータを入れた状態で撮る（docs/testing.md「スクリーンショット」）。
#
#   tools/screenshots/take.sh [出力先]       # 既定の出力先は docs/screenshots（リポジトリに入れ、README とストアの掲載に使う）
#
# 1. Firebase のエミュレータ（Auth と Firestore）を起動する（起動済みならそれを使う）
# 2. テストデータを入れる（tools/testdata/seed.py --showcase。前に入れたデータは消える）
# 3. エミュレータにつなぐデバッグ版とテストを Android Emulator に入れ、アプリのデータを消す
# 4. ScreenshotTour でアプリを操作して各画面を撮り、出力先を置き換える（1080×1920）。
#    失敗したときは出力先を変えず、撮れた分と失敗したときの画面（failed.png）を build/screenshots に残す
#
# 端末は ANDROID_SERIAL で選ぶ（既定は最初の Android Emulator）。アプリのデータを消して入れ替えるので、実機では動かさない。
# 同時には1つしか実行しない（実行中なら、すぐに終わる）。
# 撮ったあとも、エミュレータにつなぐデバッグ版が入ったまま。戻すときは ./gradlew :app:installDebug
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="${1:-$ROOT/docs/screenshots}"
WORK="$ROOT/build/screenshots"
PROJECT="cartmate-cheesecomer"
PACKAGE="com.cheesecomer.cartmate"
DEVICE_DIR="/sdcard/Android/data/$PACKAGE/files/screenshots"

if ! command -v adb >/dev/null; then
  SDK="${ANDROID_HOME:-$(sed -n 's/^sdk\.dir=//p' "$ROOT/local.properties" 2>/dev/null)}"
  PATH="$SDK/platform-tools:$PATH"
fi

export ANDROID_SERIAL="${ANDROID_SERIAL:-$(adb devices | awk '/^emulator-[0-9]+\tdevice$/ { print $1; exit }')}"
if [[ "$ANDROID_SERIAL" != emulator-* ]]; then
  echo "Android Emulator が見つからない（起動するか、ANDROID_SERIAL で指定する）" >&2
  exit 1
fi

# 同時に実行すると、テストデータの入れ直しとアプリの入れ替えで、互いの撮影を壊す（どちらも失敗し、原因も分かりにくい）。
# Firebase のエミュレータと端末は、どのチェックアウトから実行しても共有なので、ロックは共通の場所に置く
LOCK="/tmp/cartmate-take-screenshots.lock"
if ! mkdir "$LOCK" 2>/dev/null; then
  holder="$(cat "$LOCK/pid" 2>/dev/null || true)"
  if [[ -n "$holder" ]] && kill -0 "$holder" 2>/dev/null; then
    echo "別の take.sh（pid ${holder}）が実行中。終わってから実行する" >&2
    exit 1
  fi
  # 前の実行が強制終了などでロックを残した
  rm -rf "$LOCK"
  mkdir "$LOCK" || { echo "ロックを取れない（$LOCK）" >&2; exit 1; }
fi
echo $$ >"$LOCK/pid"

mkdir -p "$OUT" "$WORK"
cleanup() {
  rm -rf "$LOCK"
  adb shell am broadcast -a com.android.systemui.demo -e command exit >/dev/null 2>&1 || true
  adb shell wm size reset >/dev/null 2>&1 || true
  [[ -n "${FIREBASE_PID:-}" ]] && kill "$FIREBASE_PID" 2>/dev/null || true
  if [[ -n "${CAPTIVE_PORTAL_MODE:-}" ]]; then
    if [[ "$CAPTIVE_PORTAL_MODE" == null ]]; then
      adb shell settings delete global captive_portal_mode >/dev/null 2>&1 || true
    else
      adb shell settings put global captive_portal_mode "$CAPTIVE_PORTAL_MODE" >/dev/null 2>&1 || true
    fi
  fi
}
trap cleanup EXIT

# 1. Firebase のエミュレータ
if ! nc -z 127.0.0.1 8085 2>/dev/null || ! nc -z 127.0.0.1 9099 2>/dev/null; then
  echo "Firebase のエミュレータを起動する（ログは $WORK/firebase.log）"
  (cd "$ROOT/firebase" && exec npx --prefix rules-test firebase emulators:start --only firestore,auth --project "$PROJECT") \
    >"$WORK/firebase.log" 2>&1 &
  FIREBASE_PID=$!
  for _ in $(seq 60); do
    nc -z 127.0.0.1 8085 2>/dev/null && nc -z 127.0.0.1 9099 2>/dev/null && break
    kill -0 "$FIREBASE_PID" 2>/dev/null || { echo "Firebase のエミュレータが起動しない（$WORK/firebase.log）" >&2; exit 1; }
    sleep 1
  done
fi

# 2. テストデータ
python3 "$ROOT/tools/testdata/seed.py" --showcase --project "$PROJECT"

# 3. アプリとテスト。前の起動のログインとキャッシュを残さない
(cd "$ROOT" && ./gradlew -q :app:installDebug :app:installDebugAndroidTest -PfirebaseEmulator)
adb shell pm clear "$PACKAGE" >/dev/null

# オンラインの表示で撮る。アプリは、接続を確認できたネットワーク（VALIDATED）がないとオフラインと表示する。
# Android Emulator の DNS が使えなくなっている（PC のネットワークが変わったあとなど）と確認できないが、Firebase の
# エミュレータには 10.0.2.2 で直接つながるので、接続の確認（キャプティブポータルの検出）を止めてつなぎ直す。終わったら戻す
validated() { adb shell dumpsys connectivity | grep -o "Capabilities: [A-Z_&]*" | grep -q "&VALIDATED&"; }
if ! validated; then
  CAPTIVE_PORTAL_MODE="$(adb shell settings get global captive_portal_mode | tr -d '\r')"
  adb shell settings put global captive_portal_mode 0
  adb shell cmd connectivity airplane-mode enable
  sleep 2
  adb shell cmd connectivity airplane-mode disable
  for _ in $(seq 90); do validated && break; sleep 1; done
  validated || { echo "端末のネットワークがオンラインにならない" >&2; exit 1; }
fi

# Google Play の掲載用の比率（9:16。長辺が短辺の2倍を超える画像は載せられない）で撮る。終わったら戻す
adb shell wm size 1080x1920

# ステータスバーの時刻・電池・電波を固定し、通知を隠す（デモモード）
adb shell settings put global sysui_demo_allowed 1
demo() { adb shell am broadcast -a com.android.systemui.demo -e command "$@" >/dev/null; }
demo enter
demo clock -e hhmm 1000
demo battery -e level 100 -e plugged false
demo network -e wifi show -e level 4 -e fully true -e mobile hide
demo notifications -e visible false

# 4. 撮って取り出す。am instrument はテストが失敗しても 0 で終わるので、結果の行で判定する。
# 失敗したときも、そこまでに撮った画面と、失敗したときの画面（failed.png）を取り出す
result="$(adb shell am instrument -w -e screenshots true -e class "$PACKAGE.screenshots.ScreenshotTour" \
  "$PACKAGE.test/androidx.test.runner.AndroidJUnitRunner")"
rm -rf "$WORK"/*.png
adb pull "$DEVICE_DIR/." "$WORK" >/dev/null 2>&1 || true
if ! grep -q "^OK (1 test)" <<<"$result"; then
  echo "$result" >&2
  echo "スクリーンショットのテストが失敗した（$WORK/failed.png に失敗したときの画面）" >&2
  exit 1
fi
count="$(find "$WORK" -name '*.png' | wc -l | tr -d ' ')"
if [[ "$count" == 0 ]]; then
  echo "スクリーンショットが撮れていない（テストが飛ばされた可能性がある: -PfirebaseEmulator でビルドしたか）" >&2
  exit 1
fi
find "$OUT" -name '*.png' -delete
cp "$WORK"/*.png "$OUT"
echo "$count 枚を $OUT に保存した"
