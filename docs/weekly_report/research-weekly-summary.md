# Nori-TC 주간 연구 보고 요약

> **프로젝트 기간:** 2025년 3월 3일(월) ~ 2025년 5월 23일(금) (12주)
> **프로젝트 목표:** 설비 시뮬레이터 + MES 시뮬레이터 연동 데모
> **개발 순서:** nori-tc (백엔드) → nori-tc-ui (프론트엔드)
> **nori-tc 내부 순서:** libs/common → libs/db → libs/messaging → libs/comm + gateway-app → libs/business + business-app → libs/ui + ui-backend-app

---

## 주차별 개발 계획 요약

| 주차 | 기간 | 레이어 | 핵심 개발 내용 |
|:----:|------|--------|---------------|
| 1주차 | 3/3~3/7 | nori-tc | 로그·Mailbox·Task실행·Kafka컨슈머·DB공통 라이브러리 |
| 2주차 | 3/10~3/14 | nori-tc | 전체 도메인 JPA 엔티티·MyBatis 매퍼·Kafka 메시지 계약 |
| 3주차 | 3/17~3/21 | nori-tc | HSMS·Socket 프로토콜·게이트웨이 코어 라이브러리 |
| 4주차 | 3/24~3/28 | nori-tc | Netty 서버·DB·Kafka·Redis 게이트웨이 어댑터 + 앱 조립 |
| 5주차 | 3/31~4/4 | nori-tc | 비즈니스 도메인·런타임엔진·워크플로우·DB·Kafka 어댑터 |
| 6주차 | 4/7~4/11 | nori-tc | 모델·설비·작업 비즈니스 로직 + MES 시뮬레이터 연동 |
| 7주차 | 4/14~4/18 | nori-tc | UI 도메인·코어·DB·Web·Kafka·Redis 어댑터 전체 구현 |
| 8주차 | 4/21~4/25 | nori-tc | 인증·설비·모델·작업·메시지 REST API 완성 + 앱 조립 |
| 9주차 | 4/28~5/2 | nori-tc-ui | 인증·설비·모델·작업·메시지 전체 화면 + 연동 검증 |
| 10주차 | 5/5~5/9 | 전체 | 리팩토링 (예외처리·로그·설정·컴포넌트·타입 정비) |
| 11주차 | 5/12~5/16 | 전체 | 통합 검증 + 데모 시나리오 리허설 + 버그 수정 |
| 12주차 | 5/19~5/23 | — | 최종 데모 실행 및 산출물 취합 |

---

## 1주차 (2025.03.03 ~ 2025.03.07)

### 금주 연구 목표

- 멀티모듈 프로젝트 골격 및 공통 인프라 레이어 구축
- `libs/common`: 로깅·메일박스·컨슈머런타임·태스크실행 공통 모듈 설계 및 구현
- `libs/db`: DB 추상화 계층 및 RDBMS/Redis/MyBatis 스타터 구현

### 세부 연구 내용

1. **구조화 로그 라이브러리(`tc-common-logging`) 구현**
   MDC(Mapped Diagnostic Context) 기반 로그 컨텍스트 관리 체계를 구현한다. 요청 단위로 설비 ID(`eqp_id`), 추적 ID(`trace_id`) 등 식별 정보를 스레드 로컬에 자동 바인딩하고, 비동기 스레드 전환 시에도 MDC 값이 유실되지 않도록 `TcLogContext`(AutoCloseable)를 적용한다. `LogCompressionScheduler`를 통해 일별 로그 파일을 자동 압축 보관하는 기능도 함께 구현한다.

2. **비동기 메시지 처리 라이브러리(`tc-common-mailbox`) 구현**
   설비별로 메시지를 순차 처리하기 위한 Mailbox 패턴을 구현한다. 각 설비는 독립된 `Mailbox` 인스턴스를 가지며, 내부 `ReadyQueue`에 태스크가 적재되면 `MailboxScheduler`가 순서 보장 방식으로 하나씩 실행한다. CAS 기반 `AtomicBoolean` 플래그로 경쟁 조건 없이 단일 in-flight 실행 권한을 보장한다.

3. **태스크 실행 프레임워크(`tc-common-task-execution`) 구현**
   Kafka 기반 UI 태스크 파이프라인의 공통 실행 프레임워크를 구현한다. 검증→중복제거(traceId)→처리→응답발행→traceId마킹의 5단계 파이프라인(`KafkaTaskExecutionPipeline`)을 구성하고, 처리 실패 시 지수 백오프(최대 60초 상한) 후 DLQ로 전달하는 오류 처리 흐름을 포함한다.

4. **Kafka 컨슈머 런타임 라이브러리(`tc-common-consumer-runtime`) 구현**
   Kafka SDK 타입을 직접 노출하지 않는 중립 계층의 파티션 오프셋 커밋 추적 모듈을 구현한다. `PartitionCommitCoordinator`가 파티션별 처리 완료 여부를 추적하고, 안전한 시점에만 오프셋을 커밋하도록 설계한다.

