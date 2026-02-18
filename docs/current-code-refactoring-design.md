# Nori-TC 현재 코드 기준 리팩터링 설계서

## 1. 문서 목적
이 문서는 `nori-tc`의 현재 구현을 기준으로, 안정성과 확장성을 높이기 위한 리팩터링 목표 아키텍처와 단계별 실행안을 정의합니다.

특히 다음 확정 요구사항을 반영합니다.

1. UI lifecycle 처리를 개선하되 설비 순차처리 보장을 깨지 않습니다.
2. Socket 플러그인 JAR은 설비(`eqpId`) 단위로만 reload/hot-swap 하며, 대상 설비 외 영향은 0으로 유지합니다.
3. Business workflow 액션은 공통 기본 클래스(기본 액션) + 설비별 JAR 확장 액션 구조로 운영합니다.
4. JAR 보안은 당장 최소 수준으로 적용하고, 고급 보안은 TODO로 분리합니다.

## 2. 확정 요구사항(사용자 합의)
1. Socket 플러그인 reload 시 대상 `eqp` 1대 재연결은 허용합니다.
2. 대상 `eqp` 외 나머지 설비는 영향이 없어야 합니다.
3. Business workflow 액션 해석 우선순위는 `eqp jar > 기본 공통 액션`입니다.
4. `eqp jar`가 없으면 기본 공통 액션이 실행됩니다.
5. 보안은 1차로 단순 적용 후 TODO로 확장합니다.

## 3. 현재 코드의 핵심 상태(요약)
1. 설비 순차처리는 현재 `EqpMailbox` + `EqpProcessingCoordinator` 기반으로 동작합니다.
2. UI lifecycle는 Kafka consumer 경로에서 동기 대기(`sleep/poll`)가 존재하여 병목 가능성이 있습니다.
3. Socket 플러그인은 `eqpId -> handler` 런타임 맵 구조로 이미 설계되어 있습니다.
4. Business 액션은 현재도 `plugin 우선, 없으면 core` 방식으로 디스패치하고 있습니다.

근거 파일:

1. `libs/comm/tc-comm-gateway-core/src/main/java/com/nori/tc/comm/gateway/comm/EqpProcessingCoordinator.java`
2. `libs/comm/adapter/tc-comm-gateway-kafka-adapter/src/main/java/com/nori/tc/comm/adapters/kafka/messaging/ui/GatewayUiLifecycleCommandService.java`
3. `libs/comm/adapter/tc-comm-gateway-plugin-adapter/src/main/java/com/nori/tc/comm/adapters/plugin/socket/GatewaySocketPluginRuntimeManager.java`
4. `libs/business/tc-business-core/src/main/java/com/nori/tc/business/core/workflow/BusinessWorkflowDispatchingActionExecutor.java`

## 4. 목표 아키텍처
### 4.1 제어 평면(Control Plane) / 데이터 평면(Data Plane) 분리
1. Data Plane:
`Netty/Kafka 수신 -> EqpMailbox enqueue -> EqpProcessingCoordinator 순차 drain` 흐름을 유지합니다.
2. Control Plane:
`EQP_START/END/DELETE/UPDATE_JARFILE`는 별도 상태 전이 엔진에서 처리합니다.
3. 공통 원칙:
`eqpId` 단위 직렬화 키를 사용해 상태 전이는 순차 처리합니다.

### 4.2 설비 단위 격리(Isolation by eqpId)
1. 모든 재구성/reload는 `eqpId` scope로 제한합니다.
2. 전역 락/전역 reconnect를 금지합니다.
3. 실패는 해당 `eqpId` 컨텍스트에서만 격리합니다.

### 4.3 액션 해석 체인(Action Resolution Chain)
1. 순서:
`pluginRegistry(eqpId)` 조회 -> 있으면 plugin 액션 실행 -> 없으면 core 액션 실행
2. core 액션은 항상 fallback으로 유지합니다.
3. plugin jar 미존재 시 core 동작은 현재와 동일하게 유지됩니다.

## 5. 상세 설계
### 5.1 UI lifecycle 리팩터링 설계
목표:
`GatewayUiLifecycleCommandService`의 동기 블로킹 루프를 제거하고, 제어 이벤트 기반으로 변경합니다.

핵심 설계:

1. `EqpLifecycleStateMachine`(신규) 도입
2. 상태 전이 이벤트 큐를 `eqpId` 단위로 직렬화
3. `START/END` 요청은 즉시 `ACCEPTED`를 반환하고 비동기 완료 이벤트를 기록
4. timeout은 폴링이 아닌 scheduler 기반 만료 이벤트로 처리

