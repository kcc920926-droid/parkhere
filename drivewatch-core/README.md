# drivewatch-core

DriveWatch의 판정 로직을 담은 순수 Kotlin 모듈입니다. 웹 MVP(`drive-watcher/engine.js`, `drive-watcher/weekly.js`)를 그대로 옮겼고, Android 의존성이 없어서 어느 Android 앱에서든 가져다 쓸 수 있습니다.

| 파일 | 웹 MVP 원본 | 하는 일 |
| --- | --- | --- |
| `TripEngine.kt` | `engine.js` | 2분 구간을 같은 도로 조건의 개인 기준선과 비교하고, 같은 신호가 두 구간 연속 넘으면 변화로 기록 |
| `WeeklyEngine.kt` | `weekly.js` | 주행 요약을 주별로 모아 학습 중 / 자료 부족 / 안정 / 관찰 / 대화 권장을 판정 |
| `collect/` | 없음 | 차속·브레이크·조향·기어 신호를 구간 요약으로 묶고 판정까지 잇는다 |
| `card/Cards.kt` | 없음 | 도착 카드와 주간 카드에 쓸 문구를 만든다. 화면은 이 문구를 그리기만 하면 된다 |
| `family/` | 없음 | 주간 카드를 가족에게 보낼 요약으로 바꾸고, 가족 요약 서버의 차량 쪽 API를 부른다 |
| `demo/DemoReplays.kt` | `replays.js` | 시연용 도심·고속도로 합성 기록 |
| `demo/DemoWeeklyHistory.kt` | `weekly-history.js` | 시연용 합성 12주 기록 |
| `demo/DemoDrive.kt` | 없음 | 시연용 녹화 신호. 구간별 강한 제동이 1·0·1·4·5·1회인 주행 |

기준값과 여유는 웹 MVP와 같은 시연용 파라미터입니다. 실차에서 쓸 값은 별도 검증이 필요합니다.

## 수집기

`collect/`는 웹 MVP에 없던 부분이다. 차량에서 오는 신호를 받아 구간마다 `Frame` 하나로 만든다.

| 무엇 | 세는 방법 |
| --- | --- |
| 강한 제동 | 브레이크가 눌린 동안 차속이 4m/s² 이상으로 줄면 한 번. 브레이크를 뗐다 다시 밟아야 다음을 센다. 감속도는 0.5초 전 차속과 비교한다 |
| 조향 보정 | 조향각이 방향을 바꿔 5도 이상 되돌아오면 한 번. 시속 10km 아래에서는 세지 않는다 |
| 누적 주행 시간 | 차가 움직인 시간을 쌓는다. P단으로 5분 이상 서 있으면 0으로 돌린다 |

- 주행은 D단에 들어갈 때 시작하고, P단으로 멈추면 끝난다. 끝나지 않은 구간은 판정하지 않는다.
- 신호가 없으면 0이 아니라 값 없음으로 둔다. 판정은 그 구간을 비교하지 않는다.
- 브레이크 신호가 아예 없으면 감속도만으로 센다.
- `CollectorConfig.demo()`는 구간만 8초로 줄인다. 나머지 기준은 같다.

강한 제동의 기준은 웹 MVP의 시연 기준을 따랐다. 조향 보정은 웹 MVP에 정의가 없어서 새로 정했다. 둘 다 실차 검증이 필요하다.

```kotlin
val monitor = DriveMonitor(history, CollectorConfig.demo(), context = "city")
monitor.accept(SpeedSample(timeMs, metersPerSecond))   // 신호가 올 때마다
// MonitorUpdate.WindowJudged: 구간 하나가 끝나 판정됨
// MonitorUpdate.Arrived: 주차함. report.detection이 도착 카드의 근거
```

Android에서 신호를 받는 부분은 [drivewatch-vehicle](../drivewatch-vehicle/README.md)에 있다.

## 카드 문구

화면이 판정 결과를 직접 해석하지 않도록, 문구를 만드는 부분을 따로 뒀다. 어떤 화면 기술을 쓰든 가져다 쓸 수 있다.

```kotlin
val arrival = Cards.arrival(report)           // 주차 후
arrival.title        // "평소와 달라요"
arrival.evidence     // "강한 제동 · 2분 구간당 평소 3회 이하, 이번 최고 5회"
arrival.where        // "출발 후 6–8분 4회, 8–10분 5회"
arrival.windows      // 구간 상세에 그릴 막대들

val weekly = Cards.weekly(WeeklyEngine.assess(trips, context = "city"))   // 이번 주 카드
weekly?.chip         // "안정" | "관찰" | "대화 권장" | "학습 중" | "자료 부족"
weekly?.headline     // "강한 제동이 4주째, 조향 보정이 2주째 평소보다 많았어요"
weekly?.evidence     // 숫자 근거. 가족 서버의 evidence 항목과 같은 모양이다
weekly?.strip        // 지난 주들의 상태 띠
```

구간이 1분보다 짧으면(시연 모드) 문구가 초 단위로 나온다.