5. **DB 공통 도메인 및 스타터 구성**
   `tc-db-domain`(순수 도메인 인터페이스)과 `tc-db-core`(공통 추상 계층)를 분리하여 DB 기술 스택에 독립적인 도메인 계층을 확보한다. PostgreSQL JPA, MySQL·Oracle·MSSQL MyBatis, Redis 스타터 총 9종을 구현하며, MapStruct 1.6.3 기반 Entity↔Domain 매핑 자동화를 적용한다.

### 결론 및 차주 계획

금주는 Nori-TC 프로젝트의 공통 인프라 레이어 전체를 설계·구현하는 데 집중하였습니다. libs/common 4개 모듈과 libs/db 9개 스타터를 완성하여 이후 메시징·통신·비즈니스 계층 개발의 기반을 마련하였으며, 멀티스레드 안정성 및 프레임워크 비종속 설계 원칙을 단위 테스트로 검증하였습니다.

차주(2주차, 03.10 ~ 03.14)에는 아래 항목을 중점적으로 수행할 예정입니다.

- `libs/messaging` Kafka 인프라 구현: Kafka 컨슈머·프로듀서 공통 모듈(`tc-messaging-kafka-consumer`, `tc-messaging-kafka-producer`) 설계 및 구현. `PartitionCommitCoordinator`와 Kafka 컨슈머 연동 구조 확립
- Transactional Outbox 패턴 적용: 메시지 발행 유실 방지를 위한 `tc_msg_send_queue`·`tc_msg_send_log` DB 스키마 설계 및 Outbox 폴러(Poller) 구현
- 멀티모듈 통합 빌드 검증: 전체 모듈 일괄 빌드 수행 및 의존성 순환 참조 점검
- 단위 테스트 보완: libs/common·libs/db 대상 경계값·실패 케이스 테스트 추가

---

## 2주차 (2025.03.10 ~ 2025.03.14)

### 금주 연구 목표

- `libs/db`: 전체 도메인 JPA 엔티티 및 MyBatis 매퍼 구현 (사용자·인증·모델·설비·작업·메시지 도메인)
- `libs/messaging`: Kafka 메시지 도메인·계약 구조 및 런타임·발행 어댑터 구현
- Transactional Outbox 패턴 DB 스키마 설계 및 폴링 쿼리 구현

### 세부 연구 내용

1. **사용자·인증·모델 도메인 JPA 엔티티 구현(`tc-db-jpa-common-schema`)**
   사용자 관련 테이블(`tc_user_info`, `tc_user_group`, `tc_user_group_member`, `tc_user_group_permission`, `tc_ui_auth_session`, `tc_ui_permission`)과 모델 관련 테이블(`tc_model`, `tc_model_version` 및 하위 9개 테이블: `tc_model_param`, `tc_model_secs_message`, `tc_model_socket_message`, `tc_model_variableid`, `tc_model_reportid`, `tc_model_eventid`, `tc_model_workflow`, `tc_model_mdf`, `tc_model_dcop_item`)에 대한 JPA 엔티티 클래스를 구현한다. 각 엔티티는 테이블 제약 조건(UNIQUE, CHECK, FK CASCADE/RESTRICT)을 어노테이션으로 반영하고, MapStruct 기반 엔티티 매퍼를 함께 구현한다.

2. **설비·JAR 도메인 JPA 엔티티 구현**
   설비 관련 테이블(`tc_eqp`, `tc_eqp_global`, `tc_eqp_hsms`, `tc_eqp_socket`, `tc_eqp_param`, `tc_eqp_port_status`, `tc_eqp_state`, `tc_eqp_state_hist`, `tc_eqp_log`)과 JAR 플러그인 테이블(`tc_jar_business`, `tc_jar_gateway`)에 대한 JPA 엔티티를 구현한다. `tc_eqp`는 `model_version_key`를 통해 모델 버전과 연관 관계를 가지며, JAR 테이블은 `eqp_key` 기준 1:1 관계로 장비별 플러그인 JAR 바이너리를 저장하는 구조를 반영한다.

3. **작업·메시지 도메인 MyBatis Mapper 구현(`tc-db-mybatis-common-schema`)**
   복잡한 계층 조회가 필요한 작업 도메인(`tc_work` → `tc_work_carrier` → `tc_work_carrier_slot`, `tc_work_controljob` → `tc_work_processjob` → `tc_work_processjob_lot_map` ↔ `tc_work_lot`)과 메시지 Outbox 도메인(`tc_msg_send_queue`, `tc_msg_send_log`)에 대해 MyBatis Mapper XML 및 인터페이스를 구현한다. Transactional Outbox 패턴 폴링 쿼리(`FOR UPDATE SKIP LOCKED`)를 비롯한 핵심 조회 패턴을 Mapper로 구현한다.

4. **Kafka 메시지 도메인 및 계약 구조 구현(`tc-messaging-domain`, `tc-messaging-kafka-contract`)**
   Kafka 토픽 명세(`TcKafkaTopics`), 소스 명세(`TcKafkaSources`), 메시지 봉투 구조(`TcKafkaEnvelope`, `TcKafkaMetadata`)를 도메인 모듈에 정의한다. UI 태스크 명령/응답 계약(`KafkaUiTaskMessage`, `KafkaUiTaskReplyMessage`, `KafkaUiTaskEventType`, `KafkaUiTaskReplyEventType`)을 계약 모듈에 정의하여 게이트웨이·비즈니스·UI 백엔드 간 메시지 규약을 통일한다.

