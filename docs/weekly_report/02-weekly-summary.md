# 2주차 주간 연구개발 활동 보고서 (2026.03.09 ~ 2026.03.13)

## 연구 기간
- 2026. 3. 9. ~ 2026. 3. 13. (1주간)

## 금주 연구 목표
1. libs/db: 전체 도메인 JPA 엔티티 구현 (사용자·인증·모델·설비·JAR 도메인)
2. libs/db: 작업·메시지 도메인 MyBatis Mapper 구현 및 Transactional Outbox 스키마 설계
3. libs/messaging: Kafka 메시지 도메인·계약 구조 및 런타임·발행 어댑터 구현

## 연구 환경(Methodology)
- OS : Ubuntu 22.04 LTS (WSL2)
- Language : Java 21 (Gradle toolchain)
- Build Tool : Gradle 8.x (Kotlin DSL), Spring Dependency Management Plugin 1.1.7
- Framework : Spring Boot 4.0.2, Spring Boot Starter Data JPA
- ORM / DB : Spring Boot Starter Data JPA (Hibernate), MyBatis Spring Boot Starter 4.0.1 (MyBatis Core 3.5.19)
- JDBC Driver : PostgreSQL JDBC 42.7.4
- Object Mapping : MapStruct 1.6.3
- Messaging : kafka-clients 4.1.0, Spring Kafka (Spring Boot BOM 관리)
- Serialization : Jackson Databind (Spring Boot BOM 관리)
- Test : JUnit 5 (junit-bom), Spring Boot Starter Test

## 상세 연구 내용(Process)
[일자별 수행 기록]
- (월): PostgreSQL 전체 테이블 DDL 스크립트 작성 및 tc-db-jpa-site-schema·tc-db-mybatis-site-schema 모듈 구조 설계 (도메인 공통 타입·enum 정비 포함)
- (화): tc-db-jpa-common-schema — 모델 도메인 JPA 엔티티 구현 (tc_model, tc_model_version 및 하위 9개 테이블)
- (수): tc-db-jpa-common-schema — 설비·JAR 도메인 JPA 엔티티 구현 (tc_eqp, tc_eqp_hsms, tc_eqp_socket, tc_eqp_param 및 관련 테이블, tc_jar_business, tc_jar_gateway)
- (목): tc-db-mybatis-common-schema — 작업(Work) 도메인 계층 구조 MyBatis Mapper 구현, Transactional Outbox 메시지 Mapper 구현 (tc_msg_send_queue, tc_msg_send_log)
- (금): tc-messaging-domain, tc-messaging-kafka-contract — Kafka 메시지 도메인 및 계약 구조 정의, tc-messaging-kafka 어댑터·tc-messaging-kafka-runtime·tc-messaging-kafka-starter 구현

[주요 수행 이미지/도표]
1. PostgreSQL DDL 스크립트 작성 및 site-schema 모듈 구조 설계 (03.09)
모델·설비·작업·메시지 Outbox 도메인 전체 테이블에 대한 PostgreSQL DDL 스크립트를 작성하였습니다. 테이블 명세(컬럼 타입·UNIQUE·CHECK 제약 조건·FK CASCADE/RESTRICT 방향)를 DDL로 확정하여 이후 JPA 엔티티 및 MyBatis Mapper 구현의 기준 산출물로 삼았습니다. 아울러 현장(site)별 커스텀 확장을 위한 tc-db-jpa-site-schema(JPA 전용)·tc-db-mybatis-site-schema(MyBatis 전용) 모듈의 패키지 구조와 공통 타입 의존 방식을 설계하고 스캐폴딩을 완료하였습니다. tc-db-domain의 공통 enum 타입(EqpInterfaceType, WorkStatus 등)도 이 단계에서 정비하여 이후 엔티티에서 일관되게 사용할 수 있도록 하였습니다.

2. 모델 도메인 JPA 엔티티 구현 (03.10)
tc_model·tc_model_version 및 하위 9개 테이블(tc_model_param, tc_model_secs_message, tc_model_socket_message, tc_model_variableid, tc_model_reportid, tc_model_eventid, tc_model_workflow, tc_model_mdf, tc_model_dcop_item)에 대한 JPA 엔티티를 구현하였습니다. tc_model_version을 중심으로 하위 9개 테이블이 FK로 연결되는 1:N 관계 구조를 설계하고, 버전별 하위 정보 전체 조회 시 LAZY 로딩 전략을 적용하였습니다.

