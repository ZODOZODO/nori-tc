# 02. Context / Bean 정리 안내서

## 문서 목적

이 문서는 `tc-comm-gateway-app` 실행 시 Spring Context 안에 어떤 주요 Bean이 올라오고, 각각이 어떤 역할을 맡는지 설명합니다.

초급 개발자가 자주 막히는 지점:

1. "클래스는 많은데 어디서 Bean이 만들어지는지 모르겠습니다."
2. "core와 adapter의 책임 경계가 헷갈립니다."
3. "UI 이벤트, Kafka command, Netty channel이 어디에서 합쳐지는지 모르겠습니다."

이 문서는 위 질문에 답하기 위해 계층별로 정리합니다.

관련 문서:

1. 구동 순서: [`01-startup-sequence.md`](./01-startup-sequence.md)
2. 상태머신 공통 개념: [`03-lifecycle-state-machine-overview.md`](./03-lifecycle-state-machine-overview.md)

## 1. 전체 구조: 앱 / 스타터 / 코어 / 어댑터

## 1-1. 앱 (`apps/tc-comm-gateway-app`)

역할:

1. Spring Boot 진입점
2. starter 의존성 조립
3. 외부 설정 로딩
4. 앱 시작/종료 로그

대표 파일:

1. `apps/tc-comm-gateway-app/src/main/java/com/nori/tc/apps/commgateway/TcCommGatewayApplication.java`
2. `apps/tc-comm-gateway-app/build.gradle.kts`
3. `apps/tc-comm-gateway-app/src/main/resources/application.yaml`

핵심 포인트:

앱은 "실행 껍데기"에 가깝고, 실제 로직은 대부분 `libs/comm/*`에 있습니다.

## 1-2. 스타터 (`libs/comm/starter/tc-comm-gateway-starter`)

역할:

1. `AutoConfiguration` 제공
2. `com.nori.tc.comm` 컴포넌트 스캔
3. 코어 설정 클래스 import

대표 파일:

