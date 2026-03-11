# 06. 태스크 재시도/타임아웃 정책 (Task Retry & Timeout Policy)

## 개요

Business Core는 워크플로우 액션 실행 중 발생하는 오류를 단순히 로그로 남기고 버리지 않는다.
실패 종류에 따라 **재시도(RETRY)**, **DLQ**, **CONTINUE(정상 처리 간주)**, **FAIL** 중 하나를 결정한다.
이 결정 로직을 **TaskHandlingPolicyEvaluator**가 담당한다.

---

## 왜 정책 기반 처리가 필요한가?

| 상황 | 단순 예외 전파의 문제 |
|------|---------------------|
| 일시적 DB/외부 API 오류 | 재시도 없이 DLQ로 가면 데이터 손실 |
| 워크플로우 미매칭 | 오류가 아닌데 예외 처리 경로로 빠지면 불필요한 재시도 |
| 무한 재시도 | backoff/상한 없이 재시도하면 시스템 부하 급증 |
| 타임아웃 | 다른 실패와 동일하게 처리하면 원인 추적 어려움 |

---

## 실패 카테고리 (TaskFailureCategory)

| 카테고리 | 발생 원인 | 기본 처리 |
|----------|-----------|-----------|
| `WORKFLOW_NOT_FOUND` | 매칭 워크플로우 없음 | **CONTINUE** (정상 ACK) |
| `FILTER_EVAL` | `workflow_filter` 파싱/평가 예외 | RETRY → 소진 시 DLQ |
| `ACTION_EXEC` | 액션 메서드 실행 예외 | RETRY → 소진 시 DLQ |
| `TIMEOUT` | 태스크 처리 시간 초과 | RETRY → 소진 시 DLQ |
| `UNKNOWN` | 기타 예상치 못한 예외 | RETRY → 소진 시 DLQ |

---

## 정책 결정 흐름

```
TaskHandlingPolicyEvaluator.decide(TaskFailureContext)
        │
        ├── [1] context.timeoutTriggered() == true?
        │       └── 카테고리를 TIMEOUT으로 강제 전환
        │
        ├── [2] category == WORKFLOW_NOT_FOUND?
        │       └── TaskHandlingDecision.continueNormally()  ← Kafka ACK (정상 커밋)
        │
        ├── [3] retryPolicy.evaluate(attempt, failure)
        │       ├── shouldRetry == true
        │       │       └── TaskHandlingDecision.retry(category, backoffMs)
        │       └── shouldRetry == false (소진)
        │               ├── dlqEnabled == true
        │               │       └── TaskHandlingDecision.dlq(category, dlqRecord)
        │               └── dlqEnabled == false
        │                       └── TaskHandlingDecision.fail(category)
        │
        └── BusinessRuntimeEngine.handleFailure() 에서 결정에 따라 분기
                ├── RETRY  → scheduleRetry(backoffMs)
                ├── DLQ    → publishRuntimeDlq() + emitAck(DLQ)
                ├── CONTINUE → emitAck(SUCCESS)
                └── FAIL   → emitAck(FAILED)
```

---

## 재시도 정책 기본값

```properties
# config/tc-business-core.properties
tc.business.core.runtime.task-timeout-ms=180000       # 타임아웃 180초
tc.business.core.runtime.retry-max-attempts=3         # 최대 재시도 횟수 3
tc.business.core.runtime.retry-backoff-ms=200         # 재시도 초기 backoff 200ms
```

| 설정 | 기본값 | 설명 |
|------|--------|------|
| `task-timeout-ms` | 180,000ms (3분) | 단일 태스크 최대 처리 시간 |
| `retry-max-attempts` | 3 | 최대 재시도 횟수 (초기 시도 1회 + 재시도 3회 = 총 4회) |
| `retry-backoff-ms` | 200ms | 재시도 간 대기 시간 (exponential 적용 가능) |

---

## 타임아웃 처리 구조

```
executeWithTimeout(task)
        │
        └── TimeoutBoundRunner(timeoutScheduler, taskTimeoutMs)
                │
                ├── 설정 시간(180s) 내 완료 → 정상 반환
                └── 초과 시 TaskTimeoutExceededException 발생
                        │
                        └── processTask() catch 절에서 포착
                                → handleFailure(task, TIMEOUT, ex, timeoutTriggered=true)
```

