# 04. Dual Response 패턴 (Dual Response Pattern)

## 개요

EQP(설비) CRUD(생성/수정/삭제) 명령은 **Gateway**와 **Business** 두 서비스에 각각 효과가 있다.
두 서비스 모두 처리를 완료해야 해당 명령이 진정으로 완료된 것이므로,
UI 백엔드는 **양쪽 응답을 모두 수집한 후** 클라이언트에게 최종 결과를 반환한다.
이 패턴을 **Dual Response**라고 부른다.

---

## 왜 Dual Response인가?

```
[예시: EQP 생성]
  DB에 EQP 레코드를 저장하는 것만으로는 부족하다.
  Gateway도 새 EQP를 인식하고 내부 상태를 갱신해야 한다.
  Business Core도 모델 런타임 캐시를 갱신해야 한다.

  두 서비스 중 하나라도 실패하면 → 불일치 상태 발생
```

| 대안 | 문제점 |
|------|--------|
| Gateway에만 명령 | Business가 미처리 → 상태 불일치 |
| Business에만 명령 | Gateway가 미처리 → 상태 불일치 |
| 순차 처리 (A → B) | A 성공 B 실패 시 롤백 어려움, 레이턴시 2배 |
| **Dual Response** | 양쪽에 병렬 발행 → 양쪽 응답 대기 → 일관된 결과 반환 ✅ |

---

## 전체 흐름 다이어그램

```
클라이언트
    │  POST /api/eqp  (EQP 생성)
    ▼
EqpController
    │
    ├── [1] DB 저장 (tc_eqp 테이블)
    │
    ├── [2] DualResponseRegistry.register(traceId)
    │           → CompletableFuture 등록 + orTimeout(10s)
    │
    ├── [3] UiGatewayEventKafkaPublisher.publish(gatewayEvent)
    │           → tc.ui.events.gateway 토픽 (routePartition 고정)
    │
    ├── [4] UiBusinessEventKafkaPublisher.publish(businessEvent)
    │           → tc.ui.events.business 토픽 (라운드로빈)
    │
    └── [5] DeferredResult 반환 (HTTP 연결 보류)


[Gateway 처리]
    tc.ui.events.gateway → Gateway 수신
        │  EQP 등록 처리 완료
        ▼
    Gateway → tc.ui.commands 발행 (traceId + PASS/FAIL)
        │
        ▼
    UiCommandKafkaSubscriber (UI 백엔드)
        │
        └── DualResponseRegistry.completeGateway(traceId, result)


[Business 처리]
    tc.ui.events.business → Business Core 수신
        │  모델 캐시 갱신 완료
        ▼
    Business → tc.ui.commands 발행 (traceId + PASS/FAIL)
        │
        ▼
    UiCommandKafkaSubscriber (UI 백엔드)
        │
        └── DualResponseRegistry.completeBusiness(traceId, result)


[양쪽 완료 시]
DualResponseRegistry
    │  gatewayResult + businessResult 모두 도착
    ▼
CompletableFuture 완료
    │
    ▼
DeferredResult.setResult(finalResponse)
    │
    ▼
클라이언트 ← 200 OK / 201 Created / 204 No Content
```

---

## 핵심 클래스

| 클래스 | 역할 |
|--------|------|
| `DualResponseRegistry` | traceId별 CompletableFuture 관리, 타임아웃 처리 |
| `UiCommandKafkaSubscriber` | `tc.ui.commands` 토픽 구독 → 완료 신호 수신 |
| `EqpController` | DeferredResult 반환, Dual Response 흐름 조율 |
| `UiGatewayEventKafkaPublisher` | Gateway에 Kafka 이벤트 발행 |
| `UiBusinessEventKafkaPublisher` | Business Core에 Kafka 이벤트 발행 |

---

## DualResponseRegistry 상세