5. **Kafka 런타임 및 발행 어댑터 구현(`tc-messaging-kafka-runtime`, `tc-messaging-kafka-starter`)**
   `AbstractKafkaConsumerLifecycle`을 기반으로 Kafka 컨슈머 생명주기를 표준화하고, 정책 기반 재시도(`KafkaConsumerRuntimePolicy`)를 적용한 `AbstractPolicyDrivenKafkaConsumerLifecycle`을 구현한다. `KafkaMessagePublisher`로 메시지 발행 공통 어댑터를 구현하고, `TcMessagingKafkaAutoConfiguration`을 통해 Kafka 설정을 자동 구성으로 제공하는 스타터를 완성한다.

### 결론 및 차주 계획

금주는 전체 도메인 영역의 DB 엔티티·매퍼 구현과 Kafka 메시징 인프라 계층을 완성하였습니다. JPA와 MyBatis를 도메인 특성에 따라 분리 적용하여 복잡한 계층 조회와 단순 CRUD를 각각 최적화된 방식으로 처리할 수 있는 기반을 마련하였습니다.

차주(3주차, 03.17 ~ 03.21)에는 아래 항목을 중점적으로 수행할 예정입니다.

- 통신 도메인 모델(`tc-comm-domain`) 및 코어 인터페이스(`tc-comm-core`) 구현
- HSMS 프로토콜 타이머 스펙(T3/T5/T6/T7/T8) 및 Select/Deselect·LinkTest 처리 구현
- TCP 소켓 통신 프레임 분리 및 JAR 플러그인 확장 구조 설계
- 게이트웨이 코어 메시지 라우팅 및 Mailbox 기반 순차 처리 흐름 구현

---

## 3주차 (2025.03.17 ~ 2025.03.21)

### 금주 연구 목표

- `libs/comm`: HSMS·Socket 통신 프로토콜 라이브러리 설계 및 구현
- `libs/comm`: 게이트웨이 코어 메시지 라우팅 및 순차 처리 흐름 구현
- 통신 도메인·코어 인터페이스 정의 및 프로토콜 계층 분리 설계

### 세부 연구 내용

1. **통신 도메인 모델 구현(`tc-comm-domain`)**
   설비 통신에 필요한 핵심 도메인 모델을 정의한다. 설비 연결 상태(`ConnectionState`), 통신 인터페이스 유형(HSMS/Socket), 수신·발신 메시지 모델, 설비 식별자 구조 등 게이트웨이 전체에서 공유하는 도메인 객체를 구현한다.

2. **통신 코어 인터페이스 구현(`tc-comm-core`)**
   HSMS와 Socket 통신 방식에 무관하게 게이트웨이 상위 계층에서 통신을 제어할 수 있도록 공통 포트 인터페이스를 정의한다. 메시지 송신, 연결 상태 조회, 연결·해제 제어 인터페이스를 분리하여 구현체 교체가 가능한 구조를 확보한다.

3. **HSMS 프로토콜 구현(`tc-comm-hsms`)**
   반도체 장비 통신 표준인 HSMS 프로토콜을 구현한다. HSMS 타이머 스펙(T3/T5/T6/T7/T8)에 따른 타임아웃 처리, Select/Deselect 요청/응답 처리, LinkTest 주기 발송 및 응답 확인 로직을 구현한다.

4. **TCP 소켓 통신 구현(`tc-comm-socket`)**
   커스텀 TCP 소켓 프로토콜을 사용하는 설비와의 통신 처리를 구현한다. 소켓 연결·해제·재연결 로직, 메시지 프레임 분리 처리, JAR 플러그인을 통해 장비별 커스텀 파싱 로직을 동적으로 교체할 수 있는 확장 구조를 설계한다.

5. **게이트웨이 코어 로직 구현(`tc-comm-gateway-core`)**
   수신된 메시지를 처리 흐름에 따라 라우팅하는 게이트웨이 코어를 구현한다. 설비로부터 수신한 메시지를 `Mailbox` 기반 순차 처리 큐에 적재하고, 플러그인 JAR의 처리 로직을 호출하여 결과를 Kafka로 발행하는 흐름을 구현한다.

### 결론 및 차주 계획

금주는 설비와의 물리적 통신을 담당하는 HSMS·Socket 프로토콜 계층과 게이트웨이 코어 메시지 처리 흐름을 완성하였습니다. 프로토콜 계층과 게이트웨이 코어를 명확히 분리하여 이후 신규 프로토콜 추가 시 코어 로직 변경 없이 확장 가능한 구조를 확보하였습니다.

차주(4주차, 03.24 ~ 03.28)에는 아래 항목을 중점적으로 수행할 예정입니다.

- Netty 기반 네트워크 서버 어댑터 구현: 채널 파이프라인에 HSMS/Socket 핸들러 동적 구성, 설비 eqp_id 바인딩 서비스
- 게이트웨이 DB·Kafka·Redis 어댑터 전체 구현
- JAR 플러그인 동적 로드 및 클래스로더 격리 구조 구현
- `tc-comm-gateway-app` 전체 조립 및 설비 시뮬레이터 1차 연동 검증