상태 모델:

1. Desired: `STARTED | ENDED`
2. Runtime: `DISCONNECTED | CONNECTING | CONNECTED | STOPPING | ERROR`
3. 전이 보호:
`stateVersion`(증가값)으로 stale 이벤트를 무시

설비 순차성 보장 근거:

1. 데이터 처리 순차성은 `EqpProcessingCoordinator` 경로를 그대로 유지
2. lifecycle 전이도 `eqpId` 직렬화 키로 처리
3. 두 평면은 `eqpId` 컨텍스트와 상태 조건으로만 연결

### 5.2 Socket 플러그인(eqpid 단위 hot-swap) 설계
목표:
대상 설비만 재연결하고, 나머지 설비는 무중단 유지

현재 구조 활용:

1. Provider: `GatewaySocketPluginRuntimeProvider#findByEqpId`
2. Mutation: `GatewaySocketPluginRuntimeMutationPort#reloadByEqpId`
3. Runtime manager: `GatewaySocketPluginRuntimeManager`

개선 포인트:

1. `reloadByEqpId(eqpId)` 실행 시 해당 eqp channel만 제어
2. 순서:
`new handler 로드/검증 -> runtime swap -> target eqp reconnect trigger`
3. swap 실패 시 기존 runtime 유지(원자적 교체 보장)

재연결 전략:

1. 대상 eqp channel close
2. active면 reconnect resume/connect
3. passive면 bind 대기
4. 다른 eqp는 이벤트/채널 조작 금지

최소 보안(1차):

1. 파일명 sanitize
2. 파일 크기 상한
3. SHA-256 해시 계산 및 로그 저장

TODO 보안(2차):

1. 서명 검증
2. 신뢰 저장소(allowlist) 검증
3. 격리 프로세스(인프로세스 탈피) 옵션

### 5.3 Business workflow 액션 플러그인 설계
요구사항 반영:

1. 기본 공통 액션 클래스(기본 액션) 존재
2. 설비별 JAR 클래스는 기본 액션 확장(`extends`) 가능
3. 설비 JAR 미존재 시 기본 액션 실행

실행 규칙:

1. `action_name` 기반 key 생성
2. `pluginRegistry.find(actionKey)` 우선
3. 미존재 시 `coreRegistry.find(actionKey)` fallback
4. 둘 다 없으면 명시적 예외 + DLQ

현재와의 정합:
`BusinessWorkflowDispatchingActionExecutor`가 이미 이 정책을 구현 중이며, 이를 공식 표준 규칙으로 고정합니다.

구현 확장:

1. plugin 액션 클래스 규약 문서화
2. 액션 충돌 시 우선순위 로그(PLUGIN_OVERRIDE)
3. 액션 삭제 시 fallback 동작 검증 테스트 강화

### 5.4 메시지 처리 결과 표준화(권장)
목표:
침묵 drop 제거

규칙:

1. 입력은 반드시 결과 상태를 남김
2. 상태 값:
`ACCEPTED | RETRY | DLQ | REJECTED`
3. key mismatch/no connection/validation 오류도 기록 후 종료

## 6. 모듈/파일별 리팩터링 맵
### 6.1 Comm(Gateway)
변경 대상:

1. `libs/comm/adapter/tc-comm-gateway-kafka-adapter/src/main/java/com/nori/tc/comm/adapters/kafka/messaging/ui/GatewayUiLifecycleCommandService.java`
2. `libs/comm/adapter/tc-comm-gateway-kafka-adapter/src/main/java/com/nori/tc/comm/adapters/kafka/messaging/ui/GatewayUiRuntimeControlService.java`
3. `libs/comm/adapter/tc-comm-gateway-kafka-adapter/src/main/java/com/nori/tc/comm/adapters/kafka/messaging/ui/GatewayUiTaskProcessorRegistry.java`
4. `libs/comm/adapter/tc-comm-gateway-plugin-adapter/src/main/java/com/nori/tc/comm/adapters/plugin/socket/GatewaySocketPluginRuntimeManager.java`
5. `libs/comm/tc-comm-gateway-core/src/main/java/com/nori/tc/comm/gateway/comm/GatewayProcessingService.java`

신규 추가:

