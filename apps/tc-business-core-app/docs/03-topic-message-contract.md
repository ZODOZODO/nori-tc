# TC Business Core App 토픽/메시지 계약 동결 (Step 3)

## 1. Consume 토픽
- `tc.eqp.events`
- `tc.mes.events`
- `tc.ui.events`

## 2. Produce 토픽
- `tc.eqp.commands`
- `tc.mes.commands`
- `tc.ui.commands`

## 3. UI 요청/응답 계약
- 요청: `tc.ui.events`
- 응답(REP): `tc.ui.commands`
- 성공/실패 모두 REP 발행
- REP 발행 실패는 재시도 후 정책에 따라 처리

## 4. 라우팅 기준
- Kafka key와 무관하게 payload에서 `eqpId`를 추출하여 Mailbox 라우팅
- 순차성은 `eqpId` 단위

## 5. 실패 처리 계약
- workflow 미존재: `warn` 로그 후 정상 종료
- 예외/타임아웃: retry 후 한도 초과 시 DLQ
- DLQ 메시지는 `payloadRef`만 포함

## 6. Commit 계약
- 원칙: 처리 완료 후 commit(at-least-once)
- Consumer thread-safe 제약 준수(consumer thread에서 commit)
- out-of-order 완료를 고려한 partition 연속 commit tracker 사용

## 7. Timeout 계약
- task 실행 제한 3분
- 초과 시 `interrupt()` -> retry -> 한도 초과 시 DLQ

## 8. 비기능 계약
- `inFlight`는 동시성 락 용도만 사용
- DLQ 수동 처리와 `inFlight` 결합 금지
- 설비 중단 정책은 MES 관할
