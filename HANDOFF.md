# DriveWatch 인수인계

작성 2026-11-11 07:58 · 이 파일은 로컬에만 있다(커밋하지 않음).

현대자동차그룹 차량용 앱 개발 공모전 출품작 DriveWatch의 작업 상태다. 다음에 이어서 일할 사람이 이 문서만 읽고 시작할 수 있게 썼다.

## 1. 한눈에 보기

- **1차 접수 마감은 2026-11-15(일)**, 팀 목표는 11-14다. 이메일로 접수양식(DOCX)과 서명 동의서를 낸다.
- **가산점은 시연 영상과 GitHub(소스 + APK)** 두 가지다. 둘 다 아직 없다.
- **2차 PT는 12-03~04**이고 실제 동작하는 앱 시연이 필수다(에뮬레이터 가능).
- 판정 로직, 신호 수집, 가족 요약 서버는 만들었고 에뮬레이터에서 확인했다. **제품 화면(Android)과 APK가 없다.**
- 팀원이 차량 앱 프로토타입을 만드는 중이고, 올려 주기로 했다. 11-11 07:58 현재 원격에 없다.

## 2. 어디에 무엇이 있나

작업 폴더는 `/Users/splendor/project/parkhere-main`이다. `tanman.km`과 같은 저장소의 worktree이고, 원격은 `kcc920926-droid/parkhere`(비공개)다.

| 폴더 | 내용 | 상태 |
| --- | --- | --- |
| `drive-watcher/` | 웹 MVP. `drive.olvend.com`에 배포됨 | 테스트 25개 통과 |
| `drivewatch-core/` | 판정 로직(Kotlin), 수집기, 카드 문구, 가족 서버 호출 | 테스트 45개 통과, 웹 MVP와 404줄 대조 |
| `drivewatch-vehicle/` | 차량 신호 수집(`DriveCollectorService`)과 가족 서버 연결(`FamilyLink`), 확인용 앱 | 에뮬레이터에서 확인 |
| `family-server/` | 가족 요약 서버와 자녀 폰 웹 화면 | 테스트 13개 통과, 공개 배포됨 |
| `docs/` | 신호 확인 기록, 시연 데이터 방향 | |
| `tools/` | Virtual Console 조작 스크립트 | |
| `submission/` | 접수양식 초안과 양식에 넣는 스크립트 | 팀 검토 전 |
| `opinions/`, `meetings/` | 팀 확정 기획서, 회의록 (팀원 작성) | |

읽을 순서: `README.md` → `docs/pleos-signal-check.md` → `docs/demo-data.md` → 각 폴더의 `README.md`.

### 브랜치와 PR

**열려 있는 PR은 #7 하나다.** 11-11에 #8~#11을 #7로 합쳤다. `codex/drive-watcher-mvp` 브랜치에 커밋 11개가 모두 들어 있고, 대상은 `main`이다.

| PR | 상태 | 메모 |
| --- | --- | --- |
| #7 | 열림 | 전체 내용. 병합하면 main에 모두 들어간다 |
| #8 | "병합됨"으로 표시 | main이 아니라 #7 브랜치에 들어갔다는 뜻이다 |
| #9, #10, #11 | 닫힘 | 커밋은 #7에 그대로 있다 |

- 지금 체크아웃된 브랜치는 `codex/drive-watcher-mvp`다. 11-11 낮에 만든 가족 서버 연결 모듈은 **아직 커밋하지 않았다**(`drivewatch-core/.../family/`, `FamilyLink.kt`, 확인용 앱, README들).
- 리뷰는 없다. 저장소에 CI가 없다. main에는 아직 아무것도 병합되지 않았다.
- 원격에 `feat/signal-collector`, `feat/landing-restructure`, `feat/card-text`, `docs/proposal-update` 브랜치가 남아 있다. 내용은 모두 #7 브랜치에 있으니 지워도 된다.
- 팀원의 화면 기획과 시안은 `origin/feature/winter1-final-plan`에 있고 main에는 없다(`opinions/drive-watch-screens.md`, `docs/screens/*.png`).

