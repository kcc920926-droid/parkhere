#!/bin/sh
# 차량 앱 APK를 가족 서버에 게시한다: APP_DIR에 DriveWatch.apk와 latest.json을 둔다.
# 사용: tools/publish-apk.sh <apk 경로> ["바뀐 점 한 줄"]
# APP_DIR 기본값은 배포 폴더(~/.local/share/drive-watcher/family/app). 서버의 LaunchAgent에도 같은 APP_DIR을 준다.
set -eu
APK_IN="$1"; NOTES="${2:-}"
APP_DIR="${APP_DIR:-$HOME/.local/share/drive-watcher/family/app}"
BT="$(ls -d "${ANDROID_HOME:-$HOME/Library/Android/sdk}"/build-tools/* | sort -V | tail -1)"
BADGING="$("$BT/aapt2" dump badging "$APK_IN" | head -1)"
CODE="$(echo "$BADGING" | sed -E "s/.*versionCode='([0-9]+)'.*/\1/")"
NAME="$(echo "$BADGING" | sed -E "s/.*versionName='([^']+)'.*/\1/")"
COMMIT="$(echo "$NAME" | sed -nE 's/.*\+([0-9a-f]{7,}).*/\1/p')"
mkdir -p "$APP_DIR"
cp "$APK_IN" "$APP_DIR/DriveWatch.apk.tmp" && mv "$APP_DIR/DriveWatch.apk.tmp" "$APP_DIR/DriveWatch.apk"
SHA="$(shasum -a 256 "$APP_DIR/DriveWatch.apk" | cut -d' ' -f1)"
SIZE="$(wc -c < "$APP_DIR/DriveWatch.apk" | tr -d ' ')"
python3 - "$APP_DIR/latest.json" "$CODE" "$NAME" "$COMMIT" "$NOTES" "$SHA" "$SIZE" <<'PY'
import json, sys, datetime
path, code, name, commit, notes, sha, size = sys.argv[1:]
json.dump({"versionCode": int(code), "versionName": name, "commit": commit or None, "notes": notes, "sha256": sha, "size": int(size),
           "publishedAt": datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")},
          open(path, "w"), ensure_ascii=False, indent=1)
PY
echo "게시함: $NAME (versionCode $CODE) → $APP_DIR"
