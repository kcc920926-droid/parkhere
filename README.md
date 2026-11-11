# DriveWatch

**부모님 운전에 차는 스마트워치.** 차량 신호로 부모님 운전의 *나이*가 아니라 *본인 평소 대비 변화*를 알아채고, 불편했던 길은 다음에 피하게 돕고, 자녀에게는 감시가 아닌 요약을 보내는 Pleos Connect 차량용 앱입니다. 현대자동차그룹 차량용 앱 개발 공모전 출품작입니다.

- [플레이북](PLAYBOOK.MD) · 서비스 흐름, 공모전 MVP, 차량 데이터의 역할
- [태스크리스트](TASKLIST.MD) · 검증과 구현 순서
- [최종 기획서](opinions/drive-watch-final-plan.md) · 심사 4항목 순서의 기획 원본
- [회의록](meetings/) · 결정 기록
- [이전 기획 · 어디대?](archive/eodidae/PLAYBOOK.MD) · 목적지에 맞는 주차 구역 안내와 주차 위치 기록
- [이전 기획 · TANMAN.KM](archive/tanman.km/PLAYBOOK.MD) · 주행거리 기반 동승 비용 정산

## 드라이브 와쳐 웹 MVP

도심과 고속도로의 합성 주행 기록으로 개인 기준선, 연속 초과 감지, 감지 근거를 시연합니다.

