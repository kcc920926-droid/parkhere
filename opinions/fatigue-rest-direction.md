# 피로 → 휴식 코치 (가칭) · 방향 정리

작성 winter1 · 2026-11-04 · 상태: **방향 합의, 검증 전**. 아직 [PLAYBOOK.MD](../PLAYBOOK.MD)(어디대?)를 대체하지 않는다.

## 1. 왜 방향을 바꾸나

어디대?(주차 위치 안내)는 시연은 쉽지만 수상 경쟁력이 낮다고 판단했다.

- **창의성 약함**: 블루링크 주차 위치 확인, 인천공항 주차내비, 폰 사진으로 이미 해결되는 문제다.
- **사업성 약함**: 공항·대형시설 방문은 연 몇 회라 MAU·Retention 근거가 약하다.
- **해결 효과가 작음**: 몇 분 절약하는 수준이다.

## 2. 공모전 핵심 요약

| 항목 | 내용 |
| --- | --- |
| 접수 | 2026-10-21 ~ **11-15(일)** 이메일 `appdevchallenge@hyundai-partners.com` |
| 1차 발표 | 11-23 (20팀) |
| 2차 PT | 12-03 ~ 04 오프라인, **실제 동작 앱 시연 필수**(에뮬레이터 가능) |
| 최종 발표 | 12-18 (10팀), 2027-02 그룹 행사 전시·시상 (전시용 APK 필수, 현장 참석 의무) |
| 필수 제출 | 접수양식(DOCX = 기획서) + 팀원 전원 개별 서명 동의서, 팀원 전원 CC |
| 가산점 | 시연 영상(목업·화면 녹화 인정), GitHub(소스 + `apk/팀명_앱이름_release.apk`) |
| GitHub README 필수 항목 | 앱 이름·한 줄 소개, 실행 방법, 테스트 환경·설정, APK 위치 |
| 개발 환경 | Android API 28+, Gradle 8.0+, Kotlin/Java |
| 상금 | 총 5,100만원, 대학생·일반 부문 분리, 대상 1,000만원 |

## 3. 심사 기준 4가지와 해석

| 기준 | 공식 문구 요지 | 우리의 해석 · 판정 질문 |
| --- | --- | --- |
| **차량특화성** | 모바일 대비 차량 환경만의 가치, 주행·정차 맥락과 차량 기능의 유기적 결합 | *"대시보드에 폰을 거치하면 끝나는 앱인가?"* 그렇다면 탈락. 같은 앱이 **주행 / 정차 / P단에서 다르게 동작**하는 것이 '유기적 결합'이다. 기획서에 "한 번의 이동 타임라인"과 "모바일로 안 되는 이유" 표를 넣는다. |
| **창의성** | 독창적 아이디어, Pain Point·Needs 정의와 해결 방식의 명확성 | 새 기술이 아니라 *익숙한 니즈 × 차량 맥락*의 새 조합이면 충분하다. Pain은 누가·언제·얼마나 자주·지금은 어떻게 버티는지까지 구체적으로 쓴다(인터뷰·커뮤니티 근거). 공식 예시 3개(음성 메모·안전운전 점수·배달), 범용 "AI 비서", 주차 찾기는 경쟁이 몰리는 영역이다. |
| **기술 활용성** | Pleos SDK·API를 핵심 기능과 경험에 적절히 활용 | SDK 개수가 아니라 *"빼면 앱이 죽는가"*. 자연스러운 3역할 조합은 **Vehicle = 트리거, Navi·Location = 맥락, TTS·STT = 인터페이스**. PT에서는 Virtual Console로 상태를 바꾸면 앱이 반응하는 장면이 가장 강한 증거다. 공모전명이 "Pleos App Market"이므로 실제 탑재 가능성도 은근히 본다. |
| **사업성** | 명확한 타깃, 사용자 확보·지속 이용·확대 (MAU, Retention, 확대 계획) | 모수는 Pleos 탑재 차량 소유자로 제한되므로 *탑재 차종·출시 계획 → 설치율 → 사용률*로 MAU를 쌓는다. **Retention은 빈도에서 나온다**: 매 이동 쓰는 앱 > 연 몇 회 쓰는 앱. 심사위원이 현대차그룹이므로 차량 가치·생태계를 키우는 확장 이야기가 유리하다. |

**교집합 원칙**: 수상권 아이디어는 *매 이동마다 오는 순간 × 차만 아는 신호 × 선명한 불편*에 있다.

## 4. 새 방향: 피로 → 휴식 코치

### 4.1 출발점과 전환