## 3. 공개 주소와 배포

| 주소 | 내용 |
| --- | --- |
| https://drive.olvend.com/ | 웹 MVP 랜딩 |
| https://drive.olvend.com/family/ | 자녀 폰 웹 화면 |
| https://drive.olvend.com/api/docs | REST API 명세 (원본은 `/api/openapi.json`) |

**이 Mac에서 Cloudflare 터널로 나간다.** 이 Mac이 꺼지면 사이트도 내려간다.

| 무엇 | 어디 |
| --- | --- |
| 터널 설정 | `~/.cloudflared/drive-watcher.yml` (고치기 전: `drive-watcher.yml.bak-20261110`) |
| 웹 MVP 정적 서버 | `127.0.0.1:8769`, LaunchAgent `com.olvend.drivewatcher.app` |
| 가족 요약 서버 | `127.0.0.1:8770`, LaunchAgent `com.olvend.drivewatcher.family` |
| 터널 | LaunchAgent `com.olvend.drivewatcher.tunnel` |
| 배포본과 기록 | `~/.local/share/drive-watcher/` (가족 서버는 그 안의 `family/`) |
| 배포 절차 | `~/.local/share/drive-watcher/DEPLOY.md`, `family-server/README.md`의 "배포" |

터널은 `/api/`와 `/family/`로 시작하는 주소만 가족 서버로 보내고, 나머지는 정적 서버로 보낸다. 11-11 07:58에 세 서비스 모두 실행 중이고 세 주소 모두 200이다.

- 웹 MVP는 11-11 09:10에 다시 배포했다(랜딩 재구성, 바뀐 플레이북 포함). 배포본은 `releases/20261111T000933Z-8rb076a2`이고, 되돌리려면 `current`를 `releases/20261108T224837Z-njby03ir`로 돌린 뒤 `com.olvend.drivewatcher.app`을 재시작한다.
- 공개 가족 서버에는 기록을 지우는 주소가 없다. 비우려면 기록 파일을 지우고 서비스를 재시작한다.

## 4. 이 Mac의 개발 환경

2026-11-10에 설치했다.

| 항목 | 값 |
| --- | --- |
| Android Studio | 2026.2.2 (`/Applications/Android Studio.app`) |
| Android SDK | `~/Library/Android/sdk` (플랫폼 34·35·36, 에뮬레이터 37.2.13) |
| Pleos 시스템 이미지 | Pleos Connect v2.1.0 (Android 14, arm64). 42dot SDK 라이선스에 동의하고 설치함 |
| 가상 기기 | `Pleos_Connect_v2.1` (2560×1440, Cold boot, RAM 4GB) |
| Gradle | 프로젝트 래퍼는 8.9~8.10.2, Homebrew는 9.9.0 |
| Node | 22 |

**함정**

- **Gradle은 JDK 21로 돌려야 한다.** 셸의 기본 `java`는 Homebrew openjdk 27이고, Gradle 8.x는 거기서 실행되지 않는다.
  `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`
- `adb`와 `emulator`는 PATH에 없다. `~/Library/Android/sdk/platform-tools/adb`, `~/Library/Android/sdk/emulator/emulator`로 부른다. `~/.zshrc`는 건드리지 않았다.
- **이 Mac에는 다른 서비스가 많이 돌고 있다**(터널 4개, node 서버, k8s). `pkill` 같은 넓은 종료 명령을 쓰지 말고 PID나 LaunchAgent 이름으로 정확히 지정한다.
- 8790 포트는 OrbStack이 쓴다. 가족 서버의 로컬 기본 포트는 8795다.
- `tanman.km/.claude/launch.json`은 가족 서버를 브라우저 미리보기로 띄우려고 만든 파일이다.

## 5. 자주 쓰는 명령