---

## 4주차 (2025.03.24 ~ 2025.03.28)

### 금주 연구 목표

- `libs/comm`: Netty 기반 게이트웨이 어댑터 전체 구현 (DB·Kafka·Redis·플러그인)
- `apps/tc-comm-gateway-app`: 전체 모듈 조립 및 설비 시뮬레이터 1차 연동 검증
- 설비 바인딩, 연결 상태 기록, JAR 플러그인 런타임 관리 구현

### 세부 연구 내용

1. **Netty 기반 네트워크 서버 어댑터 구현(`tc-comm-gateway-netty-adapter`)**
   Netty 이벤트 루프 기반 고성능 TCP 서버를 구현한다. 채널 파이프라인에 HSMS 또는 Socket 프로토콜 핸들러를 동적으로 구성(`GatewayChannelHandlerFactory`). 설비를 eqp_id로 식별·바인딩하는 `EqpBindingService`, 바인딩 전 수신 메시지 임시 보관하는 `UnboundInbox`를 구현한다.

2. **게이트웨이 DB 어댑터 구현(`tc-comm-gateway-db-adapter`)**
   활성화된 설비 목록과 통신 설정(HSMS 타이머, 소켓 프로토콜 타입, IP/Port)을 DB에서 로드한다. 설비 연결 상태 변화(`tc_eqp_state`, `tc_eqp_state_hist`)를 DB에 기록하는 기능을 구현한다.

3. **게이트웨이 Kafka 어댑터 구현(`tc-comm-gateway-kafka-adapter`)**
   설비 이벤트 Kafka 발행 어댑터, Business 레이어 설비 제어 명령 구독 어댑터, UI 직접 제어 명령 수신 및 처리 결과 회신, DLQ 발행 처리를 구현한다.

4. **게이트웨이 Redis 어댑터 및 플러그인 어댑터 구현**
   설비 런타임 상태를 Redis에 관리하는 `GatewayEquipmentRuntimeService`, 처리 불가 메시지 Redis DLQ 적재, `tc_jar_gateway` JAR 동적 로드를 실행하는 `GatewaySocketPluginRuntimeManager`를 구현한다.

5. **`tc-comm-gateway-app` 조립 및 설비 시뮬레이터 1차 연동 검증**
   모든 어댑터와 코어를 앱에 조립한다. TCP 접속 → HSMS Select 핸드셰이크 → 메시지 송수신 → Kafka 발행 전체 흐름을 설비 시뮬레이터와 1차 검증한다.

### 결론 및 차주 계획

금주는 게이트웨이 어댑터 전체를 구현하고 `tc-comm-gateway-app`을 완성하였습니다. 설비 시뮬레이터와의 HSMS 통신 및 Kafka 메시지 발행 흐름을 1차 검증하여 게이트웨이 레이어의 기능적 완성도를 확인하였습니다.

차주(5주차, 03.31 ~ 04.04)에는 아래 항목을 중점적으로 수행할 예정입니다.

- 비즈니스 도메인 및 모델 런타임 스냅샷 구조 구현
- 비즈니스 코어 런타임 엔진 및 Mailbox 기반 태스크 처리 구현
- 워크플로우 매처·액션 실행기·`@TcAction` 핸들러 등록 구조 구현
- 비즈니스 DB·Kafka 어댑터 구현 (모델 런타임 캐시, 설비 이벤트 구독)

---

## 5주차 (2025.03.31 ~ 2025.04.04)

### 금주 연구 목표

- `libs/business`: 비즈니스 코어 도메인·런타임 엔진·워크플로우 엔진 구현
- `libs/business`: 비즈니스 DB·Kafka 어댑터 구현
- 모델 런타임 캐시 조립 및 플러그인 JAR 동적 로드 확장 구조 설계

### 세부 연구 내용

1. **비즈니스 도메인 및 모델 런타임 구조 구현(`tc-business-domain`)**
   모델 런타임 스냅샷(`BusinessModelRuntimeSnapshot`, `TcModelRuntime`), `SecsWorkflowKey`, `WorkflowRuntimeEntry`, `MdfRuntimeDefinition` 등 런타임 처리에 필요한 도메인 객체를 구현한다.

2. **비즈니스 코어 런타임 엔진 구현(`tc-business-core`)**
   수신 이벤트를 처리하는 `BusinessRuntimeEngine`, `Mailbox` 기반 태스크(`BusinessMailboxTask`), 처리 결과를 설비 제어 명령 발행 또는 MES 명령 발행으로 연결하는 포트 인터페이스를 정의한다.

3. **워크플로우 엔진 구현**
   수신 메시지와 모델 워크플로우를 매칭하는 `BusinessWorkflowMatcher`, 액션을 실행하는 `BusinessWorkflowActionExecutor`, `@TcAction` 어노테이션 기반 핸들러 등록 구조, 플러그인 JAR에서 커스텀 액션을 동적으로 로드하는 `BusinessWorkflowPluginRuntimeProvider`를 구현한다.