처음 아이디어는 **휴게소 안내 앱**이었다(화장실·유가 점수, 휴게소 간식 추천, 대상은 운송업·장거리 여행객). 그대로는 티맵·카카오내비·도로공사 앱과 겹쳐 창의성이 낮고, 폰으로도 똑같이 되는 정보 앱이라 차량특화성·기술 활용성도 약하다.

그래서 중심을 **"어느 휴게소"에서 "이 운전자가 지금 얼마나 지쳤는지 차가 알아채고 먼저 쉬게 만드는 것"**으로 옮긴다. 휴게소 정보는 휴식 이후의 보조 기능으로 내린다.

### 4.2 타깃

- 운송업(화물)은 Retention이 높아 끌리지만, **현재 Pleos Connect 탑재 계획에 상용차가 없다**(첫 탑재 2026-05 더 뉴 그랜저, 이어 아반떼·투싼, 기아 2027~).
- 따라서 1차 타깃은 **승용차로 매일 오래 운전하는 사람**: 외근 영업직, 장거리 출장자, 택시·대리 기사. 첫 탑재 차종이 그랜저라 법인차 영업직·출장자와 잘 맞는다.
- 화물차는 **"상용차 탑재 시 확장"**으로 사업성 확대 계획에 둔다.

### 4.3 한 번의 이동에서의 경험

| 순간 | 앱 동작 | 쓰는 SDK |
| --- | --- | --- |
| 출발·주행 중 | 연속 운전 시간, 시간대(새벽·식후), 남은 경로를 조용히 추적한다. 화면은 내비 옆 좁은 창에 "연속 1시간 52분 · 다음 휴게소 23km" 정도만 둔다. | Vehicle(Odometer·기어), NaviHelper, FusedLocation |
| 피로 의심 시점 | TTS로 짧은 대화(퀴즈·일정 확인·끝말잇기)를 건네고, 그 직후에만 STT를 연다. **응답 지연·정확도를 피로 신호로** 쓰고, 대화 자체가 졸음을 깨운다. | TextToSpeech, SpeechToText(ON_DEVICE) |
| 반응 둔화 | *"23km 앞 ○○휴게소에서 15분 쉬어 가요"* → 음성 "응" → 경유지 자동 추가. | NaviHelper |
| 휴게소 도착(P단) | 휴식 타이머, 파워냅 알람, 그 휴게소의 유명 간식·저렴한 주유 정보(보조). | Vehicle(기어), 공공데이터 |
| 이동 종료 | 휴식 기록 → 개인 주간 리포트, 향후 법인·물류 B2B 안전 운행 기록. | — |

포지셔닝: 의료기기·졸음 감지 장치가 아닌 **"휴식 코치"**.

### 4.4 핵심 재정의: 경고 앱 → 장거리 운전 플랜 코파일럿 (2026-11-05 추가)

4.3까지의 구조는 결국 "졸음 경고 + 휴식 제안"이라 핵심 기능이 약하다는 문제가 있었다.

- **이미 있는 기능과 겹친다**: 현대·기아 차량의 운전자 주의 경고(DAW)가 주행 패턴을 보고 휴식을 권한다. 내비에도 졸음쉼터·장시간 운전 휴식 안내가 있다. 심사위원이 가장 먼저 물을 질문이다.
- **경고는 사용자가 켜고 싶은 기능이 아니다**: 귀찮으면 끈다. 스스로 쓸 이유가 없으니 MAU·Retention 근거가 약하다.
- **음성 반응 피로 측정은 새롭지만 근거가 없다**: 정확도 질문에 마감 전까지 답하기 어렵다.

반면 DAW에는 없는 무기가 둘 있다. **차가 직접 행동한다**(공조·외기·조명·시트)와 **경고음 대신 대화로 깨운다**이다. 이 둘을 비상 기능이 아니라 앱의 중심으로 끌어올린다.

조종사가 비행 계획을 세우듯 **장거리 운전에 계획을 세우고 관리해 주는 앱**으로 바꾼다.

