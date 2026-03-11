# 06. DLQ 처리 (Dead Letter Queue)

## 개요

**DLQ(Dead Letter Queue, 데드 레터 큐)** 는 정상 처리에 실패한 메시지를 별도 저장소에 보관하는 패턴입니다.

nori-tc에서는 Kafka 메시지를 처리하다가 오류가 발생하면 해당 메시지를 즉시 버리지 않고,
DLQ에 저장해서 나중에 운영자가 확인하고 재처리하거나 원인을 분석할 수 있도록 합니다.

---

## 왜 DLQ가 필요한가?

### DLQ 없이 오류가 발생하면?

```
시나리오: 설비에 연결되지 않은 상태에서 명령 메시지가 도착

옵션 1 — 그냥 무시
  → 명령이 아무 기록도 없이 사라짐
  → 운영자가 왜 설비가 동작 안 하는지 알 수 없음

옵션 2 — 계속 재시도
  → Kafka offset이 진행되지 않아 뒤에 오는 메시지도 모두 대기
  → 전체 처리가 멈춤 (Head-of-Line Blocking)

옵션 3 — DLQ에 저장하고 다음 메시지 처리
  → 실패 정보가 남아서 원인 분석 가능
  → 다음 메시지 처리는 계속 진행
  → 운영자가 나중에 재처리 가능
```

nori-tc는 **옵션 3** 을 선택합니다.

---

## 전체 흐름

```
Kafka 메시지 수신
       ↓
   처리 시도
       ↓
  처리 성공? ─── Yes ──→ Kafka offset 커밋 (정상 종료)
       │
      No
       ↓
  재시도 가능? ─── Yes ──→ 재시도 (최대 횟수까지)
       │                      │
      No                  모두 실패
       │                      │
       └────────────┬─────────┘
                    ↓
              DLQ에 저장
                    ↓
         Kafka offset 커밋 (다음 메시지로 진행)
```

---

## DLQ 저장 위치

DLQ는 **Redis** 에 저장됩니다. (앱별로 별도의 Redis 인스턴스 또는 키 공간 사용)

| 앱 | Redis 키 패턴 | 보관 기간 |
|----|--------------|---------|
| tc-comm-gateway-app | `tc:comm:gateway:dlq:{messageId}` | 7일 (604,800초) |
| tc-business-core-app | `tc:business:core:dlq:{messageId}` | 별도 설정 |

```properties
# tc-redis.properties (Gateway)
tc.comm.gateway.redis.dlq-ttl-seconds=604800   # 7일
```

---

## DLQ 메시지 구조

DLQ에 저장되는 메시지는 원본 메시지 내용과 실패 원인을 함께 담습니다.

### Business Core DLQ 메시지 구조

```java
// BusinessDlqMessage (도메인 모델)
public record BusinessDlqMessage(
    String source,         // 어느 컴포넌트에서 실패했는지 (예: "BUSINESS_RUNTIME")
    String stage,          // 처리의 어느 단계에서 실패했는지 (예: "PROCESS", "FILTER")
    String reasonCode,     // 실패 이유 코드 (예: "TIMEOUT", "FILTER_EVAL_FAILED")
    String reason,         // 실패 이유 설명 (예외 메시지)

    String topic,          // 원본 Kafka 토픽 이름
    int partition,         // 원본 Kafka partition 번호
    long offset,           // 원본 Kafka offset
    String eqpId,          // 설비 ID
    String traceId,        // 추적 ID (원본 메시지와 동일)

    String messageType,    // 메시지 종류 (EQP, MES, UI)
    String messageName,    // 메시지 이름 (예: "S6F11")
    String payloadRef,     // 원본 페이로드 참조 또는 축약본
    Map<String, String> tags  // 추가 메타데이터
) {}
```

### tags 예시

```json
{
  "failureCategory": "TIMEOUT",
  "attempt": "3",
  "sourceTopic": "tc.eqp.events",
  "sourcePartition": "2",
  "sourceOffset": "14523",
  "exceptionClass": "java.util.concurrent.TimeoutException",
  "attempts": "3",
  "occurredAtEpochMs": "1741692001234"
}
```

---

## DLQ 발생 조건

### tc-comm-gateway-app 에서 DLQ 발생하는 경우

