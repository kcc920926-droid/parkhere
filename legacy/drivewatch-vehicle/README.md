# drivewatch-vehicle

차량 신호를 받아 `drivewatch-core`의 수집기에 넣는 Android 부분입니다.

| 모듈 | 내용 |
| --- | --- |
| `vehicle/` | Android 라이브러리. 차량 앱이 가져다 쓴다 |
| `harness/` | 확인용 앱. 수집과 판정이 에뮬레이터에서 도는지 보는 화면이고, 제품 화면이 아니다 |

## vehicle 모듈

| 클래스 | 하는 일 |
| --- | --- |
| `VehicleSignalSource` | 기어·브레이크·조향각은 Pleos Vehicle SDK에서, 차속은 AAOS Car API에서 받는다 |
| `ReplaySignalSource` | 녹화한 신호를 같은 입구로 흘린다. 시연 예비용 |
| `DriveCollectorService` | 포그라운드 서비스. 화면 뒤에서 신호를 받아 구간마다 판정하고, 마지막 주행 결과를 들고 있는다 |

**수집은 반드시 포그라운드 서비스에서 해야 한다.** 주행을 시작하면 시스템이 내비를 화면 앞으로 가져오고, 화면에 없는 앱은 프로세스가 멈춘다. 액티비티에서 신호를 받으면 주행 중에 아무것도 받지 못한다. 에뮬레이터에서 확인했다.

```kotlin
DriveCollectorService.configure(DriveCollectorService.Setup(history, CollectorConfig(), context = "city"))
DriveCollectorService.start(context)          // 화면이 앞에 있을 때 부른다
// bindService로 연결한 뒤
val state = service.state                     // 진행 중인 구간, 끝난 구간들, 마지막 주행 결과
state.lastReport?.detection                   // 주차 후 카드의 근거
```

차속 권한 `android.car.permission.CAR_SPEED`는 실행 중에 사용자에게 요청해야 한다. 없으면 강한 제동을 셀 수 없고, `state.issue`에 이유가 한 줄로 들어온다.

## 빌드와 확인

JDK 17 또는 21이 필요하다. `drivewatch-core`는 옆 폴더에서 바로 가져온다(`includeBuild`).

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./gradlew :harness:assembleDebug
adb install -r harness/build/outputs/apk/debug/harness-debug.apk
adb forward tcp:8088 tcp:8088
node ../vconsole-demo.js      # Virtual Console로 시연 주행을 재생
adb logcat -s DWCOLLECT
```

기대하는 기록:

```
trip started
window 1 hardBrakes=1 ... observation=typical
window 4 hardBrakes=4 ... observation=watching
window 5 hardBrakes=5 ... observation=change
arrived windows=6 detection=hardBrakes@40s
```

확인용 앱의 `녹화 신호 (시연용)` 버튼을 누르면 Virtual Console 없이 같은 주행이 재생된다.

## 확인하지 않은 것

- 실차. 에뮬레이터(Pleos Connect v2.1.0)에서만 확인했다.
- 포그라운드 서비스와 `specialUse` 유형이 Pleos App Market 심사에서 허용되는지.
- 재부팅 뒤 자동 시작. 지금은 앱을 한 번 열어야 서비스가 시작된다.
- 도로 조건(도심 / 고속도로) 구분. 지금은 시작할 때 정한 값 하나를 쓴다.