타임아웃은 별도 `timeoutScheduler` 스레드(`biz-timeout-*`)에서 감시한다.
워커 스레드는 블로킹되지 않으며, 타임아웃 트리거 시 해당 워커를 인터럽트한다.

---

## 재시도 스케줄링

```
scheduleRetry(task, backoffMs)
        │
        └── timeoutScheduler.schedule(
                () -> mailboxScheduler.enqueue(task.nextAttempt()),
                backoffMs,
                TimeUnit.MILLISECONDS
            )
```

- `task.nextAttempt()` → 시도 횟수(attempt)가 1 증가한 새 `BusinessMailboxTask` 생성
- 재시도는 원래의 `eqpId` 기반 Mailbox로 다시 진입 → **순서 보장 유지**
- 재시도 큐 오버플로우 시 → 즉시 DLQ 처리

---

## DLQ 진입 조건

```
retry 소진 (attempt > retryMaxAttempts) + dlqEnabled == true
        │
        └── DlqRecordFactory.create(context, finalCategory)
                → DlqRecord (payload, reasonCode, category, timestamp …)
        │
        └── BusinessDlqPublisherPort.publish(dlqRecord)
                → Redis DLQ 저장 (7일 TTL)
```

DLQ에 저장된 레코드는 UI 백엔드에서 조회/재처리 가능하다.

> 관련 문서: [공통: DLQ 처리](../common/06-dlq-handling.md)

---

## 결정 결과 요약표

| 결정 | Kafka ACK | 재시도 | DLQ 저장 | 설명 |
|------|-----------|--------|-----------|------|
| `CONTINUE` | SUCCESS 커밋 | 없음 | 없음 | 정상 처리로 간주 (WORKFLOW_NOT_FOUND) |
| `RETRY` | RETRY_SCHEDULED | backoffMs 후 재진입 | 없음 | Mailbox 재진입 |
| `DLQ` | DLQ 커밋 | 없음 | 저장 | 재시도 소진 또는 치명적 오류 |
| `FAIL` | FAILED | 없음 | 없음 | DLQ 비활성 상태 최종 실패 |

---

## Disposition 메트릭

처리 결과는 `BusinessRuntimeDisposition` 메트릭으로 기록된다.

```
ACCEPTED  — 정상 처리 완료 (PROCESSED 또는 WORKFLOW_NOT_FOUND)
RETRY     — 재시도 예약
DLQ       — DLQ 전송
REJECTED  — 큐 오버플로우, Worker Reject 등 거부
```

---

## 예외별 처리 경로 요약

```
processTask()
    │
    ├── SUCCESS                    → ACCEPTED("PROCESSED") + ACK(SUCCESS)
    ├── WORKFLOW_NOT_FOUND         → ACCEPTED("WORKFLOW_NOT_FOUND") + ACK(SUCCESS)
    ├── TaskTimeoutExceededException → handleFailure(TIMEOUT, timeoutTriggered=true)
    ├── BusinessWorkflowFilterEvaluationException → handleFailure(FILTER_EVAL)
    ├── BusinessWorkflowActionExecutionException  → handleFailure(ACTION_EXEC)
    └── Exception                  → handleFailure(UNKNOWN)
```

---

## 운영 포인트

| 항목 | 설명 |
|------|------|
| 재시도 횟수 확인 | `attempt` 필드 로그에서 진행 재시도 횟수 추적 가능 |
| 타임아웃 튜닝 | 외부 API 호출이 포함된 액션이 많다면 `task-timeout-ms` 상향 검토 |
| DLQ 비활성화 | `dlqEnabled=false`이면 재시도 소진 후 FAIL → 오프셋은 진행되고 데이터는 유실 |
| 재시도 Mailbox 순서 | 재시도도 eqpId Mailbox로 진입 → 같은 장비의 다음 메시지 처리 지연 가능 |
| backoff 상한 | `sleepBackoff()`에서 최대 60초로 제한 (무한 대기 방지) |

---

## 관련 문서

- [Business: 3단계 큐 구조](02-three-stage-queue-structure.md) — 재시도가 Mailbox로 재진입하는 경로
- [Business: 워크플로우 매칭](03-workflow-matching.md) — WORKFLOW_NOT_FOUND 발생 지점
- [공통: DLQ 처리](../common/06-dlq-handling.md) — DLQ 저장소 구조
- [공통: Kafka 메시징 패턴](../common/04-kafka-messaging-pattern.md) — Manual ACK/커밋 흐름