## 가족 서버 연결

`family/`는 [가족 요약 서버](../family-server/README.md)의 차량 쪽 API를 부른다. JDK와 Android에 모두 있는 `HttpURLConnection`만 쓰고, JSON도 직접 다뤄서 의존성이 없다.

| 클래스 | 하는 일 |
| --- | --- |
| `FamilySummary` | 서버에 보내는 주간 요약. `FamilySummary.from(card)`가 주간 카드를 그대로 옮긴다. 차에서 본 문구와 가족이 받는 문구가 같다 |
| `FamilyClient` | 등록, 초대 코드, 공유 범위, 요약 보내기, 가족 확인 읽기. 호출 하나가 API 하나다 |
| `FamilySession` | 등록 정보를 차에 저장하고, 보내지 못한 요약을 남겨 뒀다가 다시 보낸다 |

```kotlin
val session = FamilySession(FamilyClient(), storage)          // 기본 주소는 https://drive.olvend.com
session.register("김정숙", ShareScope.STATUS)                  // 처음 한 번. 다시 불러도 서버를 부르지 않는다
val invite = session.invite()                                  // invite.display = "123 456", 10분 동안 한 번
session.joinUrl(invite)                                        // QR에 담을 주소
session.send(FamilySummary.from(weeklyCard, promise = "…"))    // 운전자가 `가족에게 보내기`를 눌렀을 때
session.view()                                                 // 연결된 가족, 마지막 요약을 확인한 가족
```

- 모든 호출은 답이 올 때까지 기다린다. Android에서는 [drivewatch-vehicle](../drivewatch-vehicle/README.md)의 `FamilyLink`가 뒤에서 불러 준다.
- 실패하면 `FamilyError`가 나온다. `code`는 서버의 오류 코드이고, 서버에 닿지 못했으면 `offline`이다.
- 서버에 닿지 못했거나 서버가 잠시 못 받으면(5xx, 429) 요약을 남겨 둔다. `retryPending()`이 다시 보낸다. 서버가 거절한 요약(4xx)은 남기지 않는다.
- `unauthorized`가 오면 서버가 이 차를 모른다는 뜻이다. `forget()` 뒤에 다시 등록한다.
- 요약에는 위치와 경로가 들어갈 자리가 없다. 테스트가 보내는 항목 이름을 확인한다.
- 같은 주에 다시 보내면 서버가 덮어쓰고 가족의 확인 표시를 지운다. 그래서 자동으로 보내지 않고, 운전자가 누를 때만 보낸다.

## 테스트

JDK 17 또는 21이 필요합니다. Gradle 8.10.2는 그보다 새 JDK에서 실행되지 않습니다.

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew test
```

- `TripEngineTest`, `WeeklyEngineTest`: 웹 MVP의 `engine.test.js`, `weekly.test.js`를 옮긴 테스트입니다. 지표 하나가 4주 이어지는 경우는 여기에만 있습니다.
- `CollectorTest`, `DriveMonitorTest`: 수집기와, 신호에서 도착 결과까지 이어지는 흐름을 확인합니다.
- `CardsTest`: 카드 문구가 웹 MVP와 기획서의 문구와 같은지 확인합니다.
- `FamilyTest`: 요약 본문, 요청 주소와 토큰, 오류 구분, 등록 정보 저장, 보내지 못한 요약의 재전송을 확인합니다. 실제 HTTP로 나가는지는 테스트 안에서 띄운 작은 서버로 봅니다.
- `ParityTest`: 웹 MVP가 낸 결과(`src/test/resources/parity/expected.txt`)와 Kotlin 결과를 한 줄씩 비교합니다. 시연 기록과 고정 시드 난수 기록(구간 300개, 주행 150회)을 씁니다.

웹 MVP의 판정 로직을 고치면 기준 결과를 다시 만듭니다. 난수 순서는 `tools/dump-parity.js`와 `ParityDump.kt`가 같아야 합니다.

```sh
node tools/dump-parity.js > src/test/resources/parity/expected.txt
```

켜져 있는 가족 서버에 실제로 보내 보려면 `familyCheck`를 씁니다. 테스트에는 포함되지 않습니다.

```sh
(cd ../family-server && npm start)                                   # 다른 터미널에서
./gradlew familyCheck -Pserver=http://127.0.0.1:8795 -Pwait=30       # 등록 → 코드 → 요약 → 가족 확인 기다리기
./gradlew familyCheck -Pserver=https://drive.olvend.com -Pping=true  # 공개 서버에 닿는지만 본다. 아무것도 쓰지 않는다
```

## 쓰는 법

```kotlin
val session = TripEngine.createSession(history)          // 같은 운전자의 과거 구간 기록
val result = session.ingest(frame)                        // 2분 구간을 시간 순서대로
result.detection                                          // 처음 감지한 구간과 근거, 없으면 null

val trip = WeeklyEngine.tripFromFrames(frames, date)      // 주행 한 번의 요약
val weeks = WeeklyEngine.assess(trips, context = "city")  // 주별 상태
```
