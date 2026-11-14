# family-server

DriveWatch의 가족 요약 서버와 자녀 폰 웹 화면입니다. 차량 앱이 주간 요약을 보내면, 연결된 가족이 휴대폰 브라우저에서 봅니다. **시연용**이며 운영 환경용 인증·보관 설계가 아닙니다.

공개 주소는 다음과 같습니다.

- 폰 화면: <https://drive.olvend.com/family/>
- REST API 명세: <https://drive.olvend.com/api/docs> (원본은 <https://drive.olvend.com/api/openapi.json>)

의존성이 없습니다. Node 20 이상이면 실행됩니다.

```sh
npm start        # http://127.0.0.1:8795/
npm test
```

| 환경 변수 | 기본값 | 뜻 |
| --- | --- | --- |
| `PORT` | 8795 | 포트 |
| `HOST` | 127.0.0.1 | 실제 휴대폰에서 열려면 `0.0.0.0`으로 띄우고 같은 Wi-Fi의 PC 주소로 접속 |
| `STORE_FILE` | `data/store.json` | 기록 파일. 저장소에 올라가지 않음 |
| `TRUST_PROXY` | 없음 | `1`이면 요청 수를 셀 때 Cloudflare가 알려 주는 접속 주소를 쓴다. 터널 뒤에서만 켠다 |

Pleos 에뮬레이터 안의 앱은 이 PC의 서버를 `http://10.0.2.2:8795`로 부릅니다.

## 지키는 것

- **부모님이 먼저.** 초대 코드는 운전자가 차에서 띄운다. 코드는 6자리, 10분, 한 번만 쓸 수 있다.
- **보내기로 한 것만.** 운전자가 `상태만`을 고르면 숫자 근거는 저장하지도, 내보내지도 않는다. 나중에 `상태만`으로 바꾸면 이미 저장된 숫자도 가족에게 보이지 않는다.
- **위치와 경로는 받지 않는다.** 요약은 정해진 항목만 골라 저장하고 나머지는 버린다.
- 연결되지 않은 사람은 요약을 읽거나 확인할 수 없다.
- 한 주소(IP)에서 등록은 1시간에 10번, 코드 입력은 10분에 10번, 쓰기 요청은 1분에 120번까지다. 운전자는 500명, 가족은 2,000명까지 등록된다.

## REST API 명세

명세는 `openapi.json` 한 파일이 원본이고, `/api/docs`가 그 파일을 읽어 화면으로 보여준다. 주소를 추가하거나 바꾸면 `server.js`의 `ROUTES`와 `openapi.json`을 함께 고친다. 둘이 다르면 테스트가 실패한다.

## 차량 앱이 부르는 주소

`Authorization: Bearer <carToken>`을 붙입니다.

| 요청 | 하는 일 |
| --- | --- |
| `POST /api/car/drivers` `{name, shareScope}` | 운전자 등록. `{driverId, carToken}`을 돌려준다 |
| `POST /api/car/invites` | 초대 코드 발급. `{code, expiresAt}` |
| `PUT /api/car/share-scope` `{shareScope}` | `status` 또는 `evidence` |
| `POST /api/car/summaries` | 주간 요약 보내기 (아래 형식) |
| `GET /api/car/me` | 연결된 가족과, 최근 요약을 확인한 사람 |

```json
{
  "week": "2026-11-09",
  "status": "talk",
  "headline": "한 줄 요약 (안정일 때)",
  "goodPoints": ["밤 운전 0회"],
  "promise": "해 지면 운전 안 하기 · 이번 주 지킴",
  "avoided": "중앙대로 합류 2번 피했어요",
  "changes": ["강한 제동이 4주째 평소보다 많았어요"],
  "evidence": [{ "label": "강한 제동", "unit": "회/100km", "typical": 6.3, "value": 21.9, "weeks": 4 }]
}
```