3. 설비·JAR 도메인 JPA 엔티티 구현 (03.11)
설비 관련 테이블(tc_eqp, tc_eqp_global, tc_eqp_hsms, tc_eqp_socket, tc_eqp_param, tc_eqp_port_status, tc_eqp_state, tc_eqp_state_hist, tc_eqp_log)과 JAR 플러그인 테이블(tc_jar_business, tc_jar_gateway)에 대한 JPA 엔티티를 구현하였습니다. tc_eqp는 tc_model_version과 FK로 연결되며, tc_jar_business·tc_jar_gateway는 eqp_key 기준 1:1 관계로 장비별 플러그인 JAR 바이너리를 저장합니다.

4. 작업 도메인·Outbox MyBatis Mapper 구현 (03.12)
복잡한 계층 조회가 필요한 작업 도메인(tc_work → tc_work_carrier → tc_work_carrier_slot, tc_work_controljob → tc_work_processjob → tc_work_processjob_lot_map ↔ tc_work_lot)에 대한 MyBatis Mapper XML 및 인터페이스를 구현하였습니다. Transactional Outbox 패턴을 위한 tc_msg_send_queue·tc_msg_send_log Mapper도 구현하였으며, 발행 대상 미처리 건 조회 시 FOR UPDATE SKIP LOCKED 힌트를 적용하여 다중 인스턴스 환경에서 중복 처리가 발생하지 않도록 설계하였습니다.

5. Kafka 메시지 도메인·계약·런타임·발행 어댑터 구현 (03.13)
tc-messaging-domain에 Kafka 토픽 명세(TcKafkaTopics), 소스 명세(TcKafkaSources), 메시지 봉투 구조(TcKafkaEnvelope, TcKafkaMetadata)를 정의하였습니다. tc-messaging-kafka-contract에 UI 태스크 명령·응답 계약(KafkaUiTaskMessage, KafkaUiTaskReplyMessage, KafkaUiTaskEventType)을 정의하였습니다. tc-messaging-kafka 어댑터에 KafkaMessagePublisher를 구현하고, tc-messaging-kafka-runtime에 AbstractKafkaConsumerLifecycle·AbstractPolicyDrivenKafkaConsumerLifecycle을 구현하였습니다. tc-messaging-kafka-starter에 TcMessagingKafkaAutoConfiguration을 통해 KafkaTemplate, ConsumerFactory, ProducerFactory 빈을 자동 구성하도록 완성하였습니다.


[도표 1] tc-db-jpa-common-schema 엔티티 계층 구조

모델 도메인
  tc_model
    └── tc_model_version
          ├── tc_model_param
          ├── tc_model_secs_message
          ├── tc_model_socket_message
          ├── tc_model_variableid
          ├── tc_model_reportid
          ├── tc_model_eventid
          ├── tc_model_workflow
          ├── tc_model_mdf
          └── tc_model_dcop_item

설비 도메인
  tc_eqp (← model_version_key FK)
    ├── tc_eqp_global
    ├── tc_eqp_hsms
    ├── tc_eqp_socket
    ├── tc_eqp_param
    ├── tc_eqp_port_status
    ├── tc_eqp_state
    ├── tc_eqp_state_hist
    └── tc_eqp_log
  tc_jar_business  (eqp_key 1:1)
  tc_jar_gateway   (eqp_key 1:1)


[도표 2] Transactional Outbox 패턴 흐름 (MyBatis)

  Business Logic
       │
       ▼ (동일 트랜잭션 내)
  tc_msg_send_queue INSERT   ← 메시지 발행 예약 기록
       │
       │ (commit)
       ▼
  OutboxPoller (스케줄러)
       │
       ├── SELECT ... FOR UPDATE SKIP LOCKED   ← 다중 인스턴스 중복 처리 방지
       │       (tc_msg_send_queue WHERE status = 'PENDING')
       │
       ▼
  KafkaMessagePublisher.publish()
       │
       ├── 성공 → tc_msg_send_queue status = 'SENT'
       │         tc_msg_send_log INSERT (성공 이력)
       │
       └── 실패 → tc_msg_send_queue retry_count++
                   retry_count 초과 시 status = 'FAILED'
                   tc_msg_send_log INSERT (실패 이력)