4. **비즈니스 DB 어댑터 구현(`tc-business-db-adapter`)**
   모델 정보를 DB에서 읽어 런타임 캐시로 변환하는 `BusinessModelRuntimeAssembler`와 `BusinessModelRuntimeCache`를 구현한다. `tc_model_version` 하위 전체 테이블을 조인 조회하여 런타임 객체로 조립한다.

5. **비즈니스 Kafka 어댑터 구현(`tc-business-kafka-adapter`)**
   설비 이벤트 구독, MES 이벤트 구독, 설비 제어 명령 발행, MES 명령 발행 어댑터를 구현한다. UI 태스크 명령 처리 파이프라인(`BusinessUiTaskExecutorImpl`, `BusinessUiTaskProcessorRegistry`, `BusinessUiTaskDlqReporter`)을 구현한다.

### 결론 및 차주 계획

금주는 비즈니스 코어 계층의 런타임 엔진·워크플로우 엔진·DB·Kafka 어댑터를 완성하였습니다. 모델 런타임 캐시를 기반으로 실시간 이벤트 처리 흐름의 기반을 마련하였으며, 플러그인 JAR 동적 로드 구조로 커스텀 비즈니스 로직 확장이 가능한 구조를 확보하였습니다.

차주(6주차, 04.07 ~ 04.11)에는 아래 항목을 중점적으로 수행할 예정입니다.

- 모델·설비 관리 비즈니스 로직 구현 (CRUD, 버전 상태 관리, 런타임 캐시 갱신)
- 작업(Work) 도메인 비즈니스 로직 구현 (SECS/GEM 계층 구조 생성, 상태 전이)
- `tc-business-core-app` 조립 및 MES 시뮬레이터 Kafka 연동 End-to-End 검증

---

## 6주차 (2025.04.07 ~ 2025.04.11)

### 금주 연구 목표

- `libs/business`: 모델·설비·작업(Work) 비즈니스 로직 전체 구현
- `apps/tc-business-core-app`: 전체 모듈 조립 및 MES 시뮬레이터 연동 검증
- Redis DLQ, 비즈니스 플러그인 런타임 어댑터 완성

### 세부 연구 내용

1. **모델 관리 비즈니스 로직 구현**
   `tc_model`, `tc_model_version` 및 하위 9개 테이블 생성·조회·수정·삭제 비즈니스 로직을 구현한다. 버전 상태 관리, 버전 변경 시 연결 설비 영향 검증, 모델 런타임 캐시 갱신 로직을 포함한다.

2. **설비 관리 비즈니스 로직 및 JAR 플러그인 관리 구현**
   설비 등록·수정·활성화/비활성화 로직을 구현한다. 설비 등록 시 통신 인터페이스에 따른 HSMS/Socket 설정 분기 생성 트랜잭션 처리, `tc_jar_business` UPSERT 및 플러그인 동적 로드 관리 기능을 구현한다.

3. **작업(Work) 비즈니스 로직 구현**
   MES 작업 시작 명령 수신 → `tc_work` 생성 → SECS/GEM 계층 구조(Work → ControlJob → ProcessJob → Lot) 하위 레코드 순차 생성을 구현한다. 작업 상태 전이, 캐리어·슬롯 상태 업데이트, 작업 완료 후 MES 보고 발행 흐름을 구현한다.

4. **비즈니스 Redis 어댑터 및 플러그인 어댑터 완성**
   `RedisBusinessDlqPublisher`, `RedisBusinessUiTraceIdDeduplicationStore`, `BusinessWorkflowPluginRuntimeManager` 보안 검증 및 클래스로더 격리 구조를 구현한다.

5. **`tc-business-core-app` 조립 및 MES 시뮬레이터 Kafka 연동 검증**
   모든 어댑터와 코어를 앱에 조립한다. MES 시뮬레이터 Kafka 메시지 수신 → 작업 생성 → Business 처리 → 게이트웨이 설비 명령 발행 End-to-End 흐름과 역방향(설비 수신 → 게이트웨이 → 비즈니스 이벤트 처리 → MES 응답 발행) 흐름도 함께 검증한다.

### 결론 및 차주 계획

금주는 비즈니스 핵심 로직(모델·설비·작업 도메인)을 완성하고 `tc-business-core-app`을 조립하였습니다. MES 시뮬레이터와의 Kafka 연동을 통해 작업 생성부터 설비 제어 명령 발행까지의 End-to-End 흐름을 검증하였습니다.

차주(7주차, 04.14 ~ 04.18)에는 아래 항목을 중점적으로 수행할 예정입니다.

- UI 도메인·코어 포트 인터페이스 전체 정의
- UI DB 어댑터 구현 (사용자·그룹·권한·세션·설비·모델 CRUD)
- UI 웹 어댑터 구현 (인증·설비·모델·사용자 API 컨트롤러, 비동기 결과 폴링 API)
- UI Kafka·Redis 어댑터 구현 (명령 응답 구독, 비동기 결과 저장·조회)

---

## 7주차 (2025.04.14 ~ 2025.04.18)

### 금주 연구 목표

- `libs/ui`: UI 도메인·코어·DB·Web·Kafka·Redis 어댑터 전체 구현
- 인증(JWT)·설비·모델·사용자·권한 관련 포트 인터페이스 및 어댑터 완성
- 비동기 명령 처리 결과 추적 구조 설계 및 Redis 기반 구현

### 세부 연구 내용

1. **UI 도메인 모델 구현(`tc-ui-domain`)**
   인증 관련 모델(`AuthToken`, `UserPrincipal`), UI 태스크 처리 결과 모델(`UiTaskResult`, `UiTaskStatus`)을 정의한다. 비동기 명령 처리 결과 추적 구조를 도메인 모델로 분리한다.

2. **UI 코어 포트 인터페이스 구현(`tc-ui-core`)**
   DB 포트(사용자·그룹·권한·세션·설비·모델 CRUD), 메시징 포트(이벤트 발행, 명령 수신, 게이트웨이 라우팅), Redis 포트(비동기 결과 저장, 토큰 캐시, DLQ 조회) 인터페이스를 정의한다. 공통 예외 클래스 및 `PagedResponse`를 구현한다.

3. **UI DB 어댑터 구현(`tc-ui-db-adapter`)**
   사용자·그룹·권한 CRUD 어댑터, 세션 관리 어댑터, 설비 목록 조회 어댑터, 모델 CRUD 어댑터, 그룹·권한 매핑 어댑터, API 권한 검증 어댑터(`JpaApiPermissionPort`)를 구현한다.

4. **UI 웹 어댑터 구현(`tc-ui-web-adapter`)**
   인증 API(`AuthController`), 설비 API(`EqpController`), 모델 API(`ModelController`), 사용자·그룹·권한 관리 API 컨트롤러를 구현한다. 비동기 명령 수락 응답, 비동기 결과 폴링 API(`AsyncResultController`), 전역 예외 핸들러(`UiApiExceptionHandler`)를 구현한다.

5. **UI Kafka 어댑터 및 Redis 어댑터 구현**
   비즈니스·게이트웨이 방향 이벤트 발행 어댑터, 명령 응답 토픽 구독 어댑터, 비동기 처리 결과 Redis 저·조회 서비스, 토큰 캐시 서비스, Business/Gateway DLQ Redis 조회 서비스를 구현한다.

### 결론 및 차주 계획

금주는 UI 백엔드 전체 어댑터 계층을 완성하였습니다. 도메인·코어·DB·Web·Kafka·Redis 어댑터를 모두 구현하여 `tc-ui-backend-app` 조립을 위한 모든 구성 요소를 준비하였습니다.

차주(8주차, 04.21 ~ 04.25)에는 아래 항목을 중점적으로 수행할 예정입니다.

- 인증 API 완성: JWT 발급·갱신·무효화, Redis 토큰 캐시, API 접근 권한 검증 인터셉터
- 설비·모델·작업·메시지 REST API 완성 및 비동기 폴링 패턴 적용
- `tc-ui-backend-app` 전체 조립 및 로그인부터 설비 조회·모델 관리까지 전체 흐름 검증

---

## 8주차 (2025.04.21 ~ 2025.04.25)

### 금주 연구 목표

- `apps/tc-ui-backend-app`: 전체 REST API 완성 및 앱 조립
- 인증·설비·모델·작업·메시지 API End-to-End 동작 검증
- DLQ 조회·재처리 API 구현 및 비동기 폴링 패턴 완성

### 세부 연구 내용

1. **인증 API 완성 — 로그인·세션·권한 관리**
   JWT 토큰 발급·갱신·무효화를 구현한다. 로그인 시 사용자 조회·검증 후 Access/Refresh Token 발급, 세션 저장, Redis 토큰 캐시 최적화, API 접근 권한 검증 인터셉터를 구현한다.

2. **설비 API 완성 — 조회·생성·수정·생명주기 제어**
   설비 목록 페이지네이션 조회, 단건 상세 조회(HSMS/Socket 설정 포함)를 구현한다. 설비 생성 시 모델 버전 연결 유효성 검증, HSMS/Socket 설정 분기 생성, 생명주기 제어 명령을 Kafka로 발행하고 비동기 결과 폴링 패턴을 적용한다.

3. **모델 API 완성 — 모델·버전·하위 정보 전체 CRUD**
   모델 원장 및 버전 생성·수정·삭제 API를 구현한다. 모델 하위 정보 일괄 UPSERT API(model_param, model_variableid, model_reportid, model_eventid, model_workflow, model_secs_message/socket_message/mdf, model_dcop_item), 변경 후 비즈니스 레이어 모델 런타임 캐시 갱신 명령 Kafka 발행을 구현한다.

4. **작업 이력 및 메시지 조회 API 구현**
   설비별 작업 이력 조회(페이지네이션, 상태 필터), 작업 단건 상세 조회 시 ControlJob → ProcessJob → Lot 계층 구조 반환을 구현한다. Outbox 메시지 발송 현황, 발송 이력, DLQ 메시지 조회 및 재처리 요청 API(`DlqController`)를 구현한다.