```sh
cd /Users/splendor/project/parkhere-main
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
ADB=~/Library/Android/sdk/platform-tools/adb

# 테스트
node --test drive-watcher/*.test.js
(cd family-server && npm test)
(cd drivewatch-core && ./gradlew cleanTest test)
(cd drivewatch-core && ./gradlew familyCheck -Pserver=http://127.0.0.1:8795 -Pwait=30)   # 켜져 있는 로컬 가족 서버에 실제로 보내 본다

# 에뮬레이터 (화면 없이 부팅. 화면을 보려면 Android Studio의 Device Manager에서 실행)
~/Library/Android/sdk/emulator/emulator -avd Pleos_Connect_v2.1 -no-window -no-snapshot -no-audio -no-boot-anim &
$ADB shell getprop sys.boot_completed      # 1이면 부팅 끝

# 확인용 앱 빌드와 설치
(cd drivewatch-vehicle && ./gradlew :harness:assembleDebug)
$ADB install -r drivewatch-vehicle/harness/build/outputs/apk/debug/harness-debug.apk
U=$($ADB shell am get-current-user | tr -d '\r')
$ADB shell pm grant --user $U com.drivewatch.harness android.car.permission.CAR_SPEED
$ADB shell am start --user $U -n com.drivewatch.harness/.MainActivity

# 시연 주행 재생 (48초) 과 기록 보기
$ADB forward tcp:8088 tcp:8088
node tools/vconsole-demo.js
$ADB logcat -d -s DWCOLLECT

# 에뮬레이터 끄기
$ADB emu kill

# 접수양식 다시 만들기 (원문은 submission/application.md)
python3 submission/build_application.py
```

기대하는 기록은 `window 4 ... observation=watching`, `window 5 ... observation=change`, `arrived windows=6 detection=hardBrakes@40s`다.

## 6. 알아낸 것 (다시 겪지 않도록)

**가족 서버 연결**

- JDK의 `HttpURLConnection`은 스트리밍 모드(`setFixedLengthStreamingMode`)에서 401 답의 본문을 읽지 못한다. 오류 코드가 사라져서 쓰지 않는다.
- 같은 주에 요약을 다시 보내면 서버가 덮어쓰고 가족의 확인 표시를 지운다(명세에 적힌 동작). 자동으로 보내지 말고 운전자가 누를 때만 보낸다.
- 폰에서 열 주소는 `/family/?code=123456`이다. 공개 주소에서는 `/api/`와 `/family/`만 가족 서버로 간다.
- 에뮬레이터에서 개발 PC는 `10.0.2.2`다. http 주소는 매니페스트의 `usesCleartextTraffic`이 있어야 붙는다(확인용 앱에만 넣었다).

**차량 신호**

- **Vehicle SDK 2.3.2에는 차속 API가 없다.** 차속은 AAOS `CarPropertyManager`의 `PERF_VEHICLE_SPEED`로 받는다. 권한 `android.car.permission.CAR_SPEED`는 실행 중에 사용자가 허용해야 한다.
- **브레이크는 눌림 여부만 온다.** "강한 제동"은 브레이크가 눌린 동안의 차속 감소율로 계산한다.
- 기어, 조향각, 방향지시등은 Vehicle SDK로 받는다. Pleos 권한(`pleos.car.permission.*`)은 설치 시 자동으로 부여됐다.
- 조향각의 부호는 Virtual Console 입력과 반대다.
- 내비 재탐색·회전 안내(NaviHelper), 음성(TextToSpeech)은 받아 보지 않았다.

**화면 뒤에서 신호 받기**

- **수집은 포그라운드 서비스에서 해야 한다.** 기어를 D로 넣으면 시스템이 내비를 앞으로 가져오고, 화면에 없는 앱은 프로세스가 멈춘다. 액티비티에서 받으면 주행 중에 아무것도 받지 못한다. `DriveCollectorService`로 해결했다.
- 팀원 프로토타입이 화면에서 직접 신호를 받고 있다면 같은 문제가 생긴다.

**에뮬레이터**

