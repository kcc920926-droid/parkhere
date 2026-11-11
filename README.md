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

- [drivewatch-core](drivewatch-core/README.md) · 구간 판정과 주간 판정을 옮긴 Kotlin 모듈. 웹 MVP와 결과가 같은지 테스트로 확인합니다.
- [drivewatch-vehicle](drivewatch-vehicle/README.md) · 차량 신호를 받는 Android 라이브러리와 확인용 앱. 수집은 포그라운드 서비스에서 합니다.
- [family-server](family-server/README.md) · 가족 요약 서버와 자녀 폰 웹 화면. [폰 화면](https://drive.olvend.com/family/) · [REST API 명세](https://drive.olvend.com/api/docs)
- [Pleos 에뮬레이터 신호 확인 기록](docs/pleos-signal-check.md) · 차속·기어·브레이크·조향·방향지시등을 받는 방법과 확인 결과
- [시연 데이터와 시연 이벤트](docs/demo-data.md) · 시연에서 무엇이 가짜이고 무엇이 실제 코드인지
- [접수양식 초안](submission/application.md) · 기획서 원문과 양식에 넣는 스크립트

## 실행 방법

구현 후 작성한다.

## 테스트 환경과 설정

- Android API 28+, Gradle 8.0+, Kotlin
- Pleos Connect 에뮬레이터, Virtual Console(시각·기어·방향지시등·주행 입력)
- 필요한 권한과 설정은 구현 후 작성한다.

## APK

`apk/팀명_DriveWatch_release.apk` (구현 후 추가)
