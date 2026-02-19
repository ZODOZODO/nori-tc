# ADR-001: Kafka 토폴로지 및 송수신 소유권 고정

## 상태
승인됨 (2026-02-19)

## 배경
- `nori-tc`는 Gateway, Business, UI Backend(미구현), 외부 MES 어댑터가 동시에 연동되는 다중 애플리케이션 구조입니다.
- 운영 중 혼선을 줄이기 위해 토픽별 Producer/Consumer 소유권을 1차 책임 기준으로 고정해야 합니다.
- 설계 기준이 코드/문서/운영 가이드에 동일하게 반영되어야 이후 단계(Phase 2, Phase 3)에서 불필요한 재설계를 줄일 수 있습니다.

## 결정
다음 8개 흐름을 표준 송수신 경로로 고정합니다.

1. `tc-comm-gateway -> tc.eqp.events -> tc-business-core`
2. `tc-business-core -> tc.mes.commands -> 외부 MES 어댑터`
3. `외부 MES 어댑터 -> tc.mes.events -> tc-business-core`
4. `tc-business-core -> tc.eqp.commands -> tc-comm-gateway`
5. `tc-ui-backend -> tc.ui.events -> tc-comm-gateway`
6. `tc-ui-backend -> tc.ui.events -> tc-business-core`
7. `tc-comm-gateway -> tc.ui.commands -> tc-ui-backend`
8. `tc-business-core -> tc.ui.commands -> tc-ui-backend`

또한 다음 운영 규칙을 고정합니다.

- `tc.ui.events`는 토픽 분리 대신 **consumer group 2개**로 이중 소비합니다.
- `tc.ui.commands`는 단일 토픽을 유지하고 `metadata.source`로 발행 주체를 구분합니다.
- 런타임 중 파티션 수 변경은 금지하며, 파티션 변경은 배포/운영 정책 절차로만 수행합니다.
- 초기 파티션 기준값은 `6`이며, 모든 설정은 코드 하드코딩 대신 properties 주입을 사용합니다.

## 결과
- 토픽 책임 경계가 고정되어 장애 분석 시 추적 경로가 단순해집니다.
- UI 이벤트 이중 소비 요구를 충족하면서 토픽 수 폭증을 방지할 수 있습니다.
- 이후 구현 단계에서 공통 계약/검증/재시도 정책을 동일 기준으로 적용할 수 있습니다.

