# drivewatch-core

DriveWatch의 판정 로직을 담은 순수 Kotlin 모듈입니다. 웹 MVP(`drive-watcher/engine.js`, `drive-watcher/weekly.js`)를 그대로 옮겼고, Android 의존성이 없어서 어느 Android 앱에서든 가져다 쓸 수 있습니다.

| 파일 | 웹 MVP 원본 | 하는 일 |
| --- | --- | --- |
| `TripEngine.kt` | `engine.js` | 2분 구간을 같은 도로 조건의 개인 기준선과 비교하고, 같은 신호가 두 구간 연속 넘으면 변화로 기록 |
| `WeeklyEngine.kt` | `weekly.js` | 주행 요약을 주별로 모아 학습 중 / 자료 부족 / 안정 / 관찰 / 대화 권장을 판정 |
| `demo/DemoReplays.kt` | `replays.js` | 시연용 도심·고속도로 합성 기록 |
| `demo/DemoWeeklyHistory.kt` | `weekly-history.js` | 시연용 합성 12주 기록 |

기준값과 여유는 웹 MVP와 같은 시연용 파라미터입니다. 실차에서 쓸 값은 별도 검증이 필요합니다.

## 테스트

JDK 17 또는 21이 필요합니다. Gradle 8.10.2는 그보다 새 JDK에서 실행되지 않습니다.

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew test
```

- `TripEngineTest`, `WeeklyEngineTest`: 웹 MVP의 `engine.test.js`, `weekly.test.js`를 옮긴 테스트입니다. 지표 하나가 4주 이어지는 경우는 여기에만 있습니다.
- `ParityTest`: 웹 MVP가 낸 결과(`src/test/resources/parity/expected.txt`)와 Kotlin 결과를 한 줄씩 비교합니다. 시연 기록과 고정 시드 난수 기록(구간 300개, 주행 150회)을 씁니다.

웹 MVP의 판정 로직을 고치면 기준 결과를 다시 만듭니다. 난수 순서는 `tools/dump-parity.js`와 `ParityDump.kt`가 같아야 합니다.

```sh
node tools/dump-parity.js > src/test/resources/parity/expected.txt
```

## 쓰는 법

```kotlin
val session = TripEngine.createSession(history)          // 같은 운전자의 과거 구간 기록
val result = session.ingest(frame)                        // 2분 구간을 시간 순서대로
result.detection                                          // 처음 감지한 구간과 근거, 없으면 null

val trip = WeeklyEngine.tripFromFrames(frames, date)      // 주행 한 번의 요약
val weeks = WeeklyEngine.assess(trips, context = "city")  // 주별 상태
```
