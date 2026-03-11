# 06. Kafka 이벤트 발행 (Kafka Event Publishing)

## 개요

`tc-ui-backend-app`은 EQP 명령(CRUD, START, END)을 **Kafka**를 통해
Gateway와 Business Core에 전달한다.
두 서비스로의 발행 방식은 **목적과 처리 특성**에 따라 다르게 설계되어 있다.

---

## 발행 대상 토픽

| 토픽 | 대상 서비스 | 파티셔닝 | 사용 명령 |
|------|------------|---------|----------|
| `tc.ui.events.gateway` | Gateway | routePartition 고정 | EQP CRUD, START, END |
| `tc.ui.events.business` | Business Core | 라운드로빈 | EQP CRUD |

---

## 왜 두 가지 파티셔닝 방식인가?

### Gateway: routePartition 고정

```
Gateway는 파티션별로 독립된 처리 파이프라인을 운영한다.
같은 EQP(장비)에 대한 명령은 항상 동일 파티션으로 전송해야
순서 보장 + 상태 일관성을 유지할 수 있다.

EQP-001 → routePartition=2 → tc.ui.events.gateway 파티션 2
EQP-002 → routePartition=5 → tc.ui.events.gateway 파티션 5
```

`routePartition`은 DB의 EQP 레코드에 저장된 값으로,
장비 등록 시 관리자가 할당한다.

### Business Core: 라운드로빈

```
Business Core는 eqpId 기반으로 토픽 큐를 분리해 순서를 보장한다.
따라서 어느 파티션으로 전송되든 상관없다.
Kafka 기본 라운드로빈으로 부하를 균등 분산한다.
```

---

## 전체 발행 흐름 다이어그램

```
EqpController
    │
    ├── [CRUD (생성/수정/삭제)]
    │       │
    │       ├── UiGatewayEventKafkaPublisher.publish(event)
    │       │       │  routePartition 조회 (DB)
    │       │       │  메시지 크기 검증
    │       │       └── kafkaTemplate.send(record).get(timeout)
    │       │               → tc.ui.events.gateway [routePartition]
    │       │
    │       └── UiBusinessEventKafkaPublisher.publish(event)
    │               └── kafkaTemplate.send(record).get(timeout)
    │                       → tc.ui.events.business [라운드로빈]
    │
    └── [START / END]
            │
            └── UiGatewayEventKafkaPublisher.publish(startEvent)
                    │  routePartition 조회
                    │  메시지 크기 검증
                    └── kafkaTemplate.send(record).get(timeout)
                            → tc.ui.events.gateway [routePartition]
```

---

## 핵심 클래스

| 클래스 | 역할 |
|--------|------|
| `UiGatewayEventKafkaPublisher` | Gateway 토픽 발행 (routePartition 기반) |
| `UiBusinessEventKafkaPublisher` | Business 토픽 발행 (라운드로빈) |
| `UiKafkaPublishProperties` | Kafka 발행 설정 (타임아웃, 최대 메시지 크기 등) |

---

## UiGatewayEventKafkaPublisher 동작 상세

```
1. eqpId로 routePartition 조회 (DB 또는 캐시)

2. 메시지 크기 사전 검증
       직렬화된 payload 크기 계산
       maxMessageBytes 초과 시 → MessageSizeLimitExceededException 발생
       (Kafka 브로커에 도달하기 전에 차단)

3. ProducerRecord 생성
       topic:     tc.ui.events.gateway
       partition: routePartition
       key:       eqpId  (파티션 내 순서를 위한 키)
       value:     직렬화된 이벤트 payload

4. 동기 발행
       kafkaTemplate.send(record).get(publishTimeoutMs)
       → 타임아웃 내 ACK 수신 확인
       → 실패 시 KafkaEventPublishException 발생
```

---

## 메시지 크기 검증

Kafka는 기본적으로 일정 크기 이상의 메시지를 거부한다.
브로커에서 거부되면 에러 추적이 어려우므로,
**발행 전에 클라이언트 측에서 미리 검증**한다.