- 값 주입은 Virtual Console(`adb forward tcp:8088 tcp:8088` 뒤 `http://127.0.0.1:8088/`)로 한다. 일반 `adb shell cmd car_service inject-vhal-event`는 막혀 있다.
- Virtual Console을 스크립트로 조작할 수 있다. 브라우저가 열려 있다는 신호를 3초 안에 계속 보내야 값 변경이 받아들여진다. 이 주소들은 공식 문서에 없고 콘솔 화면이 쓰는 것을 읽어서 찾았다. 에뮬레이터 버전이 바뀌면 달라질 수 있다.
- 공식 샘플 앱 기준: Vehicle SDK 2.3.2, JDK 17, Gradle 8.10.2, Android Gradle Plugin 8.8.2, compileSdk 35.

**접수양식**

- 양식 02번의 칸 8개에 내용을 넣었다. 01 참가신청서, 03 동의서, 04 서명은 비워 뒀다.
- **칸 안의 표가 Word에서 제대로 보이는지 확인하지 못했다.** Pages 미리보기에서는 표가 풀려서 글자만 나열됐다.

## 7. 정한 것

- 출품작은 DriveWatch. 기획서는 팀 확정안(`opinions/drive-watch-final-plan.md`)의 구조를 두고, 변화 감지 부분만 웹 MVP 방식으로 쓴다.
- 자녀 폰 화면은 Android 앱이 아니라 웹 화면이다. 제출하는 APK는 차량 앱 하나다.
- 시연의 첫 주행 장면은 재탐색 대신 제동·조향으로 보여준다.
- 시연에서 가짜는 신호 입구에만 둔다. 과거 기록은 합성 기록이고, 구간 길이만 2분에서 8초로 줄인다. 화면에 "시연용"이라고 표기한다.
- 합성 12주 기록은 오늘 기준으로 만든다(`DemoWeeklyHistory.firstMondayFor`).
- 가족 요약 서버는 `drive.olvend.com`에 올린다.
- 랜딩은 제품 소개가 아니라 기획서 주장의 증거이자 시연 영상의 대본으로 만든다. 주 버튼은 `판단 엔진 시연`이다.
- 장거리 운전 같은 다른 상황은 **향후 확장 가능성으로만** 쓴다. 화면을 따로 만들지 않는다. 음주는 기획서대로 판정하지 않고, "오늘은 다른 방법으로 가기" 버튼만 둔다.
- 운전점수(티맵·네이버)와의 차이는 기획서의 "기존 방식의 한계"에 표로 넣는다: 내비를 켠 주행만 기록, 과속·급가속·급감속을 남과 비교, 보험 할인용.

## 8. 아직 정하지 않은 것

| 무엇 | 누가 정하나 |
| --- | --- |
| 팀원 화면 기획 7절의 쟁점 5개 (가족 연결 방향, 요약 보내는 때, 운전자 확인, 기본 공개 범위, 시연 장소) | 팀. 팀원 제안을 모두 받아들여도 무리가 없다고 봤다 |
| 폰 요약의 "함께 할 일"에 치매안심센터 선별검사를 남길지. 근거가 제동·조향이라 비약으로 보일 수 있다 | 팀 |
| 시연에서 주행을 누가 운전하나 (발표자 직접, 또는 스크립트) | 팀. 어느 쪽이든 코드는 같다 |
| APK 없이 GitHub 가산점을 받을 수 있는지 | 공식 FAQ나 문의처에서 확인 |
| 팀명 (메일 제목과 APK 파일명에 들어간다) | 팀 |
| 기획서에 넣은 가정값: MAU 시나리오(탑재 10만 대당 480 / 1,800 / 3,825명), 12주 유지율(부모님 60%, 자녀 50%) | 팀 |

## 9. 남은 일

간트차트 기준이다. 위에서부터 급하다.