5. **`tc-ui-backend-app` 조립 및 전체 API 동작 검증**
   모든 어댑터와 코어를 조립한다. 로그인 → JWT 발급 → 설비 목록 조회 → 설비 생명주기 제어 → 비동기 결과 폴링 전체 흐름과 모델 등록 → 모델 런타임 캐시 갱신 → 비즈니스 레이어 반영 흐름을 검증한다.

### 결론 및 차주 계획

금주는 `tc-ui-backend-app`을 완성하고 전체 REST API의 End-to-End 동작을 검증하였습니다. 인증부터 설비·모델·작업·메시지 도메인까지 모든 API가 정상 동작함을 확인하였습니다.

차주(9주차, 04.28 ~ 05.02)에는 아래 항목을 중점적으로 수행할 예정입니다.

- `nori-tc-ui` 인증 화면 구현: 로그인, 토큰 자동 갱신 인터셉터, Router Guard
- 설비·모델·작업·메시지 관련 전체 화면 구현
- 실제 API 연동 최종 검증

---

## 9주차 (2025.04.28 ~ 2025.05.02)

### 금주 연구 목표

- `nori-tc-ui`: 인증·설비·모델·작업·메시지 전체 화면 구현
- 백엔드 API 실 연동 및 전체 흐름 최종 검증
- 공통 레이아웃·권한 기반 메뉴 동적 제어 구성

### 세부 연구 내용

1. **인증 화면 구현(`features/auth`)**
   로그인 화면(`LoginPage`)을 구현한다. Access/Refresh Token 저장, 만료 시 자동 갱신 axios 인터셉터, 미인증 사용자 리다이렉트 Router Guard를 구현한다.

2. **설비 관리 화면 구현(`features/eqp`)**
   설비 목록(`EqpSidebar`), 상세 정보(`EqpInfoTable`), 파라미터(`EqpParamTable`) 탭 구성을 구현한다. 설비 등록/수정 모달, 연결 상태 Badge 실시간 표시, 생명주기 제어 버튼을 구현한다.

3. **모델 관리 화면 구현(`features/model`)**
   모델 원장 → 버전 목록 → 하위 상세 탭 구성(파라미터 / SECS 메시지 / Socket 메시지 / VariableID / ReportID / EventID / Workflow / MDF / DCOP Item)을 구현한다. 각 탭에서 데이터 조회·등록·수정·삭제 기능을 구현한다.

4. **작업 현황 화면 구현(`features/work`) 및 메시지 조회 화면**
   설비별 진행 중 작업 목록, ControlJob → ProcessJob → Lot 계층 구조 시각화를 구현한다. Outbox 메시지 발송 상태 현황 카드, DLQ 재처리 요청 버튼을 구현한다.

5. **전체 레이아웃 구성 및 연동 최종 검증**
   공통 레이아웃(헤더, 좌측 네비게이션, 메인 콘텐츠)을 구성한다. 권한 기반 메뉴·기능 동적 제어를 적용하고, 로그인부터 설비 조회·모델 관리·작업 모니터링까지 실제 API 연동 최종 검증을 수행한다.

### 결론 및 차주 계획

금주는 `nori-tc-ui` 전체 화면을 구현하고 백엔드 API와의 실 연동을 최종 검증하였습니다. 인증부터 설비·모델·작업·메시지 모니터링까지 전체 사용자 흐름이 정상 동작함을 확인하였습니다.

차주(10주차, 05.05 ~ 05.09)에는 아래 항목을 중점적으로 수행할 예정입니다.

- `nori-tc` 전체 코드 리팩토링: 예외 처리 일관화, 로그 레벨 재검토, 설정 파일 정비
- `nori-tc-ui` 컴포넌트·타입·API 에러 처리 일관화 정비
- 중복 코드 제거, 공통 훅 분리, 타입 안전성 강화

---

## 10주차 (2025.05.05 ~ 2025.05.09)

### 금주 연구 목표

- `nori-tc` 백엔드 전체 코드 품질 정비 (예외 처리·로그·설정)
- `nori-tc-ui` 프론트엔드 컴포넌트·타입·API 처리 정비
- 데모 준비를 위한 코드 안정화

### 세부 연구 내용

1. **`nori-tc` 백엔드 공통 정비**
   예외 처리 일관화, API 응답 포맷 통일(`ApiResponse` 래퍼 적용 누락 항목 점검), 중복 비즈니스 로직 제거 및 공통 유틸로 분리한다.

2. **`nori-tc` 로그 수준 재검토**
   전체 모듈 로그 레벨 재검토, 외부 API 호출·상태 전이·에러 발생 지점 로그 누락 보완, 민감 정보 로그 노출 여부를 점검한다.

3. **`nori-tc` 설정 파일 정비**
   dev/local 환경 설정 분리 상태 점검, 환경변수 누락 항목 확인, 하드코딩된 설정값을 상수화한다.

4. **`nori-tc-ui` 컴포넌트 및 타입 정비**
   중복 컴포넌트 통합, 공통 훅(`useAuth`, `usePagination`, `useAsyncCommand`) 분리, API 응답 타입 정의 누락 항목 보완 및 타입 안전성을 강화한다.

