# drive.olvend.com 첫 페이지

DriveWatch 소개 페이지와 판단 엔진 페이지입니다.

- `public/index.html`(`home.css`, `home.js`): 첫 페이지. 앱 소개, 실제 앱 화면, 차량 앱 APK 받기.
- `public/engine-demo.html`(`engine.css`, `engine-story.js`): 판단 엔진 페이지. `legacy/drive-watcher`의 실제 판정 코드(`engine.js`)에 합성 기록(`replays.js`)과 공개 실측 CAN(`can-source.js`)을 넣어 보여 줍니다. 옛 판단 엔진 화면은 `engine-lab.html`(직접 운전해 보기)로 남깁니다.
- `public/car.js`: 두 페이지가 같이 쓰는 시계 차 그리기.
- `public/assets/`: 그림.
  - 앱 화면 그림은 `submission/screens/`의 PNG를 WebP로 바꾼 것입니다: `cwebp -q 80 -m 6 <png> -o assets/<이름>.webp`
  - 공유 미리보기 `assets/og.png`(1200×630)는 `tools/og.html`을 1200×630 크기로 찍어서 만듭니다.
- `origin.cjs`: 정적 서버입니다. 시작할 때 배포본을 한 번 읽어 메모리에서 내주고, `assets/` 아래 그림과 글꼴도 내줍니다.
- `tools/prepare-release.sh`: 배포본을 만듭니다. `public/`에 `legacy/drive-watcher`의 엔진 코드, 개인 경향 모델(`profile-demo.html`), 옛 판단 엔진 화면(`engine-lab.html`로 이름을 바꿈)을 함께 묶습니다. 옛 첫 페이지와 Pleos 화면 미리보기(`console.html`)는 넣지 않습니다. 페이지가 가리키는 파일이 빠지면 배포본을 만들지 않습니다.

## 디자인 메모

- 종이색 바탕, 잉크, 브랜드 보라(#6B5CF5), 상태 세 색(안정·관찰·대화 권장)만 씁니다. 제목은 Noto Serif KR, 본문은 Pretendard입니다.
- 반복 장치는 손으로 그은 빨간 선입니다. 하지 않는 말("이제 그만하면 안 돼?")과 보내지 않는 것(위치, 경로, 시각)을 이 선으로 지웁니다.
- 시계 차는 `home.js`에서 그립니다. 좌표는 차량 앱 `ClockCar.kt`, 가족 화면 `family-server/public/clockcar.js`와 같습니다.
- "평소와 비교" 그래프는 예시 값이고 화면에 "예시"라고 적습니다. 시연용 기능은 페이지에도 시연용이라고 적습니다.

## 미리 보기

```sh
python3 -m http.server 8776 --bind 127.0.0.1 --directory site/public
```

판단 엔진 페이지는 `legacy/drive-watcher`의 스크립트가 필요해서, 배포본을 만들어 띄워 보는 편이 쉽습니다.

```sh
release="$(DRIVEWATCH_SITE_DEPLOY=/tmp/dw-site site/tools/prepare-release.sh)"   # /tmp/dw-site/releases 폴더가 있어야 함
node site/origin.cjs "$release/site" 8777
```

`/api/`, `/family/`는 가족 서버 주소라 미리 보기에서는 열리지 않습니다. APK 버전 표시는 `/api/app/latest`를 읽지 못하면 기본 문구로 남습니다.

## 배포

`drive.olvend.com`의 `/api/`, `/family/`를 뺀 나머지 주소는 `127.0.0.1:8769`의 정적 서버(LaunchAgent `com.olvend.drivewatcher.app`)가 받습니다. 이 서버는 `~/.local/share/drive-watcher/origin.cjs`로 `~/.local/share/drive-watcher/current/site`를 내줍니다.

```sh
release="$(site/tools/prepare-release.sh)"
(cd "$release" && shasum -a 256 -c SHA256SUMS)
cp ~/.local/share/drive-watcher/origin.cjs ~/.local/share/drive-watcher/origin.cjs.bak-<날짜>   # 서버 파일을 바꿀 때만
cp "$release/origin.cjs" ~/.local/share/drive-watcher/origin.cjs
ln -sfn "$release" ~/.local/share/drive-watcher/current
launchctl kickstart -k "gui/$(id -u)/com.olvend.drivewatcher.app"
curl -s -o /dev/null -w '%{http_code}\n' https://drive.olvend.com/
```

되돌릴 때는 `current`를 이전 배포본으로 돌리고, 서버 파일을 바꿨다면 백업을 되살린 뒤 다시 `kickstart` 합니다. 새 `origin.cjs`는 옛 배포본도 그대로 내줍니다.
