# TC Comm Gateway 안내서

## 문서 목적

이 폴더의 문서는 `tc-comm-gateway-app`의 실행 구조와 실제 동작 로직(주로 `libs/comm/*`)을 초급 개발자도 한 번에 이해할 수 있도록 정리한 안내서입니다.

중요한 전제는 다음과 같습니다.

1. `apps/tc-comm-gateway-app`는 매우 얇은 실행 진입점입니다.
2. 실제 통신/라이프사이클/카프카/Netty 로직은 대부분 `libs/comm/*`에 있습니다.
3. 문서를 읽을 때는 "앱"만 보지 말고 "starter + core + adapter"를 함께 봐야 전체 흐름이 보입니다.

## 먼저 꼭 알아야 하는 용어

### 1) ACTIVE / PASSIVE 는 "gateway 기준"입니다

이 프로젝트에서 `ConnectionMode.ACTIVE`, `ConnectionMode.PASSIVE`는 gateway 기준으로 해석합니다.

정리하면 다음과 같습니다.

1. gateway 기준 `ACTIVE`
   - 게이트웨이가 먼저 연결을 시도합니다.
   - 게이트웨이는 클라이언트(outbound connect)로 접속을 시도합니다.
2. gateway 기준 `PASSIVE`
   - 게이트웨이가 listener를 열고 설비 접속을 기다립니다.
   - 게이트웨이는 서버(listener) 역할을 수행합니다.

주의:

1. `04-gateway-active-eqp-event-lifecycle.md`, `05-gateway-passive-eqp-event-lifecycle.md`도 gateway 기준 파일명/내용으로 정리되어 있습니다.
2. listener/server = `PASSIVE`, outbound/client = `ACTIVE` 기준으로 문서를 읽으면 됩니다.

### 2) 라이프사이클 요청 성공과 실제 연결 성공은 다를 수 있습니다

예를 들어 START 요청이 수락되었다고 해서 즉시 채널 연결이 완료된 것은 아닙니다.

1. 요청 수락: 상태머신에 START 요청이 등록됨
2. 실제 완료: Netty connect/listener + bind 성공 후 `CHANNEL_CONNECTED` 반영됨
3. 최종 응답: 상태머신 outcome으로 성공/실패 확정됨

### 3) 장비 단위 직렬 처리가 핵심입니다

여러 이벤트가 동시에 들어와도 `eqpId` 기준으로 순서를 보장하기 위해 mailbox/queue/scheduler를 사용합니다.

## 문서 읽는 순서 (권장)

1. [`01-startup-sequence.md`](./01-startup-sequence.md)
2. [`02-context-bean-map.md`](./02-context-bean-map.md)
3. [`03-lifecycle-state-machine-overview.md`](./03-lifecycle-state-machine-overview.md)
4. [`04-gateway-active-eqp-event-lifecycle.md`](./04-gateway-active-eqp-event-lifecycle.md)
5. [`05-gateway-passive-eqp-event-lifecycle.md`](./05-gateway-passive-eqp-event-lifecycle.md)
6. [`06-kafka-tc-eqp-commands-to-equipment-lifecycle.md`](./06-kafka-tc-eqp-commands-to-equipment-lifecycle.md)
7. [`07-config-runtime-topics-checklist.md`](./07-config-runtime-topics-checklist.md)

주의:

1. 모든 문서의 ACTIVE/PASSIVE 해석은 gateway 기준입니다. (listener/server = PASSIVE, outbound/client = ACTIVE)

## 필수 문서 목록과 역할

1. `01-startup-sequence.md`
   - 애플리케이션 구동 순서, AutoConfiguration, `SmartLifecycle phase`, 컨텍스트 기동 완료 시점
2. `02-context-bean-map.md`
   - 주요 Bean 생성 위치, 역할, 의존 관계, 계층(앱/스타터/코어/어댑터)
3. `03-lifecycle-state-machine-overview.md`
   - `EqpLifecycleStateMachine`의 이벤트/상태/timeout/pending/outcome 공통 개념
