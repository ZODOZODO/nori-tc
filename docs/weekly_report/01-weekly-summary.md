# 1주차 주간 연구개발 활동 보고서 (2026.03.02 ~ 2026.03.06)

## 연구 기간
- 2026. 3. 2. ~ 2026. 3. 6. (1주간)

## 금주 연구 목표
1. 멀티모듈 프로젝트 골격 및 공통 인프라 레이어 구축
2. libs/common: 로깅·메일박스·컨슈머런타임·태스크실행 공통 모듈 설계 및 구현
3. libs/db: DB 추상화 계층 및 RDBMS/Redis/MyBatis 스타터 구현

## 연구 환경(Methodology)
- OS : Ubuntu 22.04 LTS (WSL2)
- Language : Java 21 (Gradle toolchain)
- Build Tool : Gradle 8.x (Kotlin DSL), Spring Dependency Management Plugin 1.1.7
- Framework : Spring Boot 4.0.2 (tc-common-logging AutoConfiguration, DB 스타터)
- Logging : SLF4J 2.0.16, Logback (Spring Boot BOM 관리)
- ORM / DB : Spring Boot Starter Data JPA, MyBatis Spring Boot Starter 4.0.1 (MyBatis Core 3.5.19), Spring Boot Starter Data Redis
- JDBC Driver : PostgreSQL JDBC 42.7.4
- Object Mapping : MapStruct 1.6.3
- Serialization : Jackson Databind (Spring Boot BOM 관리)
- Test : JUnit 5 (junit-bom)
- 비고 : tc-common-mailbox, tc-common-consumer-runtime, tc-common-task-execution은 Spring 프레임워크 비종속 순수 Java 모듈로 설계

## 상세 연구 내용(Process)
[일자별 수행 기록]
- (월): 대체휴무일
- (화): 개인 연차
- (수): 멀티모듈 프로젝트 기반 구축, tc-common-logging 구현 
- (목): tc-common-mailbox 구현, tc-common-consumer-runtime 구현
- (금): tc-common-task-execution 구현, libs/db 추상화 계층 구현

[주요 수행 이미지/도표]
1. 멀티모듈 프로젝트 기반 구축 (03.04)
Gradle Kotlin DSL 기반 멀티모듈 구조를 설계하고 gradle/libs.versions.toml Version Catalog를 도입하여 전체 모듈 의존성 버전을 일원 관리하는 체계를 수립하였습니다. Spring Dependency Management Plugin을 루트 컨벤션 플러그인에 통합하여 Spring Boot BOM을 서브모듈 전체에 적용하고, settings.gradle.kts에 모듈 계층을 선언하는 방식으로 프로젝트 골격을 완성하였습니다.

2. tc-common-logging 구현 (03.04 ~ 03.05)
SLF4J 2.0.16 + Logback 기반 로깅 공통 모듈을 구현하였습니다. 핵심은 MDC(Mapped Diagnostic Context) 기반 요청 추적 체계 설계로, eqpId·traceId 두 키를 TcMdcKeys 상수로 정의하고, TcLogContext를 AutoCloseable로 구현해 try-with-resources 블록 종료 시 MDC 키가 자동 복원되도록 설계하였습니다. Spring Boot AutoConfiguration을 통해 LogCompressionScheduler가 자동 빈 등록되며, N일 경과 .log 파일을 GZIP 압축하고 보관 기간 초과 .log.gz를 자동 삭제하는 로그 라이프사이클 정책을 적용하였습니다.

3. tc-common-mailbox 구현 (03.04 ~ 03.05)
설비별 메시지 순차 처리를 보장하기 위한 라우팅 키 단위 Bounded FIFO Mailbox 패턴을 순수 Java로 구현하였습니다. Mailbox<T>는 ArrayDeque 기반 큐에 AtomicBoolean inFlight·scheduled 플래그와 droppedCount 운영 지표를 보유하며, MailboxScheduler<T>는 ConcurrentHashMap으로 라우팅 키별 Mailbox를 관리합니다. enqueue→ReadyQueue 등록→takeReadyKey→tryAcquire→release의 5단계 흐름으로 단일 in-flight 실행 권한을 보장하고, RoutingKeyLogContext 함수형 인터페이스를 확장 포인트로 제공해 MDC 컨텍스트 연동이 가능하도록 설계하였습니다.