- [공개 시연](https://drive.olvend.com/) · [소스](legacy/drive-watcher/)
- [웹 MVP 플레이북](legacy/drive-watcher/PLAYBOOK.MD)

## 구성

- [drivewatch-core](legacy/drivewatch-core/README.md) · 구간 판정과 주간 판정을 옮긴 Kotlin 모듈. 웹 MVP와 결과가 같은지 테스트로 확인합니다.
- [drivewatch-vehicle](legacy/drivewatch-vehicle/README.md) · 차량 신호를 받는 Android 라이브러리와 확인용 앱. 수집은 포그라운드 서비스에서 합니다.
- [family-server](family-server/README.md) · 가족 요약 서버와 자녀 폰 웹 화면. [폰 화면](https://drive.olvend.com/family/) · [REST API 명세](https://drive.olvend.com/api/docs)
- [Pleos 에뮬레이터 신호 확인 기록](docs/pleos-signal-check.md) · 차속·기어·브레이크·조향·방향지시등을 받는 방법과 확인 결과
- [시연 데이터와 시연 이벤트](docs/demo-data.md) · 시연에서 무엇이 가짜이고 무엇이 실제 코드인지
- [접수양식 초안](submission/application.md) · 기획서 원문과 양식에 넣는 스크립트

## 실행 방법

1. Android Studio로 저장소 루트를 연다. 또는 터미널에서 빌드한다.

   ```bash
   ./gradlew :app:assembleDebug
   ```

2. Pleos Connect Emulator(v2.1.0)에 설치하고 실행한다. 일반 Android 에뮬레이터에서도 돌지만, 그때는 차량 신호 없이 시연 주행으로 돈다.

   ```bash
   adb install -r -g app/build/outputs/apk/debug/app-debug.apk
   adb forward tcp:8088 tcp:8088   # Virtual Console
   ```

   Pleos 에뮬레이터의 기본 주행 화면 앱(`ai.umos.drivingview`)이 그래픽 오류로 계속 재시작하며 화면을 가져가면, 에뮬레이터에서만 잠시 끈다: `adb shell pm disable-user --user 10 ai.umos.drivingview` (되돌리기: `pm enable --user 10 ai.umos.drivingview`).

3. 시연 순서 (자세한 순서와 확인할 값은 [MVP 테스트 가이드](docs/mvp-test-guide.md)). 차량 앱은 부모님이 쓰고, 자녀는 폰 브라우저로 `drive.olvend.com/family`를 연다:
   1. 운전자 등록(김정숙) → 동의(보낼 범위 선택) → 약속 고르기 → 가족 초대 코드. 자녀 폰에서 가입하고 코드를 입력하면 차 화면이 "연결됐어요"로 바뀐다.
   2. 홈에서 `병원`을 누르고 `▶ 시연 주행 시작` 버튼(또는 `python3 tools/demo-drive.py`)으로 주행한다(D → P). 주행 중 재탐색 두 번째에 음성 안내, 도착 후 한 줄 음성과 "중앙대로 합류… 표시해 둘까요?" → `표시하기`.
   3. 시연 패널 `다음 주로` → `병원` → 빠른 길 / 편한 길 → `편한 길` → `▶ 시연 주행 시작`.
   4. 시연 패널 `밤 9시로` → 홈에 음성과 `다른 방법으로 가기`.
   5. 시연 패널 `4주 뒤로` → `이번 주 카드`(대화 권장) → `가족에게 보내기` → 자녀 폰에서 요약 확인 → `확인했어요` → 차 홈의 가족 카드에 확인 표시.
   6. 상단 프로필 탭에서 `박영호`로 바꾼다. 기록이 따로이고 상태는 "안정"이다.

   차량 신호(기어·방향지시등·브레이크·차속)는 실제로 받고, 내비 안내(회전 안내·재탐색)와 과거 기록은 시연용이다.

엔진 테스트: `./gradlew :app:testDebugUnitTest`

## 테스트 환경과 설정

- Android API 28+ (compileSdk 36), Gradle 8.14.3, AGP 8.13, Kotlin 2.2, Jetpack Compose, JDK 17 이상
- 확인한 환경: Android Studio 내장 JDK 21, **Pleos Connect Emulator v2.1.0**(Android 14, 2560×1440)
- Pleos SDK: Vehicle 2.3.2(기어·방향지시등·브레이크), TextToSpeech 3.3.1(기기 내 음성), AAOS Car API(차속)
- 권한: `pleos.car.permission.CAR_DRIVE`, `CAR_LIGHTS`, `TTS_SERVICE`, `android.car.permission.CAR_SPEED`(실행 중 허용). `adb install -g`로 설치하면 미리 허용된다.
- 차량 신호와 음성은 화면 뒤 포그라운드 서비스(`DriveService`)에서 받는다. D단에서는 내비가 화면 앞으로 오고 화면 뒤 앱은 멈추기 때문이다.
- **시연용인 것:** 과거 12주 기록, 내비 안내(회전 안내·재탐색), 시연 패널의 시간 건너뛰기. 일반 Android 에뮬레이터에서는 차량 신호 없이 시연 주행이 혼자 돈다.

## 구조

| 파일 | 역할 |
| --- | --- |
| `app/src/main/java/com/parkhere/drivewatch/Engine.kt` | 지표 계산, 8주 기준선, 안정·관찰·대화 권장 판정 |
| `app/src/main/java/com/parkhere/drivewatch/Model.kt` | 운전자 프로필, 시연 시계, 주행 상태, 주간 카드 문구 |
| `app/src/main/java/com/parkhere/drivewatch/DriveService.kt` | 주행 한 번: 차량 신호 수집, 회전 안내 대조, 급제동 계산, 음성 |
| `app/src/main/java/com/parkhere/drivewatch/VehicleLink.kt` | Pleos Vehicle SDK·AAOS 차속 연결 |
| `app/src/main/java/com/parkhere/drivewatch/Speech.kt` | Pleos TTS(없으면 Android TTS) |
| `app/src/main/java/com/parkhere/drivewatch/FamilyApi.kt` | 가족 서버(`drive.olvend.com`) 초대 코드·주간 요약 |
| `app/src/main/java/com/parkhere/drivewatch/Demo.kt` | 시연용 과거 기록, 목적지별 내비 안내 스크립트 |
| `app/src/main/java/com/parkhere/drivewatch/DemoDriver.kt` | `▶ 시연 주행 시작` 버튼: Virtual Console로 차를 움직인다 |
| `app/src/main/java/com/parkhere/drivewatch/MainActivity.kt` | 차량 화면 C1~C11과 시연 패널 |
| `app/src/test/java/com/parkhere/drivewatch/EngineTest.kt` | 판정 규칙과 시연 시나리오 테스트 |
| `tools/demo-drive.py` | 앱 버튼과 같은 시연 주행을 터미널에서 실행 |
| `docs/mvp-test-guide.md` | 테스트 순서와 확인할 값 |

## APK

`./gradlew :app:assembleRelease` → `app/build/outputs/apk/release/app-release.apk`. 제출용은 팀명이 정해지면 `apk/팀명_DriveWatch_release.apk`로 복사한다.