`week`는 그 주의 월요일이고, `status`는 `drivewatch-core`의 주간 판정 결과(`learning`, `insufficient`, `stable`, `observe`, `talk`)입니다. 같은 주에 다시 보내면 덮어쓰고 확인 표시가 지워집니다.

## 폰 웹 화면

`public/`에 있습니다. 가입 → 부모님 차와 연결 → 가족 목록 → 주간 요약 순서이고, 4초마다 새로 고칩니다. 푸시 알림은 없습니다. 차 화면의 QR에 `http://서버주소/family/?code=123456`을 담으면 코드가 미리 채워집니다.

화면 문구와 구성은 `opinions/drive-watch-screens.md`의 P1~P5 시안을 따랐습니다. 다른 점은 두 가지입니다. 휴대폰 번호와 인증번호는 받지 않고, 연결할 때 호칭(엄마 / 아빠 / 부모님)을 고릅니다.

## 차량 앱 없이 돌려 보기

```sh
npm start
node tools/car-sim.js setup 김정숙 evidence   # 초대 코드가 나온다
# 브라우저에서 가입하고 코드를 입력한 뒤
node tools/car-sim.js send talk               # stable | observe | talk
node tools/car-sim.js me                      # 가족이 확인했는지
```

차량 앱에서는 이 스크립트 대신 Kotlin 코드를 쓴다. `drivewatch-core`의 [`family/`](../drivewatch-core/README.md#가족-서버-연결)가 같은 API를 부르고, Android에서는 `drivewatch-vehicle`의 `FamilyLink`로 쓴다.

공개 서버를 상대로 돌리려면 `FAMILY_SERVER=https://drive.olvend.com`을 앞에 붙인다. 공개 서버에 남긴 기록은 지우는 주소가 없으니, 시연 직전이 아니면 로컬에서 한다.

## 배포

`drive.olvend.com`은 이 Mac에서 Cloudflare 터널로 나간다. 터널은 `/api/`와 `/family/`로 시작하는 주소만 이 서버(`127.0.0.1:8770`)로 보내고, 나머지는 웹 MVP의 정적 서버(`127.0.0.1:8769`)로 보낸다.

| 무엇 | 어디 |
| --- | --- |
| 배포본 | `~/.local/share/drive-watcher/family/releases/<UTC 시각>/app` |
| 지금 쓰는 배포본 | `~/.local/share/drive-watcher/family/current` (링크) |
| 기록 파일 | `~/.local/share/drive-watcher/family/data/store.json` |
| 로그 | `~/.local/share/drive-watcher/family/family.out.log`, `family.err.log` |
| 자동 실행 | `~/Library/LaunchAgents/com.olvend.drivewatcher.family.plist` |
| 터널 경로 규칙 | `~/.cloudflared/drive-watcher.yml` (고치기 전 파일은 `drive-watcher.yml.bak-20261110`) |

다음 배포 순서:

```sh
release="$(tools/prepare-release.sh)"          # 테스트 후 스냅샷을 만들고 경로를 출력
ln -sfn "$release" ~/.local/share/drive-watcher/family/current
launchctl kickstart -k "gui/$(id -u)/com.olvend.drivewatcher.family"
curl -s -o /dev/null -w '%{http_code}\n' https://drive.olvend.com/api/docs
```

문제가 있으면 `current`를 이전 배포본으로 돌리고 다시 `kickstart` 한다. 기록을 비우려면 서비스를 재시작하기 전에 기록 파일을 지운다. 공개를 내리려면 터널 설정에서 경로 규칙을 빼고 터널을 재시작한다.

## 하지 않은 것

- 휴대폰 본인 인증, 비밀번호, 토큰 만료. 토큰은 브라우저와 기록 파일에 그대로 남는다.
- 기록 삭제와 보관 기한. 공개 서버에 등록한 이름과 요약은 기록 파일을 지울 때까지 남는다.
- 푸시 알림, 가족이 약속을 제안하는 화면(P6).