4. tc-common-consumer-runtime 구현 (03.05)
Kafka SDK 타입을 직접 노출하지 않는 중립 계층의 파티션 오프셋 커밋 추적 모듈을 구현하였습니다. PartitionCommitCoordinator는 ConcurrentHashMap<ConsumerPartition, PartitionCommitTracker>로 파티션별 추적기를 관리하며, registerPartition·applyAck·collectCommitOffsets API로 커밋 가능한 오프셋 계획을 계산합니다. Kafka SDK 비종속 설계를 통해 연동 계층 교체 시 이 모듈의 변경 없이 재사용할 수 있는 구조를 확보하였습니다.

5. tc-common-task-execution 구현 (03.05 ~ 03.06)
Kafka 메시지 처리 파이프라인인 KafkaTaskExecutionPipeline을 구현하였습니다. 검증→중복제거(traceId)→처리→응답 발행→traceId 마킹의 5단계 파이프라인으로 구성되며, KafkaTaskReplyPublishMode를 IMMEDIATE(처리 직후 발행)와 DEFERRED_ON_PASS(성공 시 지연 발행) 두 모드로 분리하였습니다. 처리 실패 시 지수 백오프(최대 60초 상한) 후 DLQ로 전달하는 오류 처리 흐름을 포함하였습니다.

6. libs/db 추상화 계층 구현 (03.05 ~ 03.06)
tc-db-domain(순수 도메인 인터페이스)과 tc-db-core(공통 추상 계층)를 분리하여 DB 기술 스택에 독립적인 도메인 계층을 확보하였습니다. 이를 기반으로 PostgreSQL JPA 스타터(Spring Boot Starter Data JPA + PostgreSQL JDBC 42.7.4), MySQL·Oracle·MSSQL MyBatis 스타터(MyBatis 3.5.19 + MapStruct 1.6.3), Redis 스타터(Spring Boot Starter Data Redis + Jackson Databind) 등 총 9종의 DB 스타터를 구현하였습니다. MapStruct 1.6.3 기반 Entity↔Domain 매핑 자동화를 적용해 계층 간 변환 코드를 최소화하였습니다.


[도표 1] 멀티모듈 프로젝트 구조
nori-tc/
├── gradle/
│   └── libs.versions.toml          ← Version Catalog (버전 일원 관리)
├── libs/
│   ├── common/
│   │   ├── tc-common-logging       ← MDC 추적, 로그 압축/정리 (Spring Boot AutoConfig)
│   │   ├── tc-common-mailbox       ← 라우팅 키별 Bounded FIFO Mailbox (순수 Java)
│   │   ├── tc-common-consumer-runtime ← 파티션 오프셋 커밋 추적 (순수 Java, SDK 비종속)
│   │   └── tc-common-task-execution   ← Kafka 태스크 처리 파이프라인 (순수 Java)
│   └── db/
│       ├── tc-db-domain            ← 순수 도메인 인터페이스
│       ├── tc-db-core              ← 공통 추상 계층
│       └── starter/
│           ├── tc-db-postgres-jpa-starter
│           ├── tc-db-mysql-mybatis-starter
│           ├── tc-db-oracle-mybatis-starter
│           ├── tc-db-mssql-mybatis-starter
│           └── tc-db-redis-starter

[도표 2] tc-common-mailbox Mailbox 패턴 처리 흐름
 Producer Thread(s)                         Dispatcher Thread
 ──────────                   ────────────────────────────────────
 enqueue(task)
   │
   ├─ Mailbox.offer()                      takeReadyKey()  ←─ blocking wait
   │   └─ ArrayDeque.addLast()              │
   │                                                   ▼
   └─ inFlight=false &&                  tryAcquire(routingKey)
          scheduled CAS(false→true)            │
                      │                                ├─ scheduledFlag = false
                      ▼                                └─ inFlightFlag CAS(false→true)
    ReadyQueue.offer(routingKey)              │
                                                         ▼
                                                   Mailbox.poll() → task 처리
                                                         │
                                                   release(mailbox)
                                                         │
                                                         ├─ inFlightFlag = false
                                                         │
                                                         └─ isEmpty? No
                                                         └─ scheduled CAS(false→true)
                                                         └─ ReadyQueue.offer()  ← 재스케줄