[도표 3] Kafka 메시지 계약 구조

  tc-messaging-domain
    ├── TcKafkaTopics      ← 토픽 명 상수 (UI_TASK, EQP_EVENT, MES_CMD 등)
    ├── TcKafkaSources     ← 소스 명 상수 (GATEWAY, BUSINESS, UI_BACKEND)
    ├── TcKafkaEnvelope<T> ← 메시지 봉투 (payload + metadata)
    └── TcKafkaMetadata    ← traceId, source, eventType, timestamp

  tc-messaging-kafka-contract
    ├── KafkaUiTaskMessage      ← UI → Business/Gateway 명령 메시지
    ├── KafkaUiTaskReplyMessage ← Business/Gateway → UI 응답 메시지
    ├── KafkaUiTaskEventType    ← 명령 이벤트 타입 열거
    └── KafkaUiTaskReplyEventType ← 응답 이벤트 타입 열거


[도표 4] tc-messaging-kafka 모듈 구조 및 의존 관계

  tc-messaging-kafka-starter      ← AutoConfiguration, Bean 조립
        │
        ├── tc-messaging-kafka (Adapter)  ← KafkaMessagePublisher 구현
        │         └── tc-messaging-core  ← MessagePublisherPort (인터페이스)
        │
        └── tc-messaging-kafka-runtime    ← Consumer 생명주기 공통 구현
                  ├── tc-messaging-kafka-contract ← 메시지 계약 타입
                  ├── tc-common-consumer-runtime  ← 파티션 커밋 추적
                  ├── kafka-clients 4.1.0
                  └── spring-context (SmartLifecycle)


## 트러블슈팅(이슈 해결)
이슈 1. MapStruct annotationProcessor와 JPA 메타모델 생성 순서 충돌
* 발생 이슈(Bug/Fail): MapStruct 매퍼 컴파일 시 JPA Static Metamodel 클래스(_접미사)가 아직 생성되지 않아 MapperImpl 코드에서 심벌을 찾을 수 없다는 컴파일 오류가 발생하였습니다.
* 원인 분석: Gradle annotationProcessor 실행 순서에서 Hibernate JPA 메타모델 생성기와 MapStruct 프로세서가 동일 라운드에 실행되어, Metamodel 클래스 생성 전 MapStruct가 해당 타입을 참조하는 문제였습니다.
* 해결 방안(조치): build.gradle.kts에서 MapStruct 프로세서를 annotationProcessor로 등록하되, Hibernate Metamodel 생성기(hibernate-jpamodelgen)보다 뒤에 선언하여 처리 순서를 보장하였습니다. 컴파일 태스크에 options.compilerArgs 설정으로 순서를 명시하여 재현 불가 수준으로 안정화하였습니다.

이슈 2. tc_work 계층 구조 MyBatis resultMap 다중 중첩 매핑 오류
* 발생 이슈(Bug/Fail): tc_work → tc_work_controljob → tc_work_processjob → tc_work_lot의 4단계 중첩 계층을 MyBatis resultMap으로 매핑할 때 하위 컬렉션이 빈 리스트로 반환되는 오류가 발생하였습니다.
* 원인 분석: 다중 JOIN 쿼리에서 각 계층의 id 컬럼명이 중복되어 MyBatis가 구분하지 못하고 중복 행으로 처리하는 문제였습니다. resultMap의 id 매핑 컬럼이 상위 계층과 동일한 별칭을 사용하여 컬렉션 분기 기준이 올바르게 동작하지 않았습니다.
* 해결 방안(조치): SELECT 절의 모든 컬럼에 계층명 접두사를 포함한 고유 별칭(w_key, cj_key, pj_key, lot_key)을 부여하고, resultMap의 id 태그가 각 계층별 고유 별칭을 참조하도록 수정하였습니다. 단위 테스트에서 4단계 중첩 객체가 올바르게 조립됨을 검증하였습니다.

