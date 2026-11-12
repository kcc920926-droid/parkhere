#!/bin/sh
# drive.olvend.com 첫 페이지 배포본을 만든다. 서비스는 건드리지 않는다.
# 첫 페이지(site/public)에 웹 프로토타입 시연 두 개(legacy/drive-watcher의 판단 엔진, 개인 경향 모델)를 함께 묶는다.
# 옛 첫 페이지와 Pleos 화면 미리보기(console.html)는 넣지 않는다.
# 사용: site/tools/prepare-release.sh   (배포 순서는 site/README.md)
set -eu
SITE="$(cd "$(dirname "$0")/.." && pwd)"
LEGACY="$SITE/../legacy/drive-watcher"
DEPLOY="${DRIVEWATCH_SITE_DEPLOY:-$HOME/.local/share/drive-watcher}"
DEMOS="engine-demo.html profile-demo.html PLAYBOOK.MD style.css hud.css profile.css
  engine.js replays.js app.js course.js trip-store.js road.js engine-audio.js habits.js
  cockpit.js can-source.js telemetry.js pleos-demo.js profile-model.js profile-cache.js profile-demo.js"

RELEASE="$DEPLOY/releases/$(date -u +%Y%m%dT%H%M%SZ)-site-$$"
mkdir -p "$RELEASE/site/assets"
for f in $DEMOS; do cp "$LEGACY/$f" "$RELEASE/site/"; done
cp "$SITE/public/index.html" "$SITE/public/home.css" "$SITE/public/home.js" "$RELEASE/site/"
cp "$SITE/public/assets/"* "$RELEASE/site/assets/"
cp "$SITE/origin.cjs" "$RELEASE/origin.cjs"

# 첫 페이지와 시연 페이지가 가리키는 같은 서버 안의 파일이 모두 있는지 확인한다(/api/, /family/는 가족 서버 몫).
missing=0
for page in index.html engine-demo.html profile-demo.html; do
  for ref in $(grep -oE '(href|src)="[^"#:]+"' "$RELEASE/site/$page" | sed -E 's/^(href|src)="//; s/"$//' | sort -u); do
    case "$ref" in /api/*|/family/*|./) continue ;; esac
    [ -f "$RELEASE/site/${ref#/}" ] || { echo "없는 파일: $page → $ref" >&2; missing=1; }
  done
done
[ "$missing" -eq 0 ] || { rm -rf "$RELEASE"; exit 1; }

(cd "$RELEASE" && find site origin.cjs -type f | sort | xargs shasum -a 256 > SHA256SUMS)
chmod -R go-rwx "$RELEASE"
echo "$RELEASE"
