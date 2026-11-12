/* 차량 앱의 최신 빌드. 폴더(APP_DIR)에 tools/publish-apk.sh가 DriveWatch.apk와 latest.json을 둔다.
   앱은 latest.json의 versionCode가 자기보다 크면 "새 버전" 알림과 내려받기 QR을 띄운다.
   앱이 스스로 설치하지는 않는다(차량 앱 정책·안전). 사람이 받아 adb나 앱 마켓으로 설치한다. */
const fs = require("node:fs");
const path = require("node:path");
const { StoreError } = require("./store.js");

const APK = "DriveWatch.apk";

function loadRelease(dir) {
  const notFound = () => { throw new StoreError("no_release", 404); };
  return {
    latest() {
      if (!dir) notFound();
      const file = path.join(dir, "latest.json");
      if (!fs.existsSync(file)) notFound();
      const info = JSON.parse(fs.readFileSync(file, "utf8"));
      return {
        versionCode: info.versionCode, versionName: info.versionName, commit: info.commit ?? null,
        notes: info.notes ?? "", sha256: info.sha256 ?? null, size: info.size ?? null, publishedAt: info.publishedAt ?? null,
      };
    },
    apk() {
      const file = dir && path.join(dir, APK);
      if (!file || !fs.existsSync(file)) notFound();
      return file;
    },
  };
}

module.exports = { loadRelease, APK };