```
register(traceId):
    new CompletableFuture<DualResult>()
    → orTimeout(timeoutMs, MILLISECONDS) 설정
    → registry.put(traceId, future)
    → future.whenComplete { result, ex ->
          cleanup: registry.remove(traceId)
      }
    → DeferredResult에 연결


completeGateway(traceId, result):
    future = registry.get(traceId)
    future의 gatewayResult 설정
    양쪽 모두 도착했으면 → future.complete(dualResult)


completeBusiness(traceId, result):
    future = registry.get(traceId)
    future의 businessResult 설정
    양쪽 모두 도착했으면 → future.complete(dualResult)


타임아웃 발생 (10s 초과):
    CompletableFuture → TimeoutException
    DeferredResult.setErrorResult(504 Gateway Timeout)
```

---

## Redis Pub/Sub 분산 완료 신호

여러 UI 백엔드 인스턴스가 실행 중일 때, 특정 인스턴스가 Kafka 응답을 수신해도
DeferredResult를 등록한 인스턴스가 다를 수 있다.

이를 해결하기 위해 **Redis Pub/Sub**으로 완료 신호를 브로드캐스트한다.

```
[인스턴스 A] DeferredResult 등록 (traceId=abc)
[인스턴스 B] tc.ui.commands에서 Gateway 응답 수신 (traceId=abc)
    │
    ├── 로컬 registry에서 traceId=abc 조회 → 없음
    │
    └── Redis PUBLISH: "ui:dual-response:abc" + 결과
            │
            ▼
[인스턴스 A] Redis 구독 → "ui:dual-response:abc" 수신
            │
            └── DualResponseRegistry.completeGateway(traceId=abc, result)
```

---

## 타임아웃 동작

```
기본 타임아웃: 10초 (설정 가능)

타임아웃 발생 시 응답:

{
  "success": false,
  "data": null,
  "errorCode": "DUAL_RESPONSE_TIMEOUT",
  "errorMessage": "처리 시간이 초과되었습니다."
}
HTTP 504 Gateway Timeout
```

> **주의**: 타임아웃이 발생해도 Gateway/Business는 계속 처리 중일 수 있다.
> 클라이언트에게 실패로 응답했더라도, 서버 측에서는 처리가 완료될 수 있다.
> 이 경우 데이터 일관성은 별도 검증이 필요하다.

---

## 응답 코드 정리

| 상황 | HTTP 코드 | 설명 |
|------|-----------|------|
| 생성 성공 | 201 Created | 양쪽 PASS |
| 수정/삭제 성공 | 200 OK / 204 No Content | 양쪽 PASS |
| 타임아웃 | 504 Gateway Timeout | 10초 내 응답 미수신 |
| 어느 한 쪽 FAIL | 500 Internal Server Error | Gateway 또는 Business 처리 실패 |

---

## 설정

```properties
# config/tc-ui-backend.properties (예시)
tc.ui.dual-response.timeout-ms=10000   # 10초 (기본값)
```

---

## 운영 포인트

| 항목 | 설명 |
|------|------|
| 타임아웃 값 | Gateway/Business 처리 시간 + 여유를 고려해 조정 |
| Redis Pub/Sub | UI 백엔드 다중 인스턴스 환경에서 필수 |
| DLQ 확인 | Dual Response FAIL 시 Gateway/Business DLQ 확인 필요 |
| traceId 유일성 | UUID 기반 — 충돌 위험 없음 |
| 타임아웃 후 완료 | 504 응답 후 백엔드에서 처리 완료될 수 있음 — 상태 조회 API로 확인 필요 |

---

## 관련 문서

- [UI: 비동기 결과 폴링 (202 패턴)](05-async-result-polling.md) — START/END의 다른 비동기 패턴
- [UI: Kafka 이벤트 발행](06-kafka-event-publishing.md) — Gateway/Business 발행 방식
- [공통: DLQ 처리](../common/06-dlq-handling.md) — 처리 실패 시 DLQ 저장
- [공통: Redis 통합](../common/07-redis-integration.md) — Redis Pub/Sub 기반 분산 완료 신호