| 단계 | 하는 일 | 차별점 |
| --- | --- | --- |
| 출발: 운전 플랜 | 목적지·출발 시각으로 "3시간 10분, 오후 2~3시 식곤증 구간, 휴게소 2곳 미리 배치"를 만든다. 휴게소는 주유·간식·화장실을 함께 고려한다. | DAW는 사후 반응, 이것은 **사전 설계**. 사용자가 스스로 켤 이유가 생긴다. |
| 주행 중: 실시간 조정 | 실제 연속 운전 시간, 정체, 대화 반응으로 플랜을 고친다. "막혀서 늦어졌으니 다음 휴게소를 앞당길게요." | Navi와 Vehicle이 모두 핵심 흐름에 들어간다. |
| 피로 구간: 차가 개입 | 대화 → 실내 환경 조율 → 그래도 둔하면 휴게소 경유 제안(5.1의 단계적 개입). | 폰으로 불가능한 장면, 시연 하이라이트. |
| 도착 후: 기록 | 휴식 이행·피로 구간 리포트. | 재사용 동기, 법인차·보험 B2B 근거. |

효과: 창의성은 "졸음 경고"가 아니라 "운전 계획"이라 DAW와 겹치지 않고, 사업성은 장거리·출장이 있을 때마다 쓰는 앱이 된다. 휴게소 정보·간식 선주문은 플랜 안에 자연스럽게 들어간다. 분야를 바꾸는 게 아니라 핵심만 바꾸므로 기존 정리·시안 대부분을 재사용한다.

### 4.5 목적지를 다시 입력하지 않는다 (2026-11-05 추가)

허들: 플랜을 받으려고 앱을 따로 켜고 내비에 친 주소를 한 번 더 입력해야 한다면 아무도 쓰지 않는다.

해결: **내비에 목적지를 치는 순간이 곧 앱의 시작 신호**가 되게 한다. NaviHelper SDK 문서상 가능한 구조다.

1. 앱은 백그라운드에서 `NaviHelperEventListener`를 등록해 둔다.
2. 운전자가 평소처럼 내비에 목적지를 설정하면 `onDestinationChanged()` · `onRouteStarted()` · `onRoutingCompleted()`가 온다.
3. 앱이 `getDestinationInfo()`로 목적지를, `onTripInfo()` · `onRouteStateInfo()` · `onDrivingInfo()` 계열로 경로·주행 정보를 읽는다.
4. 예상 주행이 짧으면(예: 1시간 미만) 아무것도 하지 않는다. 길면 TTS로 한 문장만 말한다: *"3시간 걸리네요. 휴게소 두 곳 넣어 둘까요?"*
5. "응" 한마디면 `addWaypoint()`로 휴게소를 경유지에 넣는다. 경유지는 **최대 2곳**(`WaypointIndex.FIRST`/`SECOND`)이라 플랜의 휴게소 2곳과 맞는다.

결과적으로 운전자가 하는 일은 **평소처럼 내비에 목적지 입력 + "응" 한마디**뿐이다. 앱 화면은 내비 옆 창에 플랜 요약으로만 뜬다. Gleo App Schema를 등록하면 "글레오, 쉬는 곳 다시 짜 줘"처럼 운전자가 먼저 부를 수도 있다.

확인할 점:
- 앱이 **백그라운드일 때도** 내비 이벤트를 받는지 (6절의 백그라운드 검증과 같은 항목).
- `TripInfo` · `RouteStateInfo`에 남은 시간·거리뿐 아니라 **경로 형상**(어느 고속도로를 지나는지)까지 있는지. API 레퍼런스의 데이터 클래스 필드는 문서에 표시되지 않아 SDK에서 직접 확인해야 한다. 없으면 현재 위치·목적지·도로공사 휴게소 데이터로 경로상 휴게소를 근사한다.
- 문서상 "전달되는 이벤트는 내비 앱 구현 시나리오에 따라 다를 수 있다"는 단서가 있다 → 에뮬레이터에서 실제 수신 이벤트를 기록한다.

## 5. 덤으로 찾은 것 → 디벨롭 방향

### 5.1 차가 직접 깨운다 (차량 제어)

Vehicle SDK에 **HVAC·Window·Seat·Light(앰비언트)·Steeringwheel 제어** 도메인이 있다. 말로 권하는 데서 그치지 않고 **차 환경을 바꿔 깨우는** 단계적 개입을 설계할 수 있다. 폰으로는 불가능해 차량특화성·기술 활용성이 크게 오른다.

| 단계 | 조건(예시) | 개입 |
| --- | --- | --- |
| 0 · 관찰 | 연속 운전 < 1시간 | 개입 없음, 기록만 |
| 1 · 대화 | 연속 운전·시간대 기준 도달 | TTS 짧은 대화 + STT 반응 측정 |
| 2 · 환경 | 반응 둔화 | 실내 온도 낮춤, 외기 유입·창문 살짝, 앰비언트 조명 색 변경, 시트 통풍 (운전자 사전 동의 범위 안에서) |
| 3 · 휴식 | 둔화 지속 | 가장 가까운 휴게소 경유 제안 → 동의 시 경로 추가 |

