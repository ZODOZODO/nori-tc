# 07. UI 태스크 처리 (UI Task Handling)

## 개요

Business Core는 `tc.ui.events.business` 토픽에서 UI 백엔드가 보낸 **UI 태스크**를 수신한다.
UI 태스크는 장비 제어 명령(시작, 중지 등)이나 모델 갱신 요청 같은 **단발성 비즈니스 명령**이다.

EQP/MES 인바운드 메시지와 달리 UI 태스크는 워크플로우 매칭을 거치지 않고,
`BusinessUiTaskExecutor → KafkaTaskExecutionPipeline` 경로로 직접 처리한다.

---

## 왜 별도 처리 경로인가?

| 구분 | EQP/MES 메시지 | UI 태스크 |
|------|----------------|-----------|
| 처리 대상 | 장비/MES 이벤트 | UI 발행 명령 |
| 워크플로우 매칭 | 필요 | 불필요 |
| 응답 방식 | Kafka 발행 없음 | `tc.ui.commands` 로 결과 응답 |
| 중복 처리 | 없음 | traceId 기반 중복 제거 |
| 처리 경로 | `workflowMatcher → workflowActionExecutor` | `BusinessUiTaskExecutor → KafkaTaskExecutionPipeline` |

---

## 전체 흐름 다이어그램

```
UI 백엔드
    │  KafkaUiTaskMessage (tc.ui.events.business)
    ▼
BusinessKafkaUiTaskListener (Kafka Consumer)
    │
    └── BusinessTaskIngressPort.submit(BusinessInboundRecord)
            │  messageType = UI
            ▼
    BusinessRuntimeEngine
            │  Topic Queue (UI) → Mailbox → Worker
            ▼
    processTask(task)
            │  task.record().messageType() == UI
            ▼
    executeUiTask(task)
            │
            ▼
    BusinessUiTaskExecutorImpl.execute(record)
            │
            ├── objectMapper.readValue(payload) → KafkaUiTaskMessage
            └── KafkaTaskExecutionPipeline.dispatch(request)
                    │
                    ├── [1] traceId 중복 검사 (BusinessUiTraceIdDeduplicationStore)
                    │       └── 중복이면 즉시 PASS 반환 (Kafka 재발행 방지)
                    │
                    ├── [2] eventType → KafkaTaskProcessorSpec 조회
                    │       └── 미등록 eventType → DLQ 보고 + FAIL 반환
                    │
                    ├── [3] processor.process(request) (재시도 포함)
                    │
                    ├── [4] replyPublisher.publishResult() → tc.ui.commands 발행
                    │       └── DEFERRED_ON_PASS 정책 시 PASS 응답 지연
                    │
                    └── [5] deduplicationStore.markProcessed(traceId, ttlMs)
                                → 처리 완료 마킹 (중복 방지 TTL 적용)
```

---

## 핵심 클래스

| 클래스 | 역할 |
|--------|------|
| `BusinessUiTaskExecutor` | UI 태스크 실행 포트 인터페이스 |
| `BusinessUiTaskExecutorImpl` | payload 역직렬화 → 파이프라인 위임 |
| `KafkaTaskExecutionPipeline<KafkaUiTaskMessage>` | 공통 태스크 파이프라인 (중복 제거, 재시도, 응답 발행) |
| `BusinessUiTraceIdDeduplicationStore` | traceId 기반 중복 처리 방지 (인메모리 ConcurrentHashMap) |
| `BusinessUiTaskDlqReporter` | UI 태스크 처리 실패 시 DLQ 보고 |

---

## traceId 중복 제거 (BusinessUiTraceIdDeduplicationStore)

UI 태스크는 Kafka Exactly-Once가 보장되지 않으므로, `traceId` 기반으로 중복 처리를 방지한다.

```
ConcurrentHashMap<traceId, expiresAtEpochMs>
```

| 설정 | 기본값 | 설명 |
|------|--------|------|
| `duplicate-trace-ttl-ms` | 600,000ms (10분) | traceId 보관 유효기간 |
| `duplicate-trace-max-size` | 100,000 | 최대 캐시 항목 수 |

### 동작 원리

```
isProcessed(traceId, nowEpochMs)
    ├── 맵에 없음 → false (처리되지 않음)
    ├── 있지만 expiresAt < now → 만료 제거 후 false
    └── 있고 expiresAt >= now → true (중복!)

markProcessed(traceId, ttlMs, nowEpochMs)
    ├── expiresAt = nowEpochMs + ttlMs 저장
    ├── 256번마다 cleanupExpired() 실행 (manualGC)
    └── 최대 크기 초과 시 가장 오래된 항목 eviction
```

