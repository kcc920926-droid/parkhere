# 탄만큼 검토용 미리보기

| 파일 | 검토할 내용 |
| --- | --- |
| [rules-motion.html](rules-motion.html) | 운전자 포함 3명 출발 → 10km에서 A 하차 → 남은 D·B의 추가 구간 분담을 설명하는 약 10초 모션 |
| [personal-receipt.html](personal-receipt.html) | 동승자 모바일 영수증의 동행 티켓형·여정 노트형 비교, 계산 근거 펼치기, 송금 수단 선택 |

저장소를 내려받은 뒤 위 HTML 파일을 브라우저에서 열면 된다. GitHub 파일 화면에서는 HTML 소스로 보인다. 스타일·아이콘 등 정적 리소스를 불러오기 위해 인터넷 연결이 필요하다.

수정 가능한 원본 조각은 [src/rules-motion.html](src/rules-motion.html)과 [src/personal-receipt.html](src/personal-receipt.html)에 있다. 상위의 HTML은 원본을 독립 실행용으로 내보낸 미리보기다. 원본을 변경하면 내보낸 파일도 갱신한다.

모든 금액·탑승자·구간은 가상 예시이며 실제 송금은 실행하지 않는다. 규칙 모션은 운전자 포함 예시이고, 영수증은 동승자 A의 첫 10km 부담액 500원을 보여준다. 영수증의 송금 버튼은 확인용 안내 화면으로 전환한다.

개인 영수증은 **동승자 모바일 웹** 시안이다. 차량 앱의 넓은 창·음악 앱 옆의 분할 창은 별도 설계·검증 대상이다. 차량 SDK 연결, 실제 광고·결제 연동, 차량 앱 구현을 완료했다는 산출물은 아니다.

현재 제품 기준은 [PLAYBOOK.MD](../../PLAYBOOK.MD), 실행 과제는 [TASKLIST.MD](../../TASKLIST.MD)를 따른다.