| 언제 | 무엇 | 메모 |
| --- | --- | --- |
| 지금 | 서명 동의서와 참가신청서 | 두 사람 서명이 필요하다. 사람 손이 필요해서 가장 오래 걸린다 |
| 끝남 | 랜딩 첫 화면 재구성 | 11-11에 배포함. 9초 개요는 주행 장면으로 시작, 가족 요약 장면 포함, 한 페이지 스크롤. 운전 약속 장면은 없다 |
| 11-11~12 | 앱 화면: 도착 카드, 주간 카드 | 팀원 프로토타입이 11-11 저녁까지 안 올라오면 먼저 시작하기로 했다. 확인용 앱의 `arrivalCard()`가 카드 문구의 초안이다 |
| 끝남 | 차량 앱과 가족 서버 연결 모듈 | 11-11. `FamilyLink`로 등록·초대 코드·요약 보내기·확인 읽기. 로컬 서버와 에뮬레이터에서 확인. 제품 화면에 붙이는 일은 화면이 나온 뒤 |
| 11-12 | 시연 패널과 앱 시계 | `밤 9시로`, `다음 주로`, `4주 뒤로`. 앱 전체가 하나의 시계를 보게 한다 |
| 11-13 | APK 빌드 | `apk/팀명_DriveWatch_release.apk` |
| 11-13 | 시연 영상 | **11-12 밤: 임원 보고 톤의 보고체 시연 영상 `submission/video/roughcut/report-v11.mp4`(2분 43초, 자막 싱크 수정, 자막 띄어쓰기 수정·미끄러지는 밑줄, 말과 같은 박자의 강조·손가락·목록 테두리, 광고형 오프닝·엔딩, 비전 장면 "숫자 하나", 효과음은 real-sfx 스킬 순서, 내레이션 MiniMax Hana, 단어 따라 밝아지는 자막, 공모전 출품 표기, 장 01~04, 내레이션·자막·음악·실제 효과음, 새 에뮬레이터 녹화)를 새 기준으로 만들었다. 컨셉 시트 14절.** 이야기판 "괜찮다니까"의 지금 기준은 `submission/video/roughcut/film-v15.mp4`(1분 32초, 둘째 섹션이 팀원 차 앱 0.2.0의 에뮬레이터 실제 녹화로 시연 9단계를 따라감, 숫자는 앱의 익숙한 길 재탐색 기준, 실제 효과음 포함)이고 바뀐 점은 컨셉 시트 12절, 기본 구성은 9절. 이전 판 `film-v14.mp4`(62초, 시안 합성)도 남겨 두었다. 같은 이야기의 세로 숏폼판(30초)은 `submission/video/shorts/shorts-v1.mp4`, 컨셉 시트 10절. 새 구성인 숏폼 B안(차가 화자, 38초)은 `submission/video/shorts/shorts-b-v1.mp4`, 컨셉 시트 11절. 남은 것은 목소리·음악, 팀 검토. 설명형 가편집본이 `submission/video/roughcut/roughcut-v1.mp4`에 있다(11-11, 임시 기계 음성과 자막, 일부 구간은 정지 화면). 차 화면은 실내 컷에 시안을 합성한 컷(M1~M4)으로 만들었다. 컷별 연출은 `docs/demo-video-concept-sheets.md`(11-11 초안, 13컷, 2분 27초). 연출 컷 세 개(운전·사무실·식탁)는 힉스필드로 만들어 `submission/video/`에 있다(git 제외). 대본과 촬영 순서는 `docs/demo-video-script.md`(11-11 초안, 2분 26초). 도입·마무리의 사람 장면은 힉스필드 연출 컷(아직 생성 안 함), 앱 화면은 실제 녹화. 11-12에 전날 확인 4가지를 먼저 한다. 주행은 `node tools/vconsole-demo.js`로 매번 똑같이 재생된다 |
| 11-13 | GitHub 정리 | README의 실행 방법·테스트 환경·권한은 11-11에 채웠다(확인용 앱 기준, 깨끗한 사본에서 명령 확인). 제품 화면과 APK가 들어오면 실행 방법의 앱 이름과 APK 항목, `팀명`을 고친다. **심사 계정 초대는 필요 없다. 대신 비공개 저장소는 11-16(월)까지 Public으로 바꾸고 11-23까지 유지해야 가산점이 적용된다**(공모전 가이드 `/guide/submission`, 11-11에 읽음. 공개가 어려우면 소스와 APK를 묶은 zip 링크를 마감 전에 메일로 보낼 수 있다). 메일에 저장소 주소와 최종 커밋 해시를 적는다. 공개 전에 저장소와 커밋 기록에 남기면 안 되는 것이 없는지 확인해야 한다(아직 안 함). README 필수 항목에는 "에뮬레이터 버전과 화면 규격"도 있다(반영함) |
| 11-13~14 | 기획서 마무리 | 아래 목록 |
| 11-14 | 숏폼 영상 | 가산점 없음. **B안 "고자질하는 차"가 최신이다**: `submission/video/shorts/shorts-b-v1.mp4`(1080×1920, 38초, 엄마 차 아방이가 화자. "오늘, 엄마 고자질할 거야"로 열고 "엄마한테, 먼저."로 뒤집음, 차 대사 11줄·음악·실제 녹음 효과음 29개), 게시글 문안 `submission/video/shorts/post-b.md`, 구성은 컨셉 시트 11절, 세 안 비교는 `submission/video/shorts/plan-v2.md`. 이전 판인 **세로판 v1**: `submission/video/shorts/shorts-v1.mp4`(1080×1920, 30초, 같은 "괜찮다니까" 이야기를 결말부터·괜찮다 카운터·박자 컷으로 다시 짠 도파민형, 목소리·음악·실제 녹음 효과음 31개). 게시글 문안과 해시태그는 `submission/video/shorts/post.md`, 구성은 컨셉 시트 10절. 남은 것: v1과 B안 중 올릴 것 고르기, 귀로 듣고 확인(차 목소리 느낌, 정적과 쿵, 입 모양, 반복 재생 이음매), 팀 검토, 릴스·쇼츠 업로드와 기획서에 링크 기입 |
| 11-14 | 이메일 제출 | 제목 `[차량용 앱 개발 공모전] 팀명_대표자명`, 팀원 전원 CC. 수정 제출은 전체 자료와 서명 동의서를 다시 첨부 |