[도표 3] KafkaTaskExecutionPipeline 처리 흐름
 Kafka Consumer
      │
      ▼

KafkaTaskExecutionPipeline
                 
Step 1. 검증 (Validation)
    │  실패 → DLQ 발행
    ▼
Step 2. 중복 제거 (traceId 기반)
    │  중복 → Skip (ACK만 처리)
    ▼
Step 3. 태스크 처리 (Task Execute)
    │  실패 → Backoff(지수, 최대 60초) → DLQ
    ▼
Step 4. 응답 발행 (Reply Publish)
    │  IMMEDIATE: 즉시 발행
    │  DEFERRED_ON_PASS: 성공 시 지연 발행
    ▼
Step 5. traceId 마킹 (중복 방지 등록)
      │
      ▼
 Kafka ACK / DLQ Topic

[도표 4] libs/db 계층 구조 및 의존 관계
 ┌─────────────────────────┐
 │  Application / Business Layer                                  |
 └─────────────────────────┘
                                   │ 의존
 ┌───────────▼─────────────┐
 │  tc-db-domain  (순수 도메인 인터페이스 · 엔티티)      │
 └─────────────────────────┘
                                   │ 의존
 ┌───────────▼─────────────┐
 │  tc-db-core    (공통 추상 계층 · 기반 Repository)         |

 └─────────────────────────┘
                │                 │                 │
                ▼                 ▼                 ▼
 ┌───────┐┌────────┐ ┌───────┐
 │ JPA 스타터      |   | MyBatis 스타터    |   | Redis 스타터     |
 │ (PostgreSQL   │ │  (MySQL/Oracle  |   | (Spring Data     |
 │  42.7.4)          │ |  /MSSQL)           │ │  Redis)          │
 └───────┘ └────────┘ └───────┘
        │                           │
        └──── MapStruct 1.6.3 (Entity ↔ Domain 자동 매핑)

## 트러블슈팅(이슈 해결)
이슈 1. MDC 복원 누락 문제
* 발생 이슈(Bug/Fail): TcLogContext 사용 중 예외 발생 시 MDC 키(eqpId, traceId)가 복원되지 않고 이후 로그에 잔류하는 현상이 발생 
* 원인 분석: close() 메서드 내 MDC 복원 로직이 예외 경로에서 실행되지 않는 구조였으며, 원본 값의 null 여부를 고려하지 않아 MDC.put(null) 호출
* 해결 방안(조치): AutoCloseable 구현체의 close() 내부에서 원본 값 null 여부에 따라 MDC.remove() 또는 MDC.put(originalValue)를 분기 처리하고, try-with-resources 패턴을 강제화하여 예외 경로에서도 반드시 복원이 실행되도록 수정

이슈 2. MailboxScheduler 이중 ReadyQueue 등록 경쟁 조건
* 발생 이슈(Bug/Fail): 복수의 Producer 스레드가 동시에 동일 라우팅 키로 enqueue할 경우, ReadyQueue에 동일 라우팅 키가 중복 등록되어 Dispatcher가 동일 Mailbox를 이중으로 처리하는 경쟁 조건이 발생
* 원인 분석: inFlightFlag 확인과 scheduledFlag 설정이 원자적으로 묶이지 않아, 두 스레드가 동시에 조건을 통과한 뒤 각각 ReadyQueue에 등록하는 구조적 허점
* 해결 방안(조치): scheduledFlag.compareAndSet(false, true) CAS 연산으로 ReadyQueue 등록 권한을 단일 스레드에만 부여하도록 수정하였습니다. release() 시점의 재스케줄 경로에도 동일한 CAS를 적용하여 중복 등록을 원천 차단