```
payload 직렬화 → 바이트 크기 계산
    ├── 크기 <= maxMessageBytes → 정상 발행
    └── 크기 > maxMessageBytes → MessageSizeLimitExceededException
            → 400 Bad Request 반환 (클라이언트 요청 문제)
```

---

## 동기 발행 (Synchronous Publish)

Kafka 발행은 일반적으로 비동기이지만, UI 백엔드는 **동기 발행**을 사용한다.

```java
// 동기 발행 — ACK를 확인한 후 다음 단계로 진행
kafkaTemplate.send(record).get(publishTimeoutMs, MILLISECONDS);
```

### 왜 동기 발행인가?

```
비동기 발행 시:
    발행 요청 후 ACK 없이 다음 로직 진행
    → 브로커 전달 실패를 알 수 없음
    → DeferredResult 등록은 되었지만 Gateway에 메시지 미도달

동기 발행 시:
    발행 성공 확인 후 다음 단계 진행
    → 발행 실패 시 즉시 예외 발생 → 클라이언트에게 명확한 오류 반환
```

---

## 발행 실패 처리

| 실패 상황 | 처리 |
|----------|------|
| Kafka 브로커 미응답 | `publishTimeoutMs` 초과 → `KafkaEventPublishException` |
| 메시지 크기 초과 | 발행 전 검증 → `MessageSizeLimitExceededException` |
| routePartition 미조회 | DB 오류 → 상위 예외로 전파 |

---

## Kafka 메시지 형식

```json
// tc.ui.events.gateway 발행 예시 (EQP START)
{
  "traceId": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
  "eventType": "EQP_START",
  "eqpId": "EQP-001",
  "payload": {
    "modelVersionKey": 100,
    "requestedAt": "2024-01-15T10:30:00Z"
  },
  "source": "TC-UI-BACKEND"
}
```

---

## 설정

```properties
# config/tc-ui-backend.properties (예시)
tc.ui.kafka.publish-timeout-ms=5000         # 발행 타임아웃 (5초)
tc.ui.kafka.max-message-bytes=1048576       # 최대 메시지 크기 (1MB)
tc.ui.kafka.gateway-topic=tc.ui.events.gateway
tc.ui.kafka.business-topic=tc.ui.events.business
```

---

## routePartition 관리

```
routePartition = 장비가 속한 Gateway 처리 파이프라인 번호

설정 위치:
    tc_eqp 테이블의 route_partition 컬럼

할당 기준:
    - Gateway 인스턴스 수
    - 각 파티션의 처리 용량
    - 장비 수 균등 분배 권장

변경 시:
    - DB 업데이트
    - UI 백엔드 캐시 무효화 (재시작 또는 수동 갱신)
    - Gateway 파티션 설정과 반드시 정합성 확인
```

---

## 운영 포인트

| 항목 | 설명 |
|------|------|
| routePartition 정합성 | Kafka 파티션 수 >= 가장 큰 routePartition + 1 이어야 함 |
| 메시지 크기 제한 | 브로커의 `max.message.bytes`와 동기화 필요 |
| 타임아웃 설정 | 브로커 응답 지연이 잦으면 `publish-timeout-ms` 조정 |
| 발행 실패 모니터링 | `KafkaEventPublishException` 로그 → 알람 설정 권장 |
| 라운드로빈 불균형 | Business 토픽은 라운드로빈이므로 파티션 간 불균형 발생 가능 — 파티션 수와 컨슈머 수 조정 |

---

## 관련 문서

- [UI: Dual Response 패턴](04-dual-response-pattern.md) — CRUD 발행 후 응답 대기
- [UI: 비동기 결과 폴링](05-async-result-polling.md) — START/END 발행 후 폴링
- [공통: Kafka 메시징 패턴](../common/04-kafka-messaging-pattern.md) — Kafka 공통 발행/구독 패턴
- [공통: DLQ 처리](../common/06-dlq-handling.md) — 발행 실패 시 DLQ 처리
