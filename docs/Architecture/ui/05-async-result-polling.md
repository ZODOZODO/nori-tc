# 05. 비동기 결과 폴링 (Async Result Polling)

## 개요

EQP(설비) START/END 명령은 처리 시간이 길고 결과를 즉시 알 수 없다.
이런 명령에 대해 HTTP 연결을 오래 붙잡는 대신,
**202 Accepted**를 즉시 반환하고 `traceId`를 발급한다.
클라이언트는 이 `traceId`로 주기적으로 결과를 조회(**폴링**)한다.

---

## 왜 202 + 폴링인가?

| 방식 | 문제점 |
|------|--------|
| 동기 응답 (대기) | Gateway 처리 시간이 길면 HTTP 타임아웃 발생 |
| WebSocket | 구현 복잡도 높음, 상태 관리 필요 |
| SSE (Server-Sent Events) | 단방향, 재연결 시 상태 복구 필요 |
| **202 + 폴링** | 단순한 REST, 클라이언트가 원하는 시점에 조회 가능 ✅ |

---

## 전체 흐름 다이어그램

```
클라이언트
    │  POST /api/eqp/{eqpId}/start
    ▼
EqpController.start()
    │
    ├── [1] traceId 생성 (UUID)
    │
    ├── [2] UiGatewayEventKafkaPublisher.publish(startEvent, traceId)
    │           → tc.ui.events.gateway 발행 (Gateway 수신)
    │
    └── [3] 202 Accepted 즉시 반환
                { "traceId": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx" }


[Gateway 처리 (비동기)]
    tc.ui.events.gateway → Gateway 수신
        │  실제 장비 START 처리 (수 초~수십 초)
        ▼
    Gateway → tc.ui.commands 발행
        { "traceId": "...", "status": "COMPLETED", "result": {...} }


[UI 백엔드 결과 수신]
    UiCommandKafkaSubscriber
        │  tc.ui.commands 토픽 구독
        ▼
    traceId 기준으로 Redis에 결과 저장
        Redis SET: "ui:async:{traceId}" = { status, result, timestamp }
        TTL: 300s (5분)


[클라이언트 폴링]
    GET /api/async/{traceId}
    │
    ▼
AsyncResultController
    │
    ├── Redis GET: "ui:async:{traceId}"
    │
    ├── 없음 (처리 중) → 202 Accepted { "status": "PENDING" }
    │
    ├── status = COMPLETED → 200 OK { "status": "COMPLETED", "result": {...} }
    │
    ├── status = TIMEOUT → 408 Request Timeout
    │
    └── Redis TTL 만료 (5분 초과) → 404 Not Found
```

---

## 핵심 클래스

| 클래스 | 역할 |
|--------|------|
| `EqpController` | START/END 요청 처리 → 202 반환 + traceId 발급 |
| `AsyncResultController` | `GET /api/async/{traceId}` 폴링 처리 |
| `UiCommandKafkaSubscriber` | `tc.ui.commands` 수신 → Redis에 결과 저장 |
| `AsyncResultStore` | Redis 기반 비동기 결과 저장/조회 인터페이스 |
| `UiGatewayEventKafkaPublisher` | Gateway에 START/END 이벤트 발행 |

---

## AsyncResultController 응답 상세

```
GET /api/async/{traceId}
```

| 상황 | HTTP 코드 | 응답 body |
|------|-----------|-----------|
| 처리 중 (Redis에 없음) | 202 Accepted | `{"status": "PENDING"}` |
| 처리 완료 | 200 OK | `{"status": "COMPLETED", "result": {...}}` |
| Gateway 타임아웃 | 408 Request Timeout | `{"status": "TIMEOUT", "errorMessage": "..."}` |
| Gateway 처리 실패 | 200 OK | `{"status": "FAILED", "errorCode": "...", "errorMessage": "..."}` |
| Redis TTL 만료 | 404 Not Found | `{"status": "EXPIRED"}` |

---

## Redis 결과 저장 구조

```
키: "ui:async:{traceId}"
값: {
    "status": "COMPLETED" | "FAILED" | "TIMEOUT",
    "result": { ... },          // COMPLETED 시 결과 데이터
    "errorCode": "...",         // FAILED/TIMEOUT 시 오류 코드
    "errorMessage": "...",      // FAILED/TIMEOUT 시 오류 메시지
    "completedAt": 1234567890   // 완료 시각 (epoch ms)
}
TTL: 300s (5분)
```

---

## 클라이언트 폴링 구현 가이드

```
[권장 폴링 전략]

1. POST /api/eqp/{id}/start → traceId 획득
2. 1~2초 후 첫 폴링 시작
3. 202 PENDING 응답 → n초 대기 후 재폴링 (예: 2초 간격)
4. 200 COMPLETED → 성공 처리
5. 비성공 응답 (FAILED, TIMEOUT, 404) → 오류 처리
6. 최대 폴링 횟수 제한 (예: 30회, 총 60초) — 무한 폴링 방지

[폴링 간격 권장]
  처음: 1~2초 대기
  이후: 2~3초 간격
  최대: 60초 또는 30회
```

---

## Gateway가 응답하지 않는 경우

Gateway가 처리 중 실패하거나 응답을 발행하지 않으면,
Redis에 결과가 저장되지 않아 클라이언트는 계속 `PENDING` 상태를 받게 된다.

이 경우 클라이언트는 폴링 최대 횟수를 초과하면 타임아웃으로 처리하고,
운영자는 Gateway DLQ를 확인해야 한다.

```
최대 폴링 시간 경과 후 클라이언트 → 타임아웃 처리
Gateway 측 → tc.dlq.gateway 또는 로그 확인
```

---

## START vs END vs CRUD 패턴 비교

| 명령 | 패턴 | 설명 |
|------|------|------|
| EQP 생성/수정/삭제 | Dual Response | Gateway + Business 양쪽 완료 대기 |
| EQP START | 202 + 폴링 | Gateway만 처리, 완료까지 시간 소요 |
| EQP END | 202 + 폴링 | Gateway만 처리, 완료까지 시간 소요 |

---

## 설정

```properties
# config/tc-ui-backend.properties (예시)
tc.ui.async-result.ttl-ms=300000     # Redis 결과 보관 시간 (5분, 기본값)
```

---

## 운영 포인트

| 항목 | 설명 |
|------|------|
| Redis TTL | 5분 — 이 시간 내에 폴링하지 않으면 결과 소실 |
| Gateway 미응답 | DLQ 확인 + Gateway 로그 추적 |
| 다중 인스턴스 | Redis 중앙 저장소 사용으로 어느 인스턴스에서 폴링해도 동일 결과 |
| traceId 보관 | 클라이언트는 traceId를 임시 저장해야 폴링 가능 |
| 중복 폴링 방지 | COMPLETED 응답 수신 후 폴링 즉시 중지 권장 |

---

## 관련 문서

- [UI: REST API 구조](01-rest-api-structure.md) — `GET /api/async/{traceId}` 엔드포인트
- [UI: Dual Response 패턴](04-dual-response-pattern.md) — CRUD의 다른 비동기 패턴
- [UI: Kafka 이벤트 발행](06-kafka-event-publishing.md) — Gateway에 START/END 이벤트 발행
- [공통: Redis 통합](../common/07-redis-integration.md) — Redis 비동기 결과 저장소
- [공통: DLQ 처리](../common/06-dlq-handling.md) — Gateway 처리 실패 시 DLQ 확인