원칙: 제어는 **사전 동의한 항목만**, 매번 음성으로 알리고 즉시 되돌릴 수 있게 한다. 창문·공조 제어는 주행 안전과 직결되므로 보수적으로 설계한다.

### 5.2 차량 졸음 신호와의 결합 (확인 필요)

SDV OS Vehicle API 레퍼런스에 `DrowsinessSeverity`, `DriverMonitoringStatus`가 있다. 서드파티 접근이 가능하다면 **차량 졸음 신호 + 음성 반응 피로 지표**를 함께 쓰는 이야기가 된다. 불가하면 기획서에 "향후 DMS 연동"으로 둔다.

### 5.3 기타 확장 후보

- **Gleo 연동**: App Schema 등록으로 *"글레오, 다음 휴게소 어디야?"* 같은 운전자 주도 호출.
- **휴게소 간식 선주문 → 도착 즉시 픽업**: 공모전 예시 주제 "주행 중 끊김 없는 주문·픽업"과 맞고, 휴게소 제휴 수수료로 사업성을 보강한다(시연은 목업).
- **B2B**: 법인차·렌터카·보험(안전 운행 할인)·물류사(상용차 탑재 시).

## 6. 플랫폼 확인 상태

| 항목 | 상태 | 근거 · 메모 |
| --- | --- | --- |
| 앱이 화면에서 사라져도 백그라운드 잔류 | ✅ 문서 확인 | 백그라운드 작업은 실행 조건·일시정지 정책을 정의하라고 권고 |
| 자원 제한 | ✅ 문서 확인 | 주행 중 비내비 앱 CPU < 5%, RAM ≤ 400MB, 상주·폴링 최소화 |
| 멀티윈도우(내비 옆 앱 창) | ✅ 문서 확인 | 드라이빙 뷰 + 앱 창 최대 2개 |
| STT 동작 방식 | ✅ 문서 확인 | 앱이 `request()`로 세션 시작 → 상시 호출어 없음. HYBRID/SERVER/ON_DEVICE, 권한 `pleos.car.permission.STT_SERVICE` |
| Gleo 음성 호출 | ✅ 문서 확인 | App Schema(JSON function calling) 등록 |
| 내비 목적지 설정 감지·조회 | ✅ 문서 확인 | `onDestinationChanged` · `onRouteStarted` · `onRoutingCompleted`, `getDestinationInfo()` |
| 경유지 추가 | ✅ 문서 확인 | `addWaypoint()`, 최대 2곳 |
| 남은 시간·거리, 경로 형상 필드 | ❓ SDK 확인 | `TripInfo` · `RouteStateInfo` · `DrivingInfo` 필드가 문서에 안 보임 |
| 백그라운드에서 내비 이벤트 수신 | ❓ 에뮬레이터 검증 | 문서에 언급 없음, 이벤트는 내비 앱 구현에 따라 다를 수 있다고 명시 |
| Vehicle 제어 도메인 존재 | ✅ 문서 확인 | HVAC·Window·Seat·Light 등. 속도·기어·연료량은 AAOS Car API 권장 |
| 시동 시 자동 시작 | ❓ 에뮬레이터 검증 | 문서에 언급 없음 |
| 백그라운드에서 STT·TTS 호출 | ❓ 에뮬레이터 검증 | 문서에 언급 없음 |
| 백그라운드에서 Vehicle 콜백 수신 | ❓ 에뮬레이터 검증 | 문서에 언급 없음 |
| 서드파티의 제어 API 권한 | ❓ 에뮬레이터 검증 | 문서상 manifest에 `pleos.car.permission.CONTROL_CAR_*`(예: `CONTROL_CAR_DOORS`) 선언. 실제 부여 여부는 `ERROR_SECURITY_PERMISSION` 발생 여부로 확인 |
| `DrowsinessSeverity` 서드파티 접근 | ❓ 검증 | SDV OS API에만 보임 |

자동 시작이 안 되면 대안: 처음 한 번 켜 두면 내비 옆 창에 상주하는 구조.

## 7. 리스크와 열린 질문

- 음성 반응 지연이 실제 피로와 상관있다는 근거를 기획서에서 어떻게 보일지(문헌·간이 실험).
- 대화가 오히려 주의를 분산시키지 않는지 — 운전 중 주의분산 가이드(음성 우선, 화면 최소화)에 맞춰 설계.
- 휴게소 평가(화장실 청결 등) 데이터 출처: 도로공사 공공데이터에는 유가·편의시설·대표메뉴가 있으나 청결 점수는 없을 가능성이 높다 → 보조 기능으로만.
- 경쟁 확인: 내비 앱의 장시간 운전 휴식 알림, 차량 기본 운전자 주의 경고(DAW)와의 차별점을 명시해야 한다.
- 남은 일정 11일 — 이번 주 안에 방향 확정.

