# 운전 헬퍼 아이디어 덤핑

고령 운전자의 생활길에서 겪은 일을 다음 경로와 이동수단 선택에 반영하는 아이디어. [시장 조사](integrated-driving-coach-market.html)에서 이어진 팀 검토용 메모.

**출품 범위 권장안.** 생활길 헬퍼와 아래 안전 연계를 중심에 두고, 초보 운전연수는 이번 출품 범위에서 제외하는 방향을 권장한다. 팀 논의를 위해 ‘운전의 전제’ 기획과 후진 주차 데모를 아래에 남긴다.

## 생활길 헬퍼 · 지난번에 불편했던 길을 기억하기

> 병원까지 4분 더 걸려도 지난번에 부담스러웠던 합류를 피할 수 있다면, 그 이유를 보고 내가 고르고 싶다.

- **누가 쓰나.** 병원이나 장을 보러 자차로 같은 길을 자주 다니는 고령 운전자. 복잡한 합류나 교차로 한 곳 때문에 그날의 운전이 부담스러운 사람부터 만나본다.
- **한 번의 사용.** 병원에 도착해 주차한 뒤 지난 경로에서 중앙대로 합류를 표시한다. 다음 방문 전에 두 길을 보여준다. 빠른 길은 **24분**이고 표시한 합류를 지난다. 편한 길은 **28분**이고 그 구간을 피한다. 운전자가 경로를 고른다.
- **어디가 새롭나.** 즐겨찾는 경로와 모의주행은 이미 있다. 여기서 보여줄 건 내가 표시한 구간이 다음 선택에 반영되고, **4분을 더 쓰는 이유**를 내가 이해할 수 있다는 점이다. [티맵 즐겨찾는 경로](https://www.tmapmobility.com/story/microtip/detail/131) · [티맵 모의주행](https://www.tmapmobility.com/story/microtip/detail/138)
- **더 해볼 것.** 병원에 갈 때와 집에 돌아올 때의 진입 방향을 따로 저장하기. 평소 경로가 바뀌면 출발 전 확인된 차이 한 가지만 보여주기. 운전하기 부담스러운 날에는 본인이 저장한 대체 이동수단 열기.
- **실차에서 확인할 것.** NaviHelper에서 선택한 목적지·경유지를 넘길 수 있다. 후보 경로가 표시한 구간을 실제로 포함하는지 판정할 데이터와 권한은 별도 확인이 필요하다. 경로를 대조할 수 없으면 포함 여부를 미확인으로 둔다. [Pleos NaviHelper](https://document.pleos.ai/en/api-reference/connect-sdk-pleos/NaviHelper/intro)
- **사업성 가설.** 교통안전과 이동권을 다루는 지자체 한 곳에서 생활길 1개를 가진 참여자 15명과 4주간 시험해본다. 기본 내비 즐겨찾기와 비교해 스스로 경로를 고를 수 있는지, 필요한 외출을 마치는지, 4주 뒤에도 쓰는지 본다. 설정 지원과 효과 측정을 포함한 다음 회차에 기관이 비용을 낼지는 따로 확인한다. [서울시 고령층 교통 지원 사례](https://news.seoul.go.kr/traffic/archives/509469)

[병원길 데모](life-route-helper.html) · **중앙대로 합류 표시 → 다음번 출발 → 빠른 길 또는 편한 길 선택**. 지도와 시간은 가상이다. 표시와 선택은 브라우저에 저장된다.

## 생활길에 붙일 안전 기능

| 기능 | 생활길에 붙이는 장면 | 첫 시연 범위 |
| --- | --- | --- |
| **페달 오조작 안전 보조(PMSA)** | 지원 차량에서 정차 후 출발할 때 가까운 장애물과 급한 가속 페달 입력 등 작동 조건이 맞으면 차량이 가속을 제한하고 제동한다. 실제 개입 이벤트를 받을 수 있다면 주차 후 **“이곳에서 차량이 가속을 제한했어요”**라고 보여준다. 운전자가 장소를 확인해 표시하면 다음 방문 때 확인된 다른 주차 구역이나 접근 경로를 비교한다. 가속 제한·제동은 차량 시스템이 맡는다. [현대차 PMSA 설명서](https://ownersmanual.hyundai.com/full_webhelp/NH2/2026/ko_KR/driver_assistance_pmsa.html) · [작동 조건](https://ownersmanual.hyundai.com/full_webhelp/NH2/2026/ko_KR/topic_hfj_km4_pbc.html) | **운전자가 불편한 장소를 직접 표시**하는 흐름부터 보여준다. 개입 이벤트를 연출한다면 시연용 입력으로 표기한다. |
| **인캐빈 카메라와 전방 주시 경고(DSW)** | 해당 사양의 차량은 실내 카메라로 얼굴 방향·눈의 개폐 등을 보고 전방 주시 경고를 낸다. 제조사 경고를 읽을 수 있다면 도착 후 **“전방 주시 경고가 있었어요”**라는 사실과 시각을 보여준다. 위치와 방향을 대조할 수 있고 운전자가 원할 때 해당 구간을 생활길에 표시한다. 연계에 필요한 데이터는 경고 종류와 시각, 구간 식별에 필요한 최소 정보다. [현대차 DSW 설명서](https://ownersmanual.hyundai.com/full_webhelp/QZ/2026/ko_KR/id8df144211b6.html) | 기본 시연은 수동 표시를 사용한다. 영상 수집 없이 제조사가 제공하는 경고 결과를 읽는 방식부터 검토한다. |
| **음주운전 예방** | 출발 전 **‘다른 방법으로 가기’**에서 같은 목적지의 대리운전·택시·동행 요청을 열 수 있게 한다. 음주한 경우에도 이 버튼을 바로 쓸 수 있게 해 음주 여부를 입력하거나 저장할 필요를 줄인다. | **운전자가 대체 이동을 선택하는 흐름**을 시연한다. 운전 습관이나 시선으로 음주 여부를 알아내는 방식은 연구 후보로 둔다. 도로 상황·피로 등과 구별하려면 검증된 참조값과 오탐·미탐 검증이 필요하다. 앱의 안내는 운전 가능 여부 판정에 사용하지 않는다. [NHTSA 2026년 의회 보고서](https://www.nhtsa.gov/sites/nhtsa.gov/files/2026-03/Report-to-Congress-Advanced-Impaired-Driving-Prevention-Technology.pdf) |

병원 시연은 **주차 후 지난번에 불편했던 곳 표시 → 다음 방문 전에 경로 비교 → 선택한 경로로 출발 또는 대체 이동 선택**으로 잡는다. 경고나 개입을 읽을 수 있는 차량에서는 그 이벤트를 표시 후보로 추가한다. 실제 개입, 사용자 메모, 시연용 이벤트는 화면에서 각각 출처를 밝힌다. 급가속·급감속 점수는 블루링크에도 있으므로, 이 기획의 검증 대상은 다음 방문에서 사용자가 더 적합한 경로나 이동수단을 고를 수 있는지다. [현대차 블루링크 안전 운전 습관](https://www.hyundai.com/kr/ko/digital-customer-support/app/myhyundai/bluelink/bluelink-vehicle-care/safe-driving-habits)

### Pleos 신호 확인표

공개 가이드에서 **Vehicle State와 NaviHelper는 별도 SDK 신청 없이 사용 가능**으로 안내된다. 개별 API의 권한과 지원 여부는 차종·플랫폼·버전별로 확인한다. 아래의 ‘문서 확인’은 공개 명세 기준이며, 대상 차량이나 앱에서 실제 수신한 기록은 별도로 남긴다. [Pleos SDK 신청 안내](https://document.pleos.ai/en/docs/pleos-only/create-my-project) · [호환성·가용성 안내](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-planning-guide/api-compatibility-availability)

| 신호·기능 | 확인 상태 | 확인할 것과 앱에서 쓰는 범위 |
| --- | --- | --- |
| **목적지·경유지·내비 이벤트** | **Pleos 문서 확인.** NaviHelper의 경로 요청, 경유지 설정, 도착·재탐색 콜백이 문서에 있다. 경유지 포함 경로 요청에는 `NAVI_ROUTE_SEARCH` 권한이 필요하다. | 목적지를 차량 내비에 넘기고 도착을 확인한다. 후보 경로의 좌표나 구간 ID, 진행 방향을 받을 수 있는지와 각 콜백의 실제 수신을 확인한다. 표시한 구간을 지나는지 대조할 수 있을 때 포함·회피 여부를 안내한다. [NaviHelper 이벤트](https://document.pleos.ai/en/api-reference/connect-sdk-pleos/NaviHelper/event-listeners) · [경유지 포함 경로 요청](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/route-with-waypoints) |
| **차속·기어·주차 중 화면 사용** | **Pleos·AAOS 문서 확인.** Pleos FAQ는 차속에 AAOS `PERF_VEHICLE_SPEED`를 안내한다. AAOS는 `GEAR_SELECTION`을 정의하고 Virtual Console은 P/R/N/D 설정을 제공한다. | 차속과 기어 수신 및 권한을 확인한다. 화면 조작 가능 여부는 플랫폼의 `CarUxRestrictions`를 따른다. 차속 0만으로 주차를 확정하는 대신 기어와 플랫폼 상태를 함께 본다. [Pleos 차속 FAQ](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-developer-guide/vehicle-app-faq#checking-cluster-speed) · [AAOS 기어 명세](https://developer.android.com/reference/android/car/VehiclePropertyIds#GEAR_SELECTION) · [Virtual Console](https://document.pleos.ai/en/docs/pleos-only/app-test-guide/virtual-console-guide) · [Pleos 주행 안전 설계](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-planning-guide/driver-distraction-guideline) |
| **브레이크·가속 페달 입력** | **브레이크 상태는 Pleos Vehicle SDK 문서에 있다.** 가속 페달 눌림 비율 `ACCELERATOR_PEDAL_COMPRESSION_PERCENTAGE`는 AAOS에 정의되어 있지만 대상 Pleos 앱에서 수신 가능한지는 **미확인**이다. | 가속 페달은 `PERMISSION_READ_CAR_PEDALS`와 차량 속성 지원을 확인한다. 페달값·차속은 관측값으로만 표시하고 PMSA 개입이나 페달 오조작 확정으로 바꾸지 않는다. [Pleos Vehicle SDK](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/intro) · [AAOS 가속 페달 명세](https://developer.android.com/reference/android/car/VehiclePropertyIds#ACCELERATOR_PEDAL_COMPRESSION_PERCENTAGE) |
| **PMSA 실제 개입 이벤트** | **현대차 기능 확인. Pleos 서드파티 수신 미확인.** | 대상 차종에서 실제 개입 시작·종료를 구분하는 이벤트 ID, 권한, 시각, 지원 버전과 시뮬레이터 입력 방법을 확인한다. 일반 경고등이나 주차 거리 경고는 각자의 신호로 취급한다. |
| **DSW 경고·운전자 상태·원본 영상** | **현대차 기능 확인. Pleos 서드파티 수신 미확인.** AAOS의 운전자 주의·졸음 상태 속성은 서명 또는 특권 권한을 요구한다. | 제조사가 별도로 제공하는 읽기 전용 이벤트의 이름·권한·지원 차종을 확인한다. 주시 경고와 졸음 상태는 각각의 의미로 취급하고, 카메라 원본 접근은 기본 기능의 전제에서 제외한다. [AAOS 주의 상태](https://developer.android.com/reference/android/car/VehiclePropertyIds#DRIVER_DISTRACTION_STATE) · [AAOS 졸음·주의 상태](https://developer.android.com/reference/android/car/VehiclePropertyIds#DRIVER_DROWSINESS_ATTENTION_STATE) |
| **정확한 이벤트 위치·음주 측정** | **미확인.** SDK 신청 문서에는 FusedLocation이 준비 중으로 표기되어 있다. 공개 Pleos 문서에서 음주 측정값이나 음주 판정 이벤트도 확인하지 못했다. | 이벤트 위치는 권한과 정확도를 확인한 내비 데이터 또는 사용자 선택으로 연결한다. 위치가 없으면 경고 시각만 보여준다. 음주운전 예방 시연은 사용자가 선택하는 대체 이동으로 제공한다. [Pleos SDK 신청 안내](https://document.pleos.ai/en/docs/pleos-only/create-my-project) |

**먼저 확인할 질문.** “일반 Pleos Connect 앱에서 PMSA 실제 개입 또는 DSW 전방 주시 경고를 읽을 수 있나요? 된다면 API 이름, 권한, 지원 차종·버전, 에뮬레이터 재현 방법은 무엇인가요?” P단·내비 이벤트부터 에뮬레이터에서 실제 콜백을 기록하고, 확인된 신호만 화면 문구에 반영한다.

## 운전의 전제 · 같은 장면에서 무엇이 달라졌나

> 초보가 본 정보에서 어디까지 판단할 수 있고, 무엇을 더 봐야 하는지 짧게 연습해보자.

- **콘텐츠 단위.** 운전자 시점으로 몇 초 보여주고 “지금 확실히 아는 건 어디까지예요?”라고 묻는다. 바깥 시점으로 바꿔 빠진 정보를 드러낸다. 마지막으로 조건 하나만 바꾼 장면을 보여준다. 장면마다 `본 사실 → 하기 쉬운 추정 → 빠진 확인 → 조건을 바꾼 장면`을 기록한다.
- **첫 예시.** 왼쪽으로 차 한 대가 지나가는 걸 봤다. 확인한 건 그 한 대가 지나갔다는 사실이다. 뒤에 다른 차가 있는 장면과 없는 장면을 나란히 두고, 처음 시점에 보이지 않았던 영역을 보여준다. [캘리포니아 DMV 차선 변경·사각지대 안내](https://www.dmv.ca.gov/portal/handbook/california-driver-handbook/navigating-the-roads/)
- **후진 주차.** 같은 위치에 차와 기둥을 둔다. 핸들을 곧게 둔 후진과 왼쪽으로 꺾은 후진은 둘 다 후방뷰에서 뒤가 비어 보인다. 탑뷰로 전환하면 조향한 차의 오른쪽 앞범퍼가 바깥으로 돌아 기둥에 닿는 장면이 보인다. [영국 후진 안내, 규칙 202](https://www.gov.uk/guidance/the-highway-code/using-the-road-159-to-203)
- **다음 주행 장면 후보.** 합류할 틈은 같고 뒤차의 접근 속도만 다른 경우. 같은 초록불인데 교차로 건너편에 내 차가 들어갈 공간 유무만 다른 경우. 같은 속도와 차간거리에서 노면만 젖은 경우. 같은 골목에서 옆 차 문이 열리는 경우. 같은 커브에서 담장 때문에 출구가 가려지는 경우.
- **다음 주차 장면 후보.** 벽까지의 바닥 간격은 같은데 범퍼 높이로 튀어나온 구조물이 있는 경우. 같은 기둥이 서라운드뷰 합성 경계에 있는 경우. 경고음이 없지만 뒤에 낮은 턱이 있는 경우. 후방 가이드라인은 비슷한데 바닥이 경사진 경우. [현대차 서라운드뷰 제한](https://ownersmanual.hyundai.com/full_webhelp/MX5/2026/ko_KR/idf6ef4f47e60.html) · [현대차 주차 거리 경고 제한](https://ownersmanual.hyundai.com/full_webhelp/QT/2026/ko_KR/topic_nbb_lmg_s3c.html) · [닛산 후방 모니터 거리 안내](https://www.nissan.co.uk/owners/car-repair/car-owner-manual/manuals/iom/qashqai/0j12/e1/2024/difference-between-predictive-and-actual-distances-1.shtml)
- **차량에서 언제 쓰나.** 주차 상태에서 출발 전이나 도착 직후 30초짜리 장면 한 개를 연다. 실제 경로에 관련 구간이 있다는 근거를 얻으면 그 장면을 먼저 보여준다. [Pleos 주행 중 사용 제한](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-planning-guide/driving-restiction-policy)
- **이게 배움이 되는지.** 처음에는 어디가 닿을지 물어본 뒤 데모를 보여준다. 다음에는 기둥 위치나 핸들 방향을 바꾼 처음 보는 장면에서 **먼저 닿는 부분과 추가로 확인할 곳**을 맞히는지 본다. 파일럿에서는 이 판단의 변화를 측정한다.

[후진 주차 데모](parking-swing.html) · **왼쪽 28° → 후진 재생 → 탑뷰**. 같은 시작 위치에서 조향각을 바꿔 직진 궤적과 비교할 수 있다. 차체와 기둥 배치는 학습용 모형이다.

두 데모는 HTML 파일을 로컬 브라우저에서 열면 된다.
