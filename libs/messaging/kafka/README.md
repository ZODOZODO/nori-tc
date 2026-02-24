# `libs/messaging/kafka` 모듈 구조 안내

이 디렉터리는 Kafka 전용 공통 재사용 자산을 모아두는 위치입니다.

목표는 다음과 같습니다.

1. `core` 계층에서 Kafka SDK 의존성을 제거합니다.
2. `business`/`comm` Kafka adapter 간 중복 구현을 줄입니다.
3. `starter`는 AutoConfiguration/조립 전용으로 유지합니다.

## 모듈 역할

### `tc-messaging-kafka-contract`

Kafka 메시징 재사용 계약(DTO/인터페이스/헤더 유틸리티)을 제공합니다.

- 예시: `KafkaUiTaskMessage`, `KafkaUiTaskReplyMessage`, `KafkaHeaderSupport`
- 의도: adapter가 `starter.contract`가 아니라 이 모듈을 직접 참조하도록 고정

### `tc-messaging-kafka-runtime`

Kafka 소비 런타임 재사용 구현을 제공합니다.

- 예시: `AbstractKafkaConsumerLifecycle`, `KafkaConsumerRuntimePolicy`
- 역할: Kafka SDK 타입 변환/커밋 브리지/소비 생명주기 공통화
- 의도: adapter가 `starter.runtime`가 아니라 이 모듈을 직접 참조하도록 고정

## 경계 규칙 (요약)

1. `tc-messaging-kafka-starter`는 `contract/runtime` 구현을 직접 포함하지 않습니다.
2. `business/comm` Kafka adapter는 `com.nori.tc.messaging.kafka.starter.*`를 import하지 않습니다.
3. Kafka SDK 타입은 `contract/runtime/adapter` 계층에서만 다룹니다.
4. `core` 계층은 Kafka SDK 대신 중립 타입/포트만 사용합니다.

## 관련 검증 가드

아래 테스트가 구조 회귀를 방지합니다.

- `TcMessagingKafkaStarterArchitectureGuardTest`
- `BusinessKafkaAdapterArchitectureGuardTest`
- `CommGatewayKafkaAdapterArchitectureGuardTest`

## 확장 규칙 (RabbitMQ / Rendezvous 등)

새 브로커를 추가할 때도 같은 패턴을 유지합니다.

1. `libs/messaging/<broker>/...-contract`
2. `libs/messaging/<broker>/...-runtime`
3. `libs/messaging/adapter/...-<broker>`
4. `libs/messaging/starter/...-<broker>-starter`

즉, "재사용 공통화"는 `starter`가 아니라 `<broker>` 전용 공통 계층에서 수행합니다.