## 8. 다음 단계 제안

1. **에뮬레이터 검증 (최우선)**: 자동 시작 / 백그라운드 STT·TTS / 백그라운드 Vehicle·내비 콜백 / 제어 API 권한 / `TripInfo` 필드.
2. 타깃 인터뷰 3~5명(영업직·출장자·기사): 졸음을 어떻게 버티는지, 휴게소를 언제 정하는지.
3. 기획서(DOCX)를 심사 4항목 순서로 구성, 사업성(MAU·Retention·확장) 섹션 신설.
4. 시연 영상: Virtual Console로 연속 주행 → 대화 → 반응 둔화 → 공조·조명 변화 → 휴게소 경유 → P단 휴식 타이머.
5. GitHub README를 제출 형식에 맞게 재작성.

## 출처

**공모전 (appdevchallenge.auto-hmg.io)**
- [공모전 개요](https://appdevchallenge.auto-hmg.io/guide/overview)
- [일정·설명회](https://appdevchallenge.auto-hmg.io/guide/schedule)
- [심사·시상](https://appdevchallenge.auto-hmg.io/guide/judging-awards)
- [참가 규정](https://appdevchallenge.auto-hmg.io/guide/rules)
- [지원·제출 방법](https://appdevchallenge.auto-hmg.io/guide/submission)
- [개발 환경](https://appdevchallenge.auto-hmg.io/guide/development)
- [숏폼 챌린지](https://appdevchallenge.auto-hmg.io/guide/shortform)
- [FAQ](https://appdevchallenge.auto-hmg.io/faq)
- [공모전 참가 및 출품 유의사항](https://appdevchallenge.auto-hmg.io/notice/c0a1a000-2026-4923-8000-000000000006)
- [참가자 모집 공고](https://appdevchallenge.auto-hmg.io/notice/c0a1a000-2026-4923-8000-000000000001)

**Pleos 개발 문서 (document.pleos.ai)**
- [App Structure and Behavior Model](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-planning-guide/app-structure-screen-model)
- [Design for Performance and Resource Efficiency](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-planning-guide/performance-resource-mgmt)
- [Design Considering Driving Safety](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-planning-guide/driver-distraction-guideline)
- [App Categories](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-planning-guide/app-category)
- [Hardware Access Policy](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-planning-guide/platform-api-architecture)
- [SDK API Policy](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-planning-guide/pleos-connect-sdk-api)
- [SpeechToText SDK](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/SpeechToText/intro) · [권한](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/SpeechToText/declaring-permissions) · [오디오 데이터 전송](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/SpeechToText/sending-audio-data)
- [TextToSpeech SDK](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/TextToSpeech/intro)
- [Vehicle SDK](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/intro) · [권한](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-permission)
- [Gleo Interface](https://document.pleos.ai/en/docs/pleos-only/app-integration-guide/guide/core-concepts)
- NaviHelper SDK: [목적지](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/destinations) · [이벤트 리스너](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/event-listeners) · [경유지](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/waypoint-settings)
- `DrowsinessSeverity` 등 SDV OS API: [사이트맵](https://document.pleos.ai/sitemap.xml)에서 `Occupant/sdv_occupant_driver` 검색

**Pleos Connect 탑재 현황 (언론)**
- [Hyundai Motor Group 보도자료 (AAP, 2026-04-30)](https://www.aap.com.au/aapreleases/cision20260430ae47897/)
- [아시아경제 영문판 (2026-04-29)](https://www.asiae.co.kr/en/article/2026042915381159653)
- [InsideEVs](https://insideevs.com/news/794540/hyundai-kia-genesis-pleos-connect-infotainment/)
- [ProPakistani (2026-08-01)](https://propakistani.pk/2026/08/01/hyundai-and-kia-cars-getting-major-smartphone-like-upgrade/)

**기존 서비스 비교**
- [블루링크 주차 위치 확인](https://ownersmanual.hyundai.com/ivi/PREM_GEN5/AVNT/FE/KOR/Korean/bluelink002.html)
- 티맵·카카오내비·도로공사 앱의 휴게소 정보, 내비 휴식 알림: 출처 미수집, 경쟁 분석 시 확인 필요
