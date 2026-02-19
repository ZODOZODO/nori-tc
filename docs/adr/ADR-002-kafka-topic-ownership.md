# ADR-002: 토픽별 Producer/Consumer 소유권 매트릭스

## 상태
승인됨 (2026-02-19)

## 배경
- 토픽별 소유권이 문서화되지 않으면 동일 토픽에 다수 애플리케이션이 임의 발행하여 충돌이 발생합니다.
- 운영 관점에서 "누가 발행하고 누가 소비해야 하는지"가 명확해야 ACL, 모니터링, 장애 대응 기준을 일치시킬 수 있습니다.

## 결정
아래 소유권 매트릭스를 표준으로 고정합니다.

| Topic | Producer(소유) | Consumer(소유) | 비고 |
|---|---|---|---|
| `tc.eqp.events` | `tc-comm-gateway` | `tc-business-core` | 설비 이벤트 상행 |
| `tc.eqp.commands` | `tc-business-core` | `tc-comm-gateway` | 설비 명령 하행 |
| `tc.mes.commands` | `tc-business-core` | 외부 MES 어댑터 | MES 하행 명령 |
| `tc.mes.events` | 외부 MES 어댑터 | `tc-business-core` | MES 상행 이벤트 |
| `tc.ui.events` | `tc-ui-backend` | `tc-comm-gateway`, `tc-business-core` | consumer group 2개 |
| `tc.ui.commands` | `tc-comm-gateway`, `tc-business-core` | `tc-ui-backend` | source로 발행자 구분 |

## 정책 메모
- 토픽 override는 정책상 금지합니다.
- 파티션/retention/compaction 등 토픽 운영 파라미터는 properties 중심으로 주입합니다.
- ACL은 소유권 매트릭스를 단일 진실 공급원(SSOT)으로 사용합니다.

## 결과
- 발행 충돌 및 책임 불명확 이슈를 사전에 차단합니다.
- 운영 정책(ACL, 모니터링, 컷오버, 롤백) 문서와 코드 기준을 일치시킵니다.