1. `libs/comm/tc-comm-gateway-core/src/main/java/com/nori/tc/comm/gateway/lifecycle/EqpLifecycleStateMachine.java`
2. `libs/comm/tc-comm-gateway-core/src/main/java/com/nori/tc/comm/gateway/lifecycle/EqpLifecycleEvent.java`
3. `libs/comm/tc-comm-gateway-core/src/main/java/com/nori/tc/comm/gateway/lifecycle/EqpLifecycleTransitionGuard.java`

### 6.2 Business
변경 대상:

1. `libs/business/tc-business-core/src/main/java/com/nori/tc/business/core/workflow/BusinessWorkflowDispatchingActionExecutor.java`
2. `libs/business/adapter/tc-business-plugin-adapter/src/main/java/com/nori/tc/business/adapters/plugin/workflow/BusinessWorkflowPluginRuntimeManager.java`

신규 추가:

1. `libs/business/tc-business-core/src/main/java/com/nori/tc/business/core/workflow/ActionResolutionPolicy.java`
2. `libs/business/tc-business-core/src/main/java/com/nori/tc/business/core/workflow/ActionResolutionTrace.java`

## 7. 단계별 마이그레이션 계획
### Phase 1. 안정성 기반
1. Socket plugin 파일명 sanitize + 해시 기록 적용
2. 대상 eqp 외 영향 금지 가드 로그 추가
3. UI lifecycle 동기 폴링 경로에 feature flag 추가

완료 기준:
대상 eqp reload 시 타 eqp connection 변화가 0건

### Phase 2. UI lifecycle 비동기 전환
1. 상태 머신/이벤트 큐 도입
2. START/END 비동기 완료 이벤트로 전환
3. 기존 동기 경로는 fallback 플래그로 유지

완료 기준:
UI 이벤트 처리량 증가 시 consumer 지연 급증이 없어야 함

### Phase 3. 액션 체인 표준화
1. plugin 우선/기본 fallback 정책을 정책 클래스화
2. override/fallback/미존재 모든 케이스 로깅 표준화
3. 액션 충돌/삭제 시나리오 테스트 추가

완료 기준:
`action_name` 변경 시 plugin/core 선택 결과가 예측 가능해야 함

### Phase 4. 운영 강건화
1. disposition 이벤트 표준화
2. DLQ/REJECTED 관측 대시보드 추가
3. 보안 TODO 항목 우선순위화

세부 구현 기준:
1. 표준 disposition 값은 `ACCEPTED | RETRY | DLQ | REJECTED`로 고정
2. Gateway/Business 경로 모두 동일 키(`flow`, `disposition`)로 누적 계측
3. 보안 TODO는 우선순위 숫자(1이 가장 높음)로 관리

## 8. 테스트 전략(필수)
1. eqp 단위 격리 테스트:
`eqp-A reload` 중 `eqp-B` 처리량/연결 상태 불변 검증
2. 순차성 테스트:
동일 eqp 입력 순서와 출력 순서 보장 검증
3. 액션 fallback 테스트:
plugin jar 없음/있음/오류 케이스 모두 검증
4. lifecycle timeout 테스트:
stale 이벤트가 최신 상태를 덮지 못함을 검증
5. 회귀 테스트:
기존 core-only 동작 동일성 검증

## 9. 로그/관측 표준
1. 공통 키:
`eqpId`, `traceId`, `stateVersion`, `actionName`, `source(core|plugin)`, `result`
2. 필수 이벤트:
`PLUGIN_RELOAD_STARTED`, `PLUGIN_RELOAD_APPLIED`, `PLUGIN_RELOAD_ROLLED_BACK`
3. lifecycle:
`LIFECYCLE_REQUEST_ACCEPTED`, `LIFECYCLE_TRANSITION_APPLIED`, `LIFECYCLE_TIMEOUT`

## 10. 수용 기준(Definition of Done)
1. 대상 eqp reload가 타 설비에 영향 0임이 테스트로 증명됨
2. plugin 우선/fallback 정책이 로그와 테스트에서 일치함
3. UI lifecycle 비동기 전환 후에도 eqp 데이터 순차성 회귀 없음
4. 최소 보안(파일명/크기/해시) 적용 완료
5. TODO 보안 항목이 백로그로 명확히 분리됨

## 11. TODO 백로그(보안/고급)
1. [Priority 1] 플러그인 서명 검증
2. [Priority 2] 신뢰된 발행자 정책(allowlist) 검증
3. [Priority 3] 인프로세스 로딩 격리(별도 프로세스/샌드박스)
4. [Priority 4] 플러그인 권한 모델(네트워크/파일 접근 제한)
5. [Priority 5] 신뢰된 발행자 정책과 연계한 롤백 자동화