1. `libs/comm/starter/tc-comm-gateway-starter/src/main/java/com/nori/tc/comm/gateway/starter/autoconfigure/TcCommGatewayAutoConfiguration.java`
2. `libs/comm/starter/tc-comm-gateway-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

## 1-3. 코어 (`libs/comm/tc-comm-gateway-core`)

역할:

1. 도메인 상태/라이프사이클 관리
2. 큐/메일박스 처리
3. 메시지 라우팅/퍼블리시/디스패치
4. 설정 프로퍼티/정책 엔진

특징:

1. "무엇을 할지"를 정의하는 계층
2. Netty/Kafka 기술 세부 구현은 포트/어댑터로 분리

## 1-4. 어댑터 (`libs/comm/adapter/*`)

역할:

1. Netty 통신 런타임 구현
2. Kafka 소비자/생산자 연동
3. 외부 I/O 기술 구현

대표 모듈 예:

1. `tc-comm-gateway-netty-adapter`
2. `tc-comm-gateway-kafka-adapter`

## 2. Bean 생성의 출발점: `TcCommGatewayAutoConfiguration`

`TcCommGatewayAutoConfiguration`는 Bean 등록의 시작점입니다.

핵심 동작:

1. `@ComponentScan(basePackages = "com.nori.tc.comm")`
2. `@Import(GatewayCommConfiguration, GatewayProcessingConfiguration)`

즉, Bean 생성 방식은 크게 두 가지입니다.

1. 설정 클래스의 `@Bean`
2. 컴포넌트 스캔(`@Component`, `@Service`, `@Configuration`, ...)

## 3. `GatewayCommConfiguration` (공통 설정/파이프라인/정책)

파일:

- `libs/comm/starter/tc-comm-gateway-starter/src/main/java/com/nori/tc/comm/gateway/starter/autoconfigure/GatewayCommConfiguration.java`

## 3-1. 프로퍼티 클래스 활성화

이 설정 클래스는 많은 `@ConfigurationProperties`를 한 번에 활성화합니다.

대표 목록:

1. `GatewayRuntimeProperties`
2. `GatewayLifecycleProperties`
3. `GatewayHsmsProperties`
4. `GatewaySocketProperties`
5. `GatewayKafkaTopicProperties`
6. `GatewayKafkaClientProperties`
7. `GatewayKafkaShardProperties`
8. `GatewayUiTaskPolicyProperties`
9. `GatewayRedisProperties`
10. `GatewayPublishPolicyProperties`
11. `GatewayNettyProperties`
12. `GatewayObservabilityProperties`
13. `GatewaySocketPluginRuntimeProperties`

초급 개발자 팁:

설정을 읽을 때는 "기능 단위 prefix"로 끊어서 보는 습관이 좋습니다. 예를 들어 통신 문제면 `tc.comm.gateway.netty`, Kafka 문제면 `tc.comm.gateway.kafka` 및 `spring.kafka`를 먼저 봅니다.

## 3-2. 공통 유틸/정책 Bean

대표 Bean:

1. `ClockPort` -> `SystemClock`
   - 시간 조회 추상화
2. `TraceIdGeneratorPort` -> `UlidTraceIdGenerator`
   - 추적 ID 생성
3. `GatewaySocketPluginRuntimeProvider`
   - 플러그인 기반 socket 인코더/디코더 확장점
   - 없으면 no-op/fallback 제공
4. `PublishPolicy` -> `PublishPolicyEngine`
   - 이벤트 퍼블리시 정책 적용

이 Bean들은 특정 프로토콜/전송보다 상위 레벨의 공통 기능을 담당합니다.

## 3-3. 프로토콜/파이프라인 Bean

대표 Bean:

1. `HsmsFrameExtractor`
2. `Secs2Decoder` -> `BasicSecs2Decoder`
3. `HsmsInboundPipeline`
4. `SocketInboundPipeline`
5. `SocketTypeRegistry`

`SocketTypeRegistry` 특징:

1. socket 타입별 처리기(예: line-delimited, regex-delimited)를 등록/조회
2. command dispatcher의 payload 인코딩 시에도 사용 가능

초급 개발자 포인트:

Netty 핸들러가 받은 raw bytes가 바로 비즈니스 객체가 되는 것이 아니라, 이 계층에서 파싱/해석/라우팅 준비를 거칩니다.

## 4. `GatewayProcessingConfiguration` (장비 처리 엔진)

파일:

- `libs/comm/starter/tc-comm-gateway-starter/src/main/java/com/nori/tc/comm/gateway/starter/autoconfigure/GatewayProcessingConfiguration.java`

이 설정은 장비별 큐/메일박스 처리 중심 Bean을 만듭니다.

## 4-1. 채널/메일박스/송신 관련 Bean

1. `EquipmentChannelRegistry`
   - 장비별 활성 채널 등록/조회
   - 중복 바인딩 방지 핵심
2. `EquipmentMailboxRegistry`
   - 장비별 mailbox 생성/등록/삭제
   - `BoundedInboundQueue`, `BoundedOutboundQueue` 생성과 연결
3. `OutboundSenderPort` -> `ChannelBasedOutboundSender`
   - channel write를 수행하는 아웃바운드 송신 포트 구현

## 4-2. 인바운드 처리/라우팅 관련 Bean

1. `InboundPipelinePort` -> `ProtocolInboundPipelineRouter`
2. `RouteAndPublishUseCase`
3. `EqpSequentialProcessor`

역할 분담 관점:

1. `GatewayIngressService`가 큐에 넣음
2. `EquipmentProcessingCoordinator`가 mailbox를 돌림
3. `EqpSequentialProcessor`가 장비 단위 처리 순서를 유지하며 실행
4. 내부에서 inbound pipeline/router/use case가 동작

## 5. 컴포넌트 스캔으로 등록되는 핵심 Bean (역할별 정리)

이 섹션은 `@Bean`이 아닌 `@Component/@Service` 중심입니다.

## 5-1. 장비 컨텍스트/프로파일 계층

### `EquipmentContextRegistry`

역할:

1. 장비 컨텍스트 저장소 (in-memory)
2. `upsertProfile`, `find`, `remove`, `snapshot` 제공

왜 중요한가:

1. Netty bootstrap이 시작 대상 장비를 찾을 때 사용
2. 상태머신이 상태를 갱신할 때 사용
3. UI runtime control이 컨텍스트를 수정할 때 사용

### `EquipmentContextBootstrap`

역할:

1. 부팅 시 `EquipmentContextProfileProvider.findAllProfiles()`로 프로파일 로드
2. `EquipmentContextRegistry` 초기 적재

초기 상태 규칙:

1. `enabled=true`
   - `desiredState=STARTED`
   - `runtimeState=DISCONNECTED`
2. `enabled=false`
   - `desiredState=ENDED`
   - `runtimeState=REGISTERED`

중요:

이 단계는 "기동 의도 상태"를 메모리에 올리는 과정입니다. 실제 connect/listener 시작은 `GatewayNettyBootstrap`의 책임입니다.

## 5-2. 라이프사이클 상태머신 계층

### `EquipmentLifecycleStateMachine`

역할:

1. 장비별 START/END 요청 직렬 처리
2. 채널 연결/해제 이벤트 반영
3. timeout/실패 처리
4. outcome 발행

입력 이벤트 소스:

1. UI runtime control (`requestStart`, `requestEnd`)
2. Netty bind/unbind (`onChannelConnected`, `onChannelDisconnected`)
3. Netty start 실패 신호 (`onStartFailedIfPending`)
4. 내부 timeout scheduler (`START_TIMEOUT`, `END_TIMEOUT`)

출력:

1. `EquipmentLifecycleOutcome`
2. `EquipmentLifecycleOutcomeListener` 호출 (예: UI deferred reply 서비스)

## 5-3. 처리 서비스/코디네이터 계층

### `GatewayIngressService`

역할:

1. Netty 인바운드 raw bytes enqueue (`enqueueInbound`)
2. Kafka command 아웃바운드 raw frame enqueue (`enqueueOutbound`)
3. mailbox bind/remove
4. 장비 정보 조회 (`resolveEquipment`)
5. queue overflow 시 DLQ + quarantine 처리

초급 개발자 포인트:

1. Netty 핸들러는 최대한 빠르게 빠져나와야 하므로 큐에 넣는 구조가 중요합니다.
2. Kafka command도 즉시 channel write로 끝내지 않고 공통 큐/메일박스 경로를 타도록 설계되어 있습니다.

### `EquipmentMailboxRegistry`

역할:

1. 장비별 mailbox 생성/조회/삭제
2. `BoundedInboundQueue`, `BoundedOutboundQueue`와 runtime context 보유

왜 중요한가:

장비별 처리량 제한, backpressure, 순서 보장을 구현하는 핵심 지점입니다.

### `EquipmentRuntimeContextFactory`

역할:

1. `GatewayEquipmentInfo + properties`를 바탕으로 `EquipmentRuntimeContext` 생성
2. HSMS / SOCKET runtime context 분기
3. socketType 기본값 fallback 적용
4. HSMS session 관련 설정 구성

### `EquipmentProcessingCoordinator` (`SmartLifecycle`)

역할:

1. mailbox 실행 런타임 시작
2. inbound/outbound drain 처리
3. retry scheduler 운영

요약:

`GatewayIngressService`가 "입력 큐에 적재"를 담당한다면, `EquipmentProcessingCoordinator`는 "실제 소비/실행"을 담당합니다.

## 5-4. UI 이벤트/명령 처리 계층

### `GatewayUiEventKafkaSubscriber`

역할:

1. `tc.ui.events` 구독
2. 계약 검증
3. `KafkaMessageDispatcher<KafkaUiTaskMessage>`로 위임

정책 특징:

1. 레코드 실패 시 즉시 commit하지 않고 재시도 가능
2. Kafka poll thread와 비즈니스 처리 분리

### `GatewayUiTaskDispatcher` (`SmartLifecycle`, phase `-100`)

역할:

1. UI task를 `eqpId` 기준 mailbox로 enqueue
2. worker thread에서 `KafkaTaskExecutionPipeline` 실행
3. `GATEWAY_TASK_DISPOSITION` 로그/메트릭 기록

### `GatewayUiTaskProcessorRegistry`

역할:

1. UI eventType별 처리 스펙 등록
2. `EQP_START`, `EQP_END`를 deferred lifecycle task로 처리

핵심 포인트:

`EQP_START`, `EQP_END`는 "요청 접수"와 "최종 결과"가 분리됩니다. 이 연결은 상태머신 outcome 기반으로 마무리됩니다.

### `GatewayUiDeferredLifecycleReplyService` (`EquipmentLifecycleOutcomeListener`)

역할:

1. START/END pending reply 저장
2. 상태머신 outcome 수신
3. 최종 응답(`EQP_START_REP`, `EQP_END_REP`) publish

실무적으로 매우 중요:

UI에서 보이는 START/END 결과의 최종 판정은 이 서비스가 담당합니다.

### `GatewayUiRuntimeControlService`

역할:

1. 컨텍스트 생성/수정/삭제
2. 런타임 start/stop 요청 접수
3. 상태머신 요청 + Netty runtime control 연결
4. UI SEND_MESSAGE를 command dispatcher 경로로 전달

START 시 핵심 흐름:

1. 검증
2. `lifecycleStateMachine.requestStart(...)`
3. `connectionControlPort.startRuntimeIfPossible(eqpId)`
4. 즉시 "요청 접수" 로그
5. 최종 결과는 outcome 기반

## 5-5. Netty 어댑터 계층

### `GatewayConnectionControlPort`

역할:

1. core/UI 계층에서 Netty 런타임을 제어하기 위한 포트
2. start/stop/reconnect suppress/resume 제어 인터페이스 제공

주의:

메서드 이름 중 `connectActiveIfPossible` 등은 gateway 기준 ACTIVE(outbound) 의미로 해석합니다. 문서 해석 기준도 `ConnectionMode` gateway 기준입니다.

### `GatewayNettyBootstrap` (`SmartLifecycle`, phase `0`)

역할:

1. Netty event loop / reconnect scheduler 관리
2. gateway 기준 PASSIVE 장비용 공유 listener 관리
3. gateway 기준 ACTIVE 장비용 outbound connect/reconnect 관리
4. `GatewayConnectionControlPort` 구현

내부적으로 관리하는 중요한 상태(예시):

1. shared listener 채널 맵
2. listener 멤버십 맵 (`eqpId -> listenerKey`)
3. reconnect suppress 상태
4. 연속 실패 카운터

### `GatewayChannelHandlerFactory`

역할:

1. listener 수신 경로 핸들러 생성 (`newPassiveHandler`)
2. outbound connect 경로 핸들러 생성 (`newActiveHandler`)

### `GatewayChannelHandler`

역할:

1. `channelActive`, `channelRead`, `channelInactive` 처리
2. bind timeout 관리
3. unbound inbox 버퍼 관리
4. bind 완료 후 `GatewayIngressService.enqueueInbound(...)` 연결

핵심 특징:

채널은 연결 직후 바로 `eqpId`를 알지 못할 수 있으므로, bind 전에 수신 데이터 일부를 임시 버퍼에 쌓고 파싱을 통해 bind를 시도합니다.

### `BindAttemptExecutor`

역할:

1. bind 시도 파싱/검증을 별도 executor에서 수행
2. Netty event loop 블로킹 방지
3. MDC/로그 컨텍스트 보존

### `EqpBindingService`

역할:

1. bind/unbind 시 장비/모드/인터페이스/샤드 검증
2. `EquipmentChannelRegistry` 바인딩
3. `GatewayIngressService.bindMailbox(...)`
4. `EquipmentLifecycleStateMachine`에 채널 이벤트 전달

요약:

Netty 채널 이벤트를 "도메인/상태머신 이벤트"로 변환하는 브리지입니다.

## 5-6. Kafka command (`tc.eqp.commands`) 처리 계층

### `GatewayEqpCommandKafkaSubscriber`

역할:

1. `tc.eqp.commands` assign 방식 구독
2. `GatewayKafkaShardProperties.ownedPartitions` 기반 partition 할당
3. 계약 검증 후 `GatewayCommandDispatcher` 호출

### `GatewayCommandDispatcher`

역할:

1. command envelope 유효성 검증
2. 장비 채널 존재 여부 확인
3. 인터페이스 타입 분기 (HSMS/SOCKET)
4. payload 인코딩
5. `GatewayIngressService.enqueueOutbound(...)`로 전달
6. 실패 시 DLQ/Quarantine/disposition 처리

중요:

현재 코드 기준으로 HSMS business command 경로는 미구현으로 DLQ 처리될 수 있습니다. 이 사실을 운영/개발자가 문서에서 명확히 알고 있어야 합니다.

## 6. "입력 이벤트별" Bean 협력 구조 (초급 개발자용)

클래스 목록만 보면 어렵기 때문에, 입력 이벤트 기준으로 보면 이해가 훨씬 쉽습니다.

## 6-1. UI에서 `EQP_START` 요청이 들어오는 경우

1. `GatewayUiEventKafkaSubscriber`
2. `GatewayUiTaskDispatcher`
3. `KafkaTaskExecutionPipeline`
4. `GatewayUiTaskProcessorRegistry` (START 처리 스펙)
5. `GatewayUiRuntimeControlService`
6. `EquipmentLifecycleStateMachine.requestStart(...)`
7. `GatewayNettyBootstrap.startRuntimeIfPossible(...)`
8. Netty 결과가 `EqpBindingService`를 통해 상태머신으로 다시 들어옴
9. `GatewayUiDeferredLifecycleReplyService`가 outcome 받아 최종 응답 publish

## 6-2. 설비에서 데이터가 들어오는 경우

1. `GatewayChannelHandler.channelRead(...)`
2. (필요 시 bind 시도/성공)
3. `GatewayIngressService.enqueueInbound(...)`
4. `EquipmentProcessingCoordinator`
5. `EqpSequentialProcessor`
6. `ProtocolInboundPipelineRouter` -> protocol pipeline -> route/publish

## 6-3. Kafka `tc.eqp.commands` 메시지가 들어오는 경우

1. `GatewayEqpCommandKafkaSubscriber`
2. `GatewayCommandDispatcher`
3. `GatewayIngressService.enqueueOutbound(...)`
4. `EquipmentProcessingCoordinator`
5. `ChannelBasedOutboundSender` -> Netty channel write

## 7. 상태 저장 위치를 구분해서 보는 법 (매우 중요)

같은 "상태"처럼 보이는 정보라도 저장 위치가 다르고 의미가 다릅니다.

1. `EquipmentContextRegistry`
   - 장비의 desired/runtime 상태 (도메인/운영 관점)
2. `EquipmentChannelRegistry`
   - 실제 활성 Netty 채널 매핑 (통신 관점)
3. `EquipmentMailboxRegistry`
   - 장비별 큐/실행 컨텍스트 (처리 관점)
4. `GatewayNettyBootstrap` 내부 맵
   - reconnect suppress, 연속 실패 카운터, shared listener membership (Netty 제어 관점)

문제 분석 시 "어떤 상태가 어긋났는지"를 먼저 정하면 읽어야 할 클래스가 줄어듭니다.

## 8. 초급 개발자가 자주 하는 이해 오류

1. `GatewayIngressService`가 모든 비즈니스 로직을 처리한다고 생각함
   - 실제로는 큐/메일박스 입구 역할이 큽니다.
2. `GatewayNettyBootstrap`만 보면 START/END 성공 판정까지 다 알 수 있다고 생각함
   - 최종 판정은 `EquipmentLifecycleStateMachine` + outcome listener까지 봐야 합니다.
3. UI START 요청 accept 로그를 최종 성공으로 오해함
   - 최종 결과는 deferred reply가 publish될 때 확정됩니다.

## 9. 초급 개발자를 위한 추천 읽기 순서 (클래스 기준)

1. `EquipmentContextRegistry`
2. `EquipmentContextBootstrap`
3. `EquipmentLifecycleStateMachine`
4. `GatewayUiRuntimeControlService`
5. `GatewayNettyBootstrap`
6. `EqpBindingService`
7. `GatewayChannelHandler`
8. `GatewayIngressService`
9. `EquipmentProcessingCoordinator`
10. `GatewayEqpCommandKafkaSubscriber`
11. `GatewayCommandDispatcher`

## 10. 다음 문서 안내

다음 문서 [`03-lifecycle-state-machine-overview.md`](./03-lifecycle-state-machine-overview.md)에서는 START/END 요청과 채널 이벤트를 어떻게 상태머신이 묶어서 최종 판정하는지 자세히 설명합니다.