4. `04-gateway-active-eqp-event-lifecycle.md`
   - gateway 기준 ACTIVE(outbound/client) 장비의 START/END/connect/reconnect 생명주기
5. `05-gateway-passive-eqp-event-lifecycle.md`
   - gateway 기준 PASSIVE(listener/server) 장비의 START/END/bind/unbind 생명주기
6. `06-kafka-tc-eqp-commands-to-equipment-lifecycle.md`
   - `tc.eqp.commands` 구독 후 설비 송신까지의 흐름, DLQ/Quarantine 포함
7. `07-config-runtime-topics-checklist.md`
   - 설정/운영 체크리스트, Kafka topic/partition 불변조건, 프로퍼티 prefix별 역할

## 코드 탐색 시작점 (권장 파일)

1. 앱 진입점
   - `apps/tc-comm-gateway-app/src/main/java/com/nori/tc/apps/commgateway/TcCommGatewayApplication.java`
2. 자동구성
   - `libs/comm/starter/tc-comm-gateway-starter/src/main/java/com/nori/tc/comm/gateway/starter/TcCommGatewayAutoConfiguration.java`
3. 코어 설정
   - `libs/comm/tc-comm-gateway-core/src/main/java/com/nori/tc/comm/gateway/config/GatewayCommConfiguration.java`
   - `libs/comm/tc-comm-gateway-core/src/main/java/com/nori/tc/comm/gateway/config/GatewayProcessingConfiguration.java`
4. 라이프사이클 상태머신
   - `libs/comm/tc-comm-gateway-core/src/main/java/com/nori/tc/comm/gateway/lifecycle/EqpLifecycleStateMachine.java`
5. Netty 런타임
   - `libs/comm/adapter/tc-comm-gateway-netty-adapter/src/main/java/com/nori/tc/comm/adapters/netty/GatewayNettyBootstrap.java`
   - `libs/comm/adapter/tc-comm-gateway-netty-adapter/src/main/java/com/nori/tc/comm/adapters/netty/EqpBindingService.java`
6. Kafka command 경로
   - `libs/comm/adapter/tc-comm-gateway-kafka-adapter/src/main/java/com/nori/tc/comm/adapters/kafka/subscribe/GatewayEqpCommandKafkaSubscriber.java`
   - `libs/comm/adapter/tc-comm-gateway-kafka-adapter/src/main/java/com/nori/tc/comm/adapters/kafka/subscribe/GatewayCommandDispatcher.java`

## 자주 하는 오해 (먼저 정리)

1. "`GatewayUiRuntimeControlService.startRuntime()`가 실제 연결 완료까지 동기 처리한다"
   - 아닙니다. 요청 수락과 실제 연결 성공은 분리되어 있습니다.
2. "채널 연결만 되면 시작 성공이다"
   - bind 성공, mailbox 바인딩, lifecycle 이벤트 반영까지 확인해야 정상 경로를 다 본 것입니다.
3. "Kafka command는 항상 설비로 전송된다"
   - 아닙니다. 계약 검증 실패, 장비 미연결, 인터페이스 불일치, 인코딩 실패, 라우팅 실패 시 drop/DLQ가 발생할 수 있습니다.

## 문서 범위

1. 게이트웨이 앱의 기동/조립 구조
2. Spring Context/Bean 구조
3. 장비 라이프사이클 상태머신
4. gateway 기준 ACTIVE/PASSIVE 장비 이벤트 생명주기
5. `tc.eqp.commands` 소비 후 설비 송신 경로
6. 설정/운영 체크리스트

## 빠른 디버깅 시작 순서 (입문자용)

1. 앱 시작 로그 (`TcCommGatewayApplication`)
2. Kafka 불변조건 검사 실패 여부 (`GatewayKafkaOperationalInvariantChecker`)
3. `EqpLifecycleStateMachine` 요청/결과 로그
4. Netty bind/connect/reconnect 로그
5. `GATEWAY_TASK_DISPOSITION` 로그 (UI task / COMMAND 흐름)

상세 내용은 각 문서에서 단계별로 설명합니다.
