#!/bin/sh
# 테스트를 돌리고, 배포용 스냅샷을 만든 뒤 그 경로를 출력한다. 서비스는 건드리지 않는다.
# 사용: tools/prepare-release.sh   (배포 순서는 README.md의 "배포" 참고)
set -eu
SOURCE="$(cd "$(dirname "$0")/.." && pwd)"
DEPLOY="${DRIVEWATCH_FAMILY_DEPLOY:-$HOME/.local/share/drive-watcher/family}"
cd "$SOURCE"
node --test test/*.test.js >/dev/null || { echo "테스트 실패: 배포를 중단합니다" >&2; exit 1; }
RELEASE="$DEPLOY/releases/$(date -u +%Y%m%dT%H%M%SZ)-$$"
mkdir -p "$RELEASE/app/public" "$RELEASE/app/demo"
cp server.js store.js sms.js demo.js release.js openapi.json package.json "$RELEASE/app/"
cp public/* "$RELEASE/app/public/"
cp -R demo/drives demo/histories "$RELEASE/app/demo/"
(cd "$RELEASE" && find app -type f | sort | xargs shasum -a 256 > SHA256SUMS)
chmod -R go-rwx "$RELEASE"
echo "$RELEASE"
