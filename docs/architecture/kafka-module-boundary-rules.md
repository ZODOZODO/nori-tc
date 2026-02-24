# Kafka 모듈 경계 규칙 (최종 정리)

이 문서는 Kafka 관련 모듈 분해 이후의 **최종 경계 규칙**과 **유지보수 규칙**을 정리합니다.

적용 대상:

- `libs/common/tc-common-consumer-runtime`
- `libs/messaging/kafka/*`
- `libs/messaging/starter/tc-messaging-kafka-starter`
- `libs/business/adapter/tc-business-kafka-adapter`
- `libs/comm/adapter/tc-comm-gateway-kafka-adapter`
- `libs/business/tc-business-core`
- `libs/comm/tc-comm-gateway-core`

## 1. 설계 목표

1. 코어(`business-core`, `comm-gateway-core`)에서 Kafka SDK 의존성 제거
2. Kafka adapter 간 공통 기능 중복 제거
3. `starter` 역할을 AutoConfiguration/조립으로 제한
4. 브로커 확장(RabbitMQ/RV 등) 시 동일 패턴 재사용

## 2. 모듈별 책임

### `libs/common/tc-common-consumer-runtime`

브로커 중립 소비 알고리즘/계약을 담당합니다.

- ACK 큐 (`AckQueue`)
- ACK 상태 (`AckStatus`)
- 재시도 정책 (`RetryPolicy`, `RetryDecision`, `FixedRetryPolicy`)
- 중립 파티션 커밋 추적 (`ConsumerPartition`, `PartitionCommitCoordinator`)

금지:

- `org.apache.kafka.*` import

### `libs/messaging/kafka/tc-messaging-kafka-contract`

Kafka 전용 재사용 계약(DTO/인터페이스/헤더 유틸리티)을 담당합니다.

- 메시지 계약 DTO
- Kafka 헤더 유틸리티
- Kafka 메시지 디스패처 계약

### `libs/messaging/kafka/tc-messaging-kafka-runtime`

Kafka 소비 런타임 재사용 구현을 담당합니다.

- 소비 lifecycle 공통 구현
- Kafka 커밋 타입 변환 브리지
- Kafka 런타임 정책/바인딩 모드

### `libs/messaging/starter/tc-messaging-kafka-starter`

Spring Boot AutoConfiguration/Bean 조립만 담당합니다.

금지:

- `starter.runtime`, `starter.contract` 패키지 재도입

### `libs/business/adapter/tc-business-kafka-adapter`
### `libs/comm/adapter/tc-comm-gateway-kafka-adapter`

도메인 코어와 Kafka 공통 런타임/계약을 연결하는 adapter 역할을 담당합니다.

허용:

- `tc-messaging-kafka-contract`
- `tc-messaging-kafka-runtime`
- `tc-messaging-kafka`

금지:

- `com.nori.tc.messaging.kafka.starter.*` import

### `libs/business/tc-business-core`
### `libs/comm/tc-comm-gateway-core`

도메인 정책/오케스트레이션/샤딩 판단 등 코어 로직을 담당합니다.

허용:

- 중립 타입/포트
- 순수 Java 알고리즘 (Kafka 호환 해시 포함)

금지:

- `org.apache.kafka.*` import

## 3. 의존성 방향 규칙

올바른 방향:

- `app` -> `starter`
- `starter` -> `adapter` / `runtime` / `contract`
- `adapter` -> `core` / `runtime` / `contract`
- `core` -> `common-neutral`

금지 방향:

- `core` -> Kafka SDK
- `adapter` -> `starter` 내부 구현 패키지
- `starter` -> 재사용 런타임/계약 구현 직접 보유

## 4. 회귀 방지 가드 테스트

현재 아래 테스트가 구조 회귀를 방지합니다.

- `BusinessCoreArchitectureGuardTest`
- `CommGatewayCoreArchitectureGuardTest`
- `CommonTaskExecutionArchitectureGuardTest`
- `BusinessKafkaAdapterArchitectureGuardTest`
- `CommGatewayKafkaAdapterArchitectureGuardTest`
- `TcMessagingKafkaStarterArchitectureGuardTest`

테스트가 실패하면 우선 아래를 확인합니다.

1. 코어에 Kafka SDK import가 다시 들어왔는지
2. adapter가 `starter.*` 패키지를 import했는지
3. `starter`에 `runtime/contract` 소스가 다시 생겼는지
4. `task-execution`이 구 `kafka.processing` 패키지를 참조하는지

## 5. 향후 브로커 확장 규칙

RabbitMQ / Rendezvous / 기타 브로커를 추가할 때도 동일한 계층 규칙을 사용합니다.

권장 구조:

1. `libs/messaging/<broker>/<broker>-contract`
2. `libs/messaging/<broker>/<broker>-runtime`
3. `libs/messaging/adapter/<adapter-<broker>>`
4. `libs/messaging/starter/<broker>-starter`

핵심 원칙:

- 공통화는 `starter`가 아니라 브로커 전용 공통 계층에서 수행합니다.
- 코어는 브로커 SDK를 몰라야 합니다.
- adapter만 브로커 SDK를 압니다.

## 6. 정리 완료 상태 (이번 단계 기준)

완료된 항목:

1. `tc-common-kafka-consumer-runtime` 구 모듈 제거
2. `settings.gradle.kts` 구 모듈 등록 제거
3. 공통 재시도/ACK/커밋 추적 계약을 `tc-common-consumer-runtime`로 통합
4. Kafka 스타터/어댑터/코어 경계 가드 테스트 추가

유지보수 시 이 문서를 기준으로 경계 위반 여부를 먼저 판단한 뒤 구현을 진행합니다.