**기획서** (`submission/application.md`를 고친 뒤 스크립트로 다시 만든다)

11-11에 반영함: 차속과 브레이크 신호의 실제 형태, 지표 표의 구현·에뮬레이터 확인·설계 표시, 자녀 화면이 웹이라는 점, 화면 뒤 수집 서비스, 내비 앱 운전 점수와의 차이, 가족 화면과 API 명세 링크. 가족 연결은 "부모님이 차에서 코드를 띄우고 자녀가 입력"으로 바꿔 적었다(팀원 화면 기획 7절의 1번 제안과 같다).

남은 것:

- 노란 칸: 시연 영상 링크, GitHub 주소와 APK 경로, 화면 시안 첨부 방식, 숏폼 링크
- Word로 열어 칸 안의 표 확인

**만들지 않은 것**

- 도로 조건(도심 / 고속도로) 구분. 수집기는 시작할 때 정한 값 하나를 쓴다
- 재부팅 뒤 수집 서비스 자동 시작
- 가족 서버의 기록 삭제, 토큰 만료, 본인 인증
- 운전 약속, 불편 구간 기억과 경로 선택 (기획서의 기능 2와 3)

## 10. 확인되지 않은 것

- **실차.** 모든 확인은 Pleos Connect Emulator v2.1.0에서 했다.
- 강한 제동(4m/s²)과 조향 보정(5도) 기준값. 시연용이다. 조향 보정의 정의는 웹 MVP에 없어서 새로 정했다.
- 포그라운드 서비스와 차속 권한이 Pleos App Market 심사에서 허용되는지.
- 폰 웹 화면을 실제 휴대폰에서 열어 보지 않았다.
- API 명세 페이지는 로컬에서만 화면으로 확인했다. 공개 주소에서는 응답 코드까지만 봤다.
- 에뮬레이터 앱에서 공개 주소(https)로 요약을 보내 보지 않았다. 앱은 로컬 서버(http://10.0.2.2:8795)로만 확인했고, 공개 주소는 PC에서 같은 Kotlin 코드로 `unauthorized` 답을 받는 것까지만 봤다.
- 화면 기획에 "지금 MVP 코드(`app/`)"라고 적혀 있지만 그 코드는 원격 어디에도 없다. Android인지 웹인지도 모른다.