이슈 3. Kafka Consumer AutoConfiguration 다중 ConsumerFactory 충돌
* 발생 이슈(Bug/Fail): tc-messaging-kafka-starter의 AutoConfiguration에서 ConsumerFactory 빈이 이미 Spring Boot Kafka AutoConfiguration에 의해 등록된 빈과 중복 선언되어 애플리케이션 기동 시 BeanDefinitionOverrideException이 발생하였습니다.
* 원인 분석: Spring Boot Starter Kafka의 KafkaAutoConfiguration이 기본 ConsumerFactory를 자동 등록하는데, 커스텀 스타터에서 @Bean으로 동일 타입을 재선언하여 충돌이 발생하였습니다.
* 해결 방안(조치): 커스텀 빈 선언부에 @ConditionalOnMissingBean(ConsumerFactory.class) 조건을 추가하여 Spring Boot 기본 빈이 등록된 경우 커스텀 빈 등록을 건너뛰도록 수정하였습니다. 동시에 Spring Boot Kafka AutoConfiguration 순서를 @AutoConfigureAfter로 명시하여 조건 판단이 올바른 순서로 수행되도록 하였습니다.

## 연구 결과/분석(Result)
* 정량적 결과(수치):
작성 DDL 스크립트 대상 테이블 수: 총 38개 (모델 11개, 설비·JAR 13개, 작업 9개, Outbox 2개, site-schema 구조 설계 대상 별도)
구현 JPA 엔티티 클래스 수: 총 24개 (모델 11개, 설비·JAR 13개)
구현 MapStruct 매퍼 수: 총 24개 (엔티티 1:1 대응)
구현 MyBatis Mapper XML 수: 총 9개 (작업 도메인 7개, Outbox 2개)
Kafka 계약 타입 정의 수: 12개 (토픽 상수 5개, 메시지 봉투 구조 2개, 계약 메시지 4개, 이벤트 타입 열거 2개 - 일부 중복 카운트 제외)
구현 Kafka 모듈 수: 4개 (tc-messaging-domain, tc-messaging-kafka-contract, tc-messaging-kafka, tc-messaging-kafka-runtime, tc-messaging-kafka-starter)
단위 테스트 작성 수: 총 18건 (MyBatis 중첩 매핑 검증 8건, Kafka 직렬화 계약 검증 6건, AutoConfiguration 조건 검증 4건)

* 결과 해석:
- DDL 스크립트를 선행 작성하여 테이블 제약 조건(UNIQUE, FK 방향, CASCADE 정책)을 JPA 어노테이션 구현 전에 명세로 확정함으로써, 엔티티 구현 과정에서 스키마 해석 오류 없이 일관된 구현이 가능하였습니다.
- tc_work 4단계 계층 구조를 MyBatis resultMap 단일 쿼리로 처리함으로써 N+1 조회 문제를 사전에 방지하였으며, 작업 이력 조회 성능의 기반이 마련되었습니다.
- Transactional Outbox 패턴에 FOR UPDATE SKIP LOCKED를 적용하여 다중 인스턴스 배포 환경에서도 메시지 발행 중복을 원천적으로 방지할 수 있는 구조를 확보하였습니다.
- Kafka 계약 타입을 별도 모듈(tc-messaging-kafka-contract)로 분리하여 게이트웨이·비즈니스·UI 백엔드 레이어 간 메시지 규약을 단일 소스로 관리하는 체계를 완성하였습니다.

## 결론 및 차주 계획
금주는 전체 도메인의 DB 엔티티·매퍼 구현과 Kafka 메시징 인프라 계층을 완성하였습니다. JPA와 MyBatis를 도메인 특성에 따라 분리 적용하여 복잡한 계층 조회와 단순 CRUD를 각각 최적화된 방식으로 처리할 수 있는 기반을 마련하였으며, Kafka 메시지 계약 구조를 독립 모듈로 정의하여 이후 레이어 간 연동의 일관성을 확보하였습니다.

차주(3주차, 03.16 ~ 03.20)에는 아래 항목을 중점적으로 수행할 예정입니다.
- 통신 도메인 모델(tc-comm-domain) 및 코어 인터페이스(tc-comm-core) 구현
- HSMS 프로토콜 타이머 스펙(T3/T5/T6/T7/T8) 및 Select/Deselect·LinkTest 처리 구현
- TCP 소켓 통신 프레임 분리 및 JAR 플러그인 확장 구조 설계
- 게이트웨이 코어 메시지 라우팅 및 Mailbox 기반 순차 처리 흐름 구현