> **주의**: 인메모리 저장이므로 앱 재시작 시 기존 traceId 정보가 초기화된다.
> 재시작 직후에는 TTL 기간 내 traceId가 다시 처리될 수 있다.

---

## 응답 발행 정책 (ReplyPublishMode)

| 정책 | 설명 |
|------|------|
| `IMMEDIATE` | 처리 결과를 즉시 `tc.ui.commands`로 발행 |
| `DEFERRED_ON_PASS` | PASS 결과는 지연, FAIL 결과만 즉시 발행 |

- 비동기 결과 처리(202 패턴)가 필요한 경우 `DEFERRED_ON_PASS` 사용
- Gateway 기동/종료처럼 시간이 걸리는 명령은 처리 완료 후 별도 경로로 응답

---

## UI 태스크 파이프라인 설정

```properties
# config/tc-business-core.properties
tc.business.core.ui-task.source=TC-BUSINESS-CORE
tc.business.core.ui-task.duplicate-trace-ttl-ms=600000   # 10분
tc.business.core.ui-task.duplicate-trace-max-size=100000
tc.business.core.ui-task.task-retry-max-attempts=3
tc.business.core.ui-task.task-retry-backoff-ms=200
tc.business.core.ui-task.reply-retry-max-attempts=3
tc.business.core.ui-task.reply-retry-backoff-ms=200
tc.business.core.ui-task.kafka-listener-enabled=false    # 운영 환경에서 true
```

> **kafka-listener-enabled**: 기본값 `false` — 의도치 않은 환경에서 UI 태스크 소비를 막기 위해
> 운영/검증 환경에서만 `true`로 오버라이드한다.

---

## UI 태스크와 타임아웃

UI 태스크도 동일한 `TimeoutBoundRunner`를 통해 처리된다.
타임아웃 설정은 `runtime.task-timeout-ms` (기본 180초)를 공유한다.

---

## MDC 컨텍스트 전파

```
BusinessUiTaskExecutorImpl.execute(record)
    │
    └── BusinessLogContext.withEqpAndTraceId(requestEqpId, requestTraceId)
            → request 본문에서 추출한 eqpId/traceId로 MDC 재설정
            → 파이프라인 전체 로그에 장비별 상관키 유지
```

> Kafka Consumer 스레드와 Business Worker 스레드가 다르므로,
> Worker 진입 시점과 파이프라인 진입 시점에서 각각 MDC를 재설정한다.

---

## 오류 처리

| 오류 상황 | 처리 |
|----------|------|
| payload 역직렬화 실패 | `IllegalStateException` → `handleFailure(UNKNOWN)` → 재시도/DLQ |
| eventType 미등록 | DLQ 보고 + FAIL 반환 (Kafka ACK는 진행) |
| processor 예외 | 재시도 후 소진 시 DLQ 보고 + FAIL 반환 |
| 응답 발행 실패 | 재시도 후 소진 시 `KafkaTaskReplyPublishException` → DLQ |
| 중복 traceId | 즉시 PASS 반환 (재처리 없음) |

---

## 운영 포인트

| 항목 | 설명 |
|------|------|
| kafka-listener-enabled | 개발 환경에서 `false`, 운영에서 `true`로 명시 설정 필요 |
| 중복 TTL | UI 태스크를 같은 traceId로 재전송할 경우 TTL(600s) 내에는 처리 생략됨 |
| 캐시 크기 초과 | `duplicate-trace-max-size`를 초과하면 가장 오래된 항목부터 eviction |
| 재시작 시 중복 | 앱 재시작 후 TTL 내 traceId가 재처리될 수 있음 (Redis 미사용) |
| DLQ 구분 | UI 태스크 DLQ는 `BusinessDlqMessage` 타입으로 저장 → 일반 EQP 이벤트 DLQ와 구분 |

---

## 관련 문서

- [Business: 3단계 큐 구조](02-three-stage-queue-structure.md) — UI 메시지도 Mailbox Worker를 통해 처리
- [Business: 태스크 재시도/타임아웃 정책](06-task-retry-timeout-policy.md) — 실패 시 정책
- [공통: DLQ 처리](../common/06-dlq-handling.md) — DLQ 저장소
- [UI: Kafka 이벤트 발행](../ui/06-kafka-event-publishing.md) — UI 백엔드의 발행 측
