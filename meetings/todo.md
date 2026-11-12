# DriveWatch 할 일

기준: [최종 기획서](../opinions/drive-watch-final-plan.md), [11-08 첫 회의](2026-11-08-kickoff.md). 목표는 11-14 완성, 제출 마감은 11-15다. 담당은 정해지면 이름을 적는다.

## 1. 에뮬레이터 확인 (가장 먼저)

결과에 따라 기획서 8절 "검증된 것과 가정" 표를 고친다.

- [ ] NaviHelper 콜백 수신: `onDestinationChanged`, `onReRouteEnded`, `onTBTInfo`, `onDestinationArrived` (앱이 백그라운드일 때도)
- [ ] `TBTInfo` 필드: 회전 방향·거리·도로명이 오는가
- [ ] 경로 비교: 두 경로의 소요 시간을 받을 수 있는가, `changeRouteOption`·`addWaypoint`로 편한 길을 만들 수 있는가
- [ ] Vehicle 리스너: TurnSignal, Brake, Odometer, Wiper, Light, Seat
- [ ] AAOS 차속(`PERF_VEHICLE_SPEED`)·기어(`GEAR_SELECTION`) 서드파티 권한
- [ ] TextToSpeech 사용 가능 여부 (안 되면 화면 문구로 대체)
- [ ] 시동·내비 이벤트로 앱이 자동으로 동작하는가

## 2. 앱 구현

회의 결정: 값 변화에 따른 시각화 수준까지 구현한다.

- [ ] 주행 기록: Vehicle·Navi 이벤트 수집과 저장
- [ ] 지표 계산: 익숙한 길 재탐색률, 방향지시등 이행률, 급제동, 기어 머뭇거림, 야간·빗길 비중, 연속 운전 시간, 주행량
- [ ] 기준선과 상태 판정: 안정 / 관찰 / 대화 권장
- [ ] 생활길: 도착 후 불편 구간 표시 → 다음 출발 때 빠른 길·편한 길 비교 → 선택
- [ ] 운전 약속: 설정, 출발 때 확인 문구, 이행 기록
- [ ] "오늘은 다른 방법으로 가기" 버튼
- [ ] 주간 카드(차량 화면)
- [ ] 자녀용 요약·근거 카드·대화 가이드 화면 (시연용 목업으로 충분한지 결정)
- [ ] 시연용 데이터: 몇 주치 주행 기록을 미리 넣어 상태 변화를 보여 줄 수 있게

## 3. 시연 자료

- [ ] 시연 시나리오(기획서 7절) 리허설: Virtual Console 입력 순서 정리
- [ ] 시연 영상 녹화 (가산점)
- [ ] 숏폼: 구현 후 AI 영상으로 제작

## 4. 제출

- [ ] 접수양식(DOCX)에 최종 기획서 옮기기
- [ ] 팀원 전원 개별 서명 동의서
- [ ] GitHub README: 앱 이름·한 줄 소개, 실행 방법, 테스트 환경·설정, APK 위치
- [ ] APK: `apk/팀명_DriveWatch_release.apk`
- [ ] 메일 제목 `[차량용 앱 개발 공모전] 팀명_대표자명`, 팀원 전원 CC, `appdevchallenge@hyundai-partners.com`
- [ ] 팀명·대표자 정하기

## 5. 기획 보강 (시간 되면)

- [ ] 인터뷰: 고령 운전자 부모를 둔 자녀 3명 + 고령 운전자 2명
- [ ] PMSA·DSW 이벤트를 서드파티 앱이 읽을 수 있는지 Pleos에 문의
- [ ] DriveWatch 이름 중복 확인 (앱 마켓, KIPRIS)
- [ ] [drive-watch.md](../archive/opinions/drive-watch.md)의 고령 사고 비중 "약 20%" → 23.7%