5. **`nori-tc-ui` API 호출 에러 처리 일관화**
   공통 에러 핸들링 적용, 로딩·에러 상태 UI 컴포넌트 공통화, 비동기 폴링 타임아웃 처리를 정비한다.

### 결론 및 차주 계획

금주는 전체 코드의 품질을 정비하고 데모 준비를 위한 코드 안정화를 완료하였습니다. 예외 처리·로그·설정·컴포넌트 전반에 걸쳐 일관성을 확보하였습니다.

차주(11주차, 05.12 ~ 05.16)에는 아래 항목을 중점적으로 수행할 예정입니다.

- 설비 시뮬레이터·MES 시뮬레이터 연동 전체 흐름 재검증
- UI 전체 화면 흐름 실서버 환경 재검증
- 데모 시나리오 수립 및 리허설 1회 실시
- 리허설 발견 버그 수정 및 데모 환경 안정화

---

## 11주차 (2025.05.12 ~ 2025.05.16)

### 금주 연구 목표

- 설비 시뮬레이터·MES 시뮬레이터 연동 전체 흐름 통합 재검증
- 데모 시나리오 확정 및 리허설 1회 실시
- 리허설 발견 버그 수정 및 데모 환경·샘플 데이터 준비

### 세부 연구 내용

1. **설비 시뮬레이터 연동 전체 흐름 재검증**
   TCP 접속 → HSMS Select 핸드셰이크 → 이벤트 메시지 수신 → Kafka 발행 → 비즈니스 처리 → 설비 명령 발행 → 설비 시뮬레이터 응답 전체 흐름을 재검증한다.

2. **MES 시뮬레이터 연동 전체 흐름 재검증**
   작업 시작 명령 발행 → 비즈니스 작업 생성 처리 → 설비 제어 명령 발행 → 작업 완료 후 MES 보고 발행 전체 흐름을 재검증한다. Kafka 메시지 파티셔닝·오프셋 처리 이상 여부를 함께 점검한다.

3. **UI 전체 화면 흐름 재검증**
   로그인 → 설비 연결 상태 확인 → 모델 정보 조회 → 작업 현황 모니터링 → 메시지 DLQ 조회 전체 흐름을 실제 서버 연동 환경에서 재검증한다.

4. **데모 시나리오 수립 및 리허설**
   시연 순서(시스템 기동 → 모델 등록 → 설비 등록 → 시뮬레이터 연결 → 작업 실행 → UI 모니터링)를 확정하고, 리허설을 1회 실시한다.

5. **리허설 발견 버그 수정 및 데모 환경 안정화**
   리허설에서 발견된 버그를 우선순위에 따라 수정하고, 데모용 샘플 데이터 준비 및 DB 초기화 스크립트를 완성한다.

### 결론 및 차주 계획

금주는 전체 통합 흐름을 재검증하고 데모 리허설을 완료하였습니다. 리허설 과정에서 발견된 버그를 수정하여 데모 환경의 안정성을 확보하였습니다.

차주(12주차, 05.19 ~ 05.23)에는 최종 데모를 실행하고 연구 결과 산출물을 취합합니다.

---

## 12주차 (2025.05.19 ~ 2025.05.23)

### 금주 연구 목표

- 최종 데모 실행: 설비 시뮬레이터 + MES 시뮬레이터 연동 End-to-End 시연
- 연구 결과 산출물 전체 취합 및 연구 노트 최종 작성
- 프로젝트 회고 및 잔여 과제 정리

### 세부 연구 내용

1. **데모 환경 최종 점검**
   서버 기동 순서(PostgreSQL → Redis → Kafka → tc-comm-gateway-app → tc-business-core-app → tc-ui-backend-app → nori-tc-ui) 확인, 설비·MES 시뮬레이터 정상 동작 확인, DB 샘플 데이터 적재를 확인한다.

2. **최종 데모 실행**
   모델 등록 → 설비 등록 → 게이트웨이 연결 → 작업 실행 → 작업 현황 모니터링 → MES 보고의 End-to-End 시나리오를 UI 화면을 통해 실시간 시연한다.

3. **데모 결과 기록 및 산출물 취합**
   데모 화면 캡처, 시스템 동작 로그 수집, 주요 API 호출 결과 기록, 연구 노트 증빙 자료(소스코드 스냅샷, 실행 화면, 로그 데이터)를 취합한다.

4. **연구 노트 최종 작성**
   12주차 연구 노트 및 전체 기간 연구 결과를 정리한다. 정량적 결과(구현된 API 수, 처리된 메시지 건수, 구현 모듈 수)를 취합하여 기재한다.

5. **프로젝트 회고 및 잔여 과제 정리**
   구현하지 못한 기능, 개선이 필요한 항목, 향후 고도화 방향을 정리한다.

### 결론

12주간 Nori-TC 프로젝트를 통해 설비 시뮬레이터 및 MES 시뮬레이터와 연동하는 Tool Controller 시스템의 전체 레이어(공통 인프라 → DB → 메시징 → 통신 게이트웨이 → 비즈니스 코어 → UI 백엔드 → 프론트엔드)를 설계·구현하고 End-to-End 동작을 시연하였습니다.
