# DriveWatch

**부모님 운전에 차는 스마트워치.** 차량 신호로 부모님 운전의 *나이*가 아니라 *본인 평소 대비 변화*를 알아채고, 불편했던 길은 다음에 피하게 돕고, 자녀에게는 감시가 아닌 요약을 보내는 Pleos Connect 차량용 앱입니다. 현대자동차그룹 차량용 앱 개발 공모전 출품작입니다.

- [플레이북](PLAYBOOK.MD) · 서비스 흐름, 공모전 MVP, 차량 데이터의 역할
- [태스크리스트](TASKLIST.MD) · 검증과 구현 순서
- [최종 기획서](opinions/drive-watch-final-plan.md) · 심사 4항목 순서의 기획 원본
- [회의록](meetings/) · 결정 기록
- [이전 기획 · 어디대?](eodidae_old/PLAYBOOK.MD) · 목적지에 맞는 주차 구역 안내와 주차 위치 기록
- [이전 기획 · TANMAN.KM](tanman.km_old/PLAYBOOK.MD) · 주행거리 기반 동승 비용 정산

## 드라이브 와쳐 웹 MVP

도심과 고속도로의 합성 주행 기록으로 개인 기준선, 연속 초과 감지, 감지 근거를 시연합니다.

- [공개 시연](https://drive.olvend.com/) · [소스](drive-watcher/)
- [웹 MVP 플레이북](drive-watcher/PLAYBOOK.MD)

## 구성

- [drivewatch-core](drivewatch-core/README.md) · 구간 판정과 주간 판정을 옮긴 Kotlin 모듈. 웹 MVP와 결과가 같은지 테스트로 확인합니다. 카드 문구와 가족 서버 호출도 여기에 있습니다.
- [drivewatch-vehicle](drivewatch-vehicle/README.md) · 차량 신호를 받고 가족 서버에 요약을 보내는 Android 라이브러리와 확인용 앱. 수집은 포그라운드 서비스에서 합니다.
- [family-server](family-server/README.md) · 가족 요약 서버와 자녀 폰 웹 화면. [폰 화면](https://drive.olvend.com/family/) · [REST API 명세](https://drive.olvend.com/api/docs)
- [Pleos 에뮬레이터 신호 확인 기록](docs/pleos-signal-check.md) · 차속·기어·브레이크·조향·방향지시등을 받는 방법과 확인 결과
- [시연 데이터와 시연 이벤트](docs/demo-data.md) · 시연에서 무엇이 가짜이고 무엇이 실제 코드인지
- [시연 영상 대본과 촬영 순서](docs/demo-video-script.md) · 장면별 화면·내레이션·자막, 촬영 전 확인할 것
- [시연 영상 컷별 컨셉 시트](docs/demo-video-concept-sheets.md) · 컷마다의 구도·움직임·글자·소리와 참고한 광고 연출 경향
- [접수양식 초안](submission/application.md) · 기획서 원문과 양식에 넣는 스크립트

## 지금 구현된 범위

| 부분 | 상태 |
| --- | --- |
| 구간 판정, 주간 판정, 카드 문구 (Kotlin) | 구현. 웹 MVP와 결과가 같은지 테스트로 확인 |
| 차량 신호 수집 (기어·차속·브레이크·조향) | 구현. Pleos Connect 에뮬레이터에서 확인 |
| 가족 요약 서버와 자녀 폰 웹 화면 | 구현. `drive.olvend.com`에 배포 |
| 차량 앱에서 가족 서버로 요약 보내기 | 구현. 에뮬레이터에서 확인 |
| 차량 앱의 제품 화면 (주차 후 카드, 주간 카드, 가족 연결) | 작업 중. 지금 저장소의 Android 앱은 동작을 확인하는 화면이다 |
| 운전 약속, 불편 구간 기억 | 설계 |

모든 확인은 에뮬레이터에서 했고, 실차에서 확인한 것은 없습니다. 평소 기록은 합성 기록입니다.

## 실행 방법

### 설치 없이 보기

- 웹 시연: <https://drive.olvend.com/>
- 자녀 폰 화면: <https://drive.olvend.com/family/>
- REST API 명세: <https://drive.olvend.com/api/docs>

### 차량 앱 (Pleos Connect 에뮬레이터)

Pleos Connect 에뮬레이터가 켜져 있어야 합니다. 준비는 아래 [테스트 환경과 설정](#테스트-환경과-설정)에 있습니다.

```sh
cd drivewatch-vehicle
./gradlew :harness:assembleDebug
adb install -r harness/build/outputs/apk/debug/harness-debug.apk
adb shell am start -n com.drivewatch.harness/.MainActivity
```

처음 실행하면 차속과 알림 권한을 묻습니다. 둘 다 허용합니다.

시연 주행은 두 가지 방법으로 재생합니다. 48초짜리 같은 주행입니다.

```sh
adb forward tcp:8088 tcp:8088
node ../tools/vconsole-demo.js      # Virtual Console로 차량 신호를 넣는다
```

Virtual Console 없이 보려면 앱의 `녹화 신호 (시연용)` 버튼을 누릅니다.

주행이 끝나면 화면에 아래 카드가 나옵니다.

```
평소와 달라요
강한 제동 · 8초 구간당 평소 3회 이하, 이번 최고 5회
출발 후 24–32초 4회, 출발 후 32–40초 5회
```

가족에게 요약을 보내는 흐름은 [drivewatch-vehicle](drivewatch-vehicle/README.md#가족-서버-연결-확인)에 있습니다.

### 가족 요약 서버

```sh
cd family-server
npm start                           # http://127.0.0.1:8795/family/ , 명세는 /api/docs
```

설치할 패키지는 없습니다. 차량 앱 없이 차량 역할을 해 보는 방법은 [family-server](family-server/README.md#차량-앱-없이-돌려-보기)에 있습니다.

### 웹 시연을 로컬에서

```sh
python3 -m http.server 8774 --bind 127.0.0.1    # 저장소 루트에서. http://127.0.0.1:8774/drive-watcher/
```

### 테스트

```sh
node --test drive-watcher/*.test.js             # 웹 MVP 27개
(cd family-server && npm test)                  # 가족 서버 13개
(cd drivewatch-core && ./gradlew test)          # 판정·수집·카드·가족 연결 45개
```

## 테스트 환경과 설정

아래 환경에서 확인했습니다.

| 항목 | 값 |
| --- | --- |
| Pleos Connect 에뮬레이터 버전과 화면 규격 | v2.1.0 (Android 14 · API 34, arm64) · 2560×1440 |
| 차량 SDK | Pleos Vehicle SDK 2.3.2, AAOS Car API (차속) |
| Android | minSdk 28, compileSdk 35, targetSdk 35 |
| 빌드 | Gradle 8.10.2 (저장소의 래퍼), Android Gradle Plugin 8.8.2, Kotlin 2.0.21 |
| JDK | 21 |
| Node.js | 20 이상 (22.23.1로 확인) |
| 개발 PC | macOS, Apple Silicon |

### 준비

- **JDK 21.** Gradle 8.10.2는 최신 JDK(예: 27)에서 실행되지 않습니다. 기본 `java`가 더 새 버전이면 `JAVA_HOME`을 JDK 21로 지정합니다.
- **Android SDK 위치.** `ANDROID_HOME`을 지정하거나 `drivewatch-vehicle/local.properties`에 `sdk.dir=…`을 적습니다.
- **Pleos Connect 시스템 이미지.** Android Studio의 SDK Manager에 아래 주소를 사용자 사이트로 추가하고 이미지를 받은 뒤, 그 이미지로 가상 기기를 만듭니다. 받을 때 42dot SDK 라이선스 동의가 필요합니다.
  `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml`
- **Vehicle SDK.** Maven 저장소 주소는 `drivewatch-vehicle/settings.gradle.kts`에 들어 있습니다.
- **Virtual Console.** 에뮬레이터 안의 웹 화면입니다. `adb forward tcp:8088 tcp:8088` 뒤에 <http://127.0.0.1:8088/>로 열어 기어·차속·브레이크·조향을 손으로 넣거나, `tools/vconsole-demo.js`로 같은 주행을 되풀이합니다. [Pleos Virtual Console 가이드](https://document.pleos.ai/en/docs/pleos-only/app-test-guide/virtual-console-guide)

### 앱이 쓰는 권한

| 권한 | 쓰는 곳 | 허용 방식 |
| --- | --- | --- |
| `pleos.car.permission.CAR_DRIVE` | 기어, 브레이크 눌림, 조향각 | 설치할 때 |
| `android.car.permission.CAR_SPEED` | 차속 | 실행 중에 사용자가 허용 |
| `android.permission.FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | 주행 중 화면 뒤에서 신호 받기 | 설치할 때 |
| `android.permission.POST_NOTIFICATIONS` | 수집 중 알림 | 실행 중에 사용자가 허용 |
| `android.permission.INTERNET` | 가족에게 주간 요약 보내기 | 설치할 때 |
| `ai.pleos.playground.service.vehicle.VEHICLE_BINDING` | Vehicle SDK가 차량 서비스에 연결 | SDK가 넣는다 |

빌드한 APK의 권한 목록으로 확인한 것입니다. 위치 권한은 쓰지 않습니다.

### 시연 설정

- 시연에서는 판정 구간을 2분에서 8초로 줄입니다. 판정 기준은 그대로입니다.
- 평소 기록은 합성 기록이고, 화면에 시연 모드라고 표기합니다.
- 무엇이 가짜이고 무엇이 실제 코드인지는 [시연 데이터와 시연 이벤트](docs/demo-data.md)에, 신호를 받는 방법과 확인 결과는 [신호 확인 기록](docs/pleos-signal-check.md)에 있습니다.

## APK

- 제출용 APK: `apk/팀명_DriveWatch_release.apk` (실행할 수 있는 release 빌드). 제품 화면이 들어간 뒤에 추가합니다. 지금은 없습니다.
- 지금 빌드되는 APK: 확인용 앱 `drivewatch-vehicle/harness/build/outputs/apk/debug/harness-debug.apk`. 위의 [차량 앱](#차량-앱-pleos-connect-에뮬레이터) 명령으로 만듭니다.