| 조건 | 설명 |
|------|------|
| 설비 미연결 | 명령을 전송해야 하는데 설비와 TCP 연결이 없는 경우 |
| 계약 위반 | 메시지의 필수 필드(commandId, eqpId 등)가 없는 경우 |
| 역직렬화 실패 | JSON 파싱 오류 |
| 큐 오버플로우 | 설비별 인바운드 큐가 가득 찬 경우 |
| 설비 비활성화 | disabled 상태의 설비로 명령이 도착한 경우 |

### tc-business-core-app 에서 DLQ 발생하는 경우

| 조건 | 설명 |
|------|------|
| 처리 타임아웃 | 30초 내 처리 완료 못한 경우 |
| 재시도 초과 | 최대 재시도 횟수(기본 3회) 초과 |
| 필터 평가 오류 | 워크플로우 필터 조건 평가 중 예외 |
| 액션 실행 오류 | 비즈니스 액션 실행 중 복구 불가 오류 |

---

## 재시도 정책

Business Core는 즉시 DLQ로 보내지 않고 먼저 재시도를 시도합니다.

```
처리 실패 (1차 시도)
       ↓
  재시도 가능? (RETRY 판정)
       ↓
  1,000ms 대기 후 2차 시도
       ↓
  또 실패?
       ↓
  1,000ms 대기 후 3차 시도 (최대 3회)
       ↓
  모두 실패 → DLQ 저장
```

```java
// TaskHandlingPolicyEvaluator (Business Core)
// 설정값:
// tc.business.core.runtime.retry-max-attempts=3
// tc.business.core.runtime.retry-backoff-ms=1000
```

---

## DLQ 조회 및 관리 (UI Backend)

운영자는 `tc-ui-backend-app`의 REST API를 통해 DLQ를 조회하고 관리할 수 있습니다.

```
GET  /api/dlq/gateway         → Gateway DLQ 목록 조회
GET  /api/dlq/business        → Business DLQ 목록 조회
DELETE /api/dlq/gateway/{id}  → Gateway DLQ 항목 삭제
DELETE /api/dlq/business/{id} → Business DLQ 항목 삭제
```

UI Backend는 각 앱의 Redis에 직접 접근해서 DLQ를 조회합니다.

```
tc-ui-backend-app
    ├─ Gateway Redis (port 6379) 조회 → tc:comm:gateway:dlq:* 키 조회
    └─ Business Redis (port 6380) 조회 → tc:business:core:dlq:* 키 조회
```

---

## Gateway vs Business Core DLQ 처리 비교

| 항목 | tc-comm-gateway-app | tc-business-core-app |
|------|--------------------|--------------------|
| 저장 위치 | Redis | Redis |
| TTL | 7일 | 별도 설정 |
| 재시도 | 없음 (즉시 DLQ) | 있음 (최대 3회) |
| 구현 클래스 | `DlqStorePort` 구현체 | `BusinessDlqPublisherPort` 구현체 |
| 메시지 구조 | 단순 직렬화 | `BusinessDlqMessage` 구조체 |

### Gateway에서 재시도가 없는 이유

```
Gateway의 명령 처리는 상태에 의존합니다:
- "설비 미연결" 상태에서 재시도해도 결과가 같습니다
- 재시도 간격 동안 상태가 변하지 않으면 의미 없는 재시도만 반복됩니다
- 대신 상태가 변했을 때(설비 재연결) UI에서 재요청하는 방식을 사용합니다
```

---

## 운영 포인트

```
DLQ 모니터링 체크리스트:

1. DLQ 항목 수 증가 추이
   - 갑자기 증가하면 설비 연결 문제 또는 코드 버그 가능성

2. reasonCode 분포
   - TIMEOUT 많음 → 설비 응답 지연, 워크플로우 로직 무거움
   - FILTER_EVAL_FAILED → 워크플로우 필터 조건 오류
   - DESERIALIZATION → 메시지 포맷 변경 또는 잘못된 발행

3. DLQ TTL 이전에 처리
   - 7일(Gateway) 이내에 원인 파악 및 재처리 필요
   - TTL 초과 시 메시지 자동 삭제 → 복구 불가

4. DLQ 재처리 방법
   - 원인 수정 후 UI Backend API로 DLQ 항목 삭제
   - 원본 Kafka offset 기록이 있으면 해당 메시지를 다시 발행해서 재처리
```
