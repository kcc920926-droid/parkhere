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
| `FamilyLink` | 가족 요약 서버를 부르는 입구. 통신은 뒤에서 하고 결과는 메인 스레드로 돌려준다. 등록 정보는 SharedPreferences에 남는다 |

**수집은 반드시 포그라운드 서비스에서 해야 한다.** 주행을 시작하면 시스템이 내비를 화면 앞으로 가져오고, 화면에 없는 앱은 프로세스가 멈춘다. 액티비티에서 신호를 받으면 주행 중에 아무것도 받지 못한다. 에뮬레이터에서 확인했다.

```kotlin
DriveCollectorService.configure(DriveCollectorService.Setup(history, CollectorConfig(), context = "city"))
DriveCollectorService.start(context)          // 화면이 앞에 있을 때 부른다
// bindService로 연결한 뒤
val state = service.state                     // 진행 중인 구간, 끝난 구간들, 마지막 주행 결과
state.lastReport?.detection                   // 주차 후 카드의 근거
```

차속 권한 `android.car.permission.CAR_SPEED`는 실행 중에 사용자에게 요청해야 한다. 없으면 강한 제동을 셀 수 없고, `state.issue`에 이유가 한 줄로 들어온다.

## 가족 서버 연결

`FamilyLink`는 `drivewatch-core`의 `FamilySession`을 Android에서 쓰기 좋게 감싼 것이다. 권한은 `INTERNET` 하나이고 라이브러리의 매니페스트에 들어 있다.

```kotlin
val family = FamilyLink(context)                       // 기본 주소는 https://drive.olvend.com
if (family.hasPending) family.retryPending { }         // 앱을 열 때: 지난번에 못 보낸 요약을 다시 보낸다

family.register("김정숙", ShareScope.STATUS) { result ->            // 처음 한 번
    family.invite { invite ->                                       // 가족 연결 화면
        invite.getOrNull()?.let { show(it.display, family.joinUrl(it)) }   // "123 456"과 QR 주소
    }
}
family.send(FamilySummary.from(weeklyCard)) { result ->             // `가족에게 보내기`
    val error = result.exceptionOrNull() as? FamilyError
    if (error?.retryable == true) { /* "통신이 되면 보낼게요" */ }
}
family.view { result -> result.getOrNull()?.latest?.ackedBy }       // 누가 확인했는지
```

- 결과는 메인 스레드에서 `Result`로 온다. 실패하면 `FamilyError`다.
- 공개 주소는 https다. http 주소(개발 PC의 서버)는 앱 매니페스트에 `usesCleartextTraffic`이 있어야 붙는다. 확인용 앱에만 넣었다.
- 등록 정보(차 토큰)는 앱의 SharedPreferences에 평문으로 남는다. 앱 데이터를 지우면 새 차로 등록된다.

## 빌드와 확인

JDK 17 또는 21이 필요하다. `drivewatch-core`는 옆 폴더에서 바로 가져온다(`includeBuild`).

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./gradlew :harness:assembleDebug
adb install -r harness/build/outputs/apk/debug/harness-debug.apk
adb forward tcp:8088 tcp:8088
node ../tools/vconsole-demo.js      # Virtual Console로 시연 주행을 재생
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

### 가족 서버 연결 확인

개발 PC에서 가족 서버를 켜고, 확인용 앱을 그 주소로 실행한다. 에뮬레이터에서 개발 PC는 `10.0.2.2`다.

```sh
(cd ../family-server && npm start)       # 다른 터미널에서. http://127.0.0.1:8795
adb shell am start -n com.drivewatch.harness/.MainActivity --es familyServer http://10.0.2.2:8795
adb logcat -s DWFAMILY
```

`가족 등록·코드` → 폰 화면(<http://127.0.0.1:8795/family/>)에서 가입하고 코드 입력 → `요약 보내기 (시연용)` → 폰에서 `확인했어요` → `가족 확인 보기` 순서로 누른다.

```
code 937487 · 폰에서 열 주소 http://10.0.2.2:8795/family/?code=937487
sent week=2026-11-02 status=talk evidenceStored=2
family=[민지] ackedBy=[민지] pending=false
```

서버를 끄고 `요약 보내기`를 누르면 `실패: offline (0)`이 나오고 `pending=true`가 된다. 서버를 켜고 앱을 다시 열면 `resent week=…`이 찍힌다. 주소를 주지 않고 실행하면 공개 서버에 붙으니, 시연 직전이 아니면 로컬 주소를 준다.

## 확인하지 않은 것

- 실차. 에뮬레이터(Pleos Connect v2.1.0)에서만 확인했다.
- 포그라운드 서비스와 `specialUse` 유형이 Pleos App Market 심사에서 허용되는지.
- 재부팅 뒤 자동 시작. 지금은 앱을 한 번 열어야 서비스가 시작된다.
- 도로 조건(도심 / 고속도로) 구분. 지금은 시작할 때 정한 값 하나를 쓴다.
- 에뮬레이터 앱에서 공개 주소(https://drive.olvend.com)로 보내는 것. 앱은 개발 PC의 서버(http)로만 확인했다. 공개 주소는 PC에서 같은 Kotlin 코드로 닿는 것까지만 봤다.
- 실차의 통신 환경. 통신이 끊겼다 돌아오는 상황은 서버를 껐다 켜서 흉내 냈다.