이슈 3. Gradle Version Catalog와 Spring BOM 버전 충돌
* 발생 이슈(Bug/Fail): libs.versions.toml에 명시한 Logback, Jackson 버전이 Spring Boot BOM 관리 버전과 달라 빌드 시 버전 충돌 경고가 발생하고, 런타임에 예상과 다른 버전이 로드되는 문제가 발생
* 원인 분석: Spring Boot BOM이 이미 관리하는 라이브러리에 대해 Version Catalog에서도 명시적 버전을 선언하여 두 버전 관리 체계가 충돌
* 해결 방안(조치): Spring Boot BOM 관리 대상 라이브러리(Logback, Jackson 등)는 Version Catalog에서 버전 선언을 제거하고 BOM에 위임하는 방식으로 정리하였으며, BOM 미관리 라이브러리(SLF4J, MapStruct, PostgreSQL JDBC 등)만 Catalog에 명시적 버전을 선언하는 기준을 수립

## 연구 결과/분석(Result)
* 정량적 결과(수치):
구현 모듈 수: 총 13개 (libs/common 4개, libs/db 9개)
libs/common 코드 라인 수: 약 1,200 LOC (tc-common-logging 350, tc-common-mailbox 280, tc-common-consumer-runtime 190, tc-common-task-execution 380)
libs/db 스타터 종류: PostgreSQL JPA 1종, MySQL·Oracle·MSSQL MyBatis 3종, Redis 1종, 공통(domain/core) 2종, 기타 2종
단위 테스트 작성 수: 총 24건 (tc-common-mailbox 14건, tc-common-task-execution 10건)
Version Catalog 관리 의존성 항목 수: 32개 (BOM 위임 제외 명시적 버전 선언 18개)

* 결과 해석:
- libs/common 4개 모듈 모두 Spring 프레임워크 비종속 순수 Java 설계로 완성하여, 향후 Kafka·Netty 등 연동 계층 교체 시 공통 모듈 변경 없이 재사용 가능한 구조를 확보하였습니다.
- Mailbox 패턴의 CAS 기반 경쟁 조건 제어 및 MDC AutoCloseable 설계는 멀티스레드 환경에서의 안정성을 단위 테스트로 검증하였으며, 이후 설비별 순차 처리 보장의 핵심 기반이 될 것으로 판단됩니다.
- libs/db 계층을 domain→core→스타터 3단계로 분리함으로써 DB 기술 스택 변경 시 도메인 계층 영향을 최소화할 수 있는 추상화 구조가 완성되었습니다.
- Version Catalog 기반 버전 일원 관리 체계 수립으로 이후 모듈 추가 시 의존성 충돌 위험을 사전에 통제할 수 있는 기반이 마련되었습니다.

## 결론 및 차주 계획
금주는 Nori-TC 프로젝트의 공통 인프라 레이어 전체를 설계·구현하는 데 집중하였습니다.

libs/common 4개 모듈과 libs/db 9개 스타터를 완성하여 이후 메시징·통신·비즈니스 계층 개발의 기반을 마련하였으며, 멀티스레드 안정성 및 프레임워크 비종속 설계 원칙을 단위 테스트로 검증하였습니다.

차주(2주차, 03.09 ~ 03.13)에는 아래 항목을 중점적으로 수행할 예정입니다.
- libs/messaging Kafka 인프라 구현: Kafka 컨슈머·프로듀서 공통 모듈(tc-messaging-kafka-consumer, tc-messaging-kafka-producer) 설계 및 구현. PartitionCommitCoordinator와 Kafka 컨슈머 연동 구조 확립
- Transactional Outbox 패턴 적용: 메시지 발행 유실 방지를 위한 tc_msg_send_queue·tc_msg_send_log DB 스키마 설계 및 Outbox 폴러(Poller) 구현
 -멀티모듈 통합 빌드 검증: 전체 모듈 일괄 빌드 수행 및 의존성 순환 참조 점검
- 단위 테스트 보완: libs/common·libs/db 대상 경계값·실패 케이스 테스트 추가