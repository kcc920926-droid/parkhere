# DriveWatch

**부모님 운전에 차는 스마트워치.** 차량 신호로 부모님 운전의 *나이*가 아니라 *본인 평소 대비 변화*를 알아채고, 불편했던 길은 다음에 피하게 돕고, 자녀에게는 감시가 아닌 요약을 보내는 Pleos Connect 차량용 앱입니다. 현대자동차그룹 차량용 앱 개발 공모전 출품작입니다.

캐릭터는 **시계 차**입니다. 옆모습 차의 창문이 계기판이고, 바늘 위치·칸 색·표정으로 안정·관찰·대화 권장을 함께 보여 줍니다. 런처 아이콘에는 상태를 넣지 않습니다. 원본은 `app/design/brand/`에 있습니다.

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
   1. 운전자 등록(김정숙) → 동의(보낼 범위 선택) → 약속 고르기 → 가족 연결. 연결 화면은 QR, 6자리 코드(읽어 주기), 자녀 번호로 보내는 링크 문자(동의 체크, 연락처에서 고르기)를 함께 준다. 자녀 폰에서 코드를 넣으면 차 화면이 "연결됐어요"로 바뀐다. 폰 없이 시연할 때는 `시연용 · 가상 자녀(김민수)로 연결하기`를 누른다.
   2. 홈에서 `병원`을 누르고 `시연 주행 시작`(Virtual Console로 차를 움직임) 또는 `시연 재생`(서버의 주행 시나리오를 앱 안에서 재생) 버튼으로 주행한다. 주행 중 재탐색 두 번째에 음성 안내, 도착 후 한 줄 음성과 "중앙대로 합류… 표시해 둘까요?" → `표시하기`.
   3. 시연 패널 `다음 주로` → `병원` → 빠른 길 / 편한 길 → `편한 길` → `시연 주행 시작`.
   4. 시연 패널 `밤 9시로` → 홈에 음성과 `다른 방법으로 가기` → `대리운전` 또는 `가족에게 부탁`. 부탁은 자녀 폰에 카드로 뜨고, 자녀가 "태우러 갈게요"나 "택시를 불러 드릴게요"로 답하면 차 화면에 나타난다. 택시는 차가 아니라 자녀 폰에서 부른다.
   5. 시연 패널 `4주 뒤로` → `이번 주 카드`(대화 권장) → `가족에게 보내기` → 자녀 폰에서 요약 확인 → `확인했어요` → 차 홈의 가족 카드에 확인 표시.
   6. 상단 프로필 탭에서 `박영호`로 바꾼다. 기록이 따로이고 상태는 "안정"이다. 시연 패널의 `기록:` 버튼은 과거 기록을 평소대로 → 관찰 → 대화 권장 시나리오로 바꾼다.

   차량 신호(기어·방향지시등·브레이크·차속)는 실제로 받고, 내비 안내(회전 안내·재탐색)와 과거 기록은 시연용이다.

엔진 테스트: `./gradlew :app:testDebugUnitTest`

## 테스트 환경과 설정

- Android API 28+ (compileSdk 36), Gradle 8.14.3, AGP 8.13, Kotlin 2.2, Jetpack Compose, JDK 17 이상
- 확인한 환경: Android Studio 내장 JDK 21, **Pleos Connect Emulator v2.1.0**(Android 14, 2560×1440)
- Pleos SDK: Vehicle 2.3.2(기어·방향지시등·브레이크), TextToSpeech 3.3.1(기기 내 음성), AAOS Car API(차속)
- 권한: `pleos.car.permission.CAR_DRIVE`, `CAR_LIGHTS`, `CAR_INFO`, `PRIVATE_CAR_INFO`(차 키용 차대번호, 해시만 서버로 보냄), `TTS_SERVICE`, `android.car.permission.CAR_SPEED`(실행 중 허용). `adb install -g`로 설치하면 미리 허용된다.
- 화면: Pleos Connect UI 디자인 가이드의 색 토큰과 글꼴(Asta Sans, OFL, `assets/licenses/`)을 쓰고, 차 화면이 야간 모드면 어두운 테마로 바뀐다.
- 차량 신호와 음성은 화면 뒤 포그라운드 서비스(`DriveService`)에서 받는다. D단에서는 내비가 화면 앞으로 오고 화면 뒤 앱은 멈추기 때문이다.
- **시연용인 것:** 과거 12주 기록, 내비 안내(회전 안내·재탐색), 시연 패널의 시간 건너뛰기, 시연 재생, 대리운전 연결. 일반 Android 에뮬레이터에서는 차량 신호 없이 시연 주행이 혼자 돈다.
- **실제로 도는 것:** 가족 서버(`drive.olvend.com`)의 초대·연결·주간 요약·확인 표시·가족에게 부탁, 앱 업데이트 알림.

## 구조

| 파일 | 역할 |
| --- | --- |
| `app/src/main/java/com/parkhere/drivewatch/Engine.kt` | 지표 계산, 8주 기준선, 안정·관찰·대화 권장 판정 |
| `app/src/main/java/com/parkhere/drivewatch/Model.kt` | 운전자 프로필, 시연 시계, 주행 상태, 주간 카드 문구 |
| `app/src/main/java/com/parkhere/drivewatch/DriveService.kt` | 주행 한 번: 차량 신호 수집, 회전 안내 대조, 급제동 계산, 음성 |
| `app/src/main/java/com/parkhere/drivewatch/VehicleLink.kt` | Pleos Vehicle SDK·AAOS 차속 연결 |
| `app/src/main/java/com/parkhere/drivewatch/Speech.kt` | Pleos TTS(없으면 Android TTS) |
| `app/src/main/java/com/parkhere/drivewatch/FamilyApi.kt` | 가족 서버(`drive.olvend.com`): 등록, 초대 코드·문자, 주간 요약, 가족에게 부탁, 앱 새 버전 |
| `app/src/main/java/com/parkhere/drivewatch/Backup.kt` | 운전자 목록을 차에 저장, 운전자가 허락하면 주행 요약을 서버에 보관·복원 |
| `app/src/main/java/com/parkhere/drivewatch/CarKey.kt` | 차 키: 차대번호(없으면 기기 ID)의 HMAC. 다시 설치해도 같은 서버 운전자로 이어진다 |
| `app/src/main/java/com/parkhere/drivewatch/Replay.kt` | 시연 재생: 서버의 주행·과거 기록 시나리오를 실제 신호와 같은 입구로 흘린다 |
| `app/src/main/java/com/parkhere/drivewatch/ClockCar.kt` | 시계 차 캐릭터, 상태 배지와 로딩 표시 |
| `app/src/main/java/com/parkhere/drivewatch/Theme.kt` | Pleos 색 토큰, 낮·밤 테마, 화면 전환 시간 |
| `app/src/main/java/com/parkhere/drivewatch/Icons.kt`, `Qr.kt` | SVG 아이콘 목록(`app/design/icons/`, `tools/svg2vector.py`로 변환), 초대 QR |
| `app/src/main/java/com/parkhere/drivewatch/Demo.kt` | 시연용 과거 기록, 목적지별 내비 안내 스크립트 |
| `app/src/main/java/com/parkhere/drivewatch/DemoDriver.kt` | `시연 주행 시작` 버튼: Virtual Console로 차를 움직인다 |
| `app/src/main/java/com/parkhere/drivewatch/MainActivity.kt` | 차량 화면 C1~C14(월별 기록·운전 졸업 포함)와 시연 패널 |
| `app/src/test/java/com/parkhere/drivewatch/` | 판정 규칙, 시연 시나리오, 운전자 ID, 차 키 테스트 |
| `tools/demo-drive.py` | 앱 버튼과 같은 시연 주행을 터미널에서 실행 |
| `docs/mvp-test-guide.md` | 테스트 순서와 확인할 값 |

## APK

`./gradlew :app:assembleRelease` → `app/build/outputs/apk/release/app-release.apk`. 제출용은 `apk/M92_DriveWatch_release.apk`다(팀명 M92). 최종본은 main에 병합한 뒤 다시 빌드해 이 이름으로 넣는다.

- **버전**: versionCode는 git 커밋 수, versionName은 `0.2.0+<커밋>`이다. 시연 패널에 표시된다.
- **서명**: `~/.drivewatch/signing.properties`가 있으면 팀 공용 키로 서명한다(저장소 밖에 두고 팀원에게 따로 전달). 없으면 디버그 키로 서명하고, 이때는 팀 키로 설치된 앱 위에 덮어 설치할 수 없다.
- **업데이트**: `family-server/tools/publish-apk.sh <apk> "바뀐 점"`으로 서버에 올리면, 차량 앱이 켜질 때 "새 버전" 알림과 내려받기 QR을 띄운다. 앱이 스스로 설치하지는 않는다.
