# 03. 워크플로우 매칭 (Workflow Matching)

## 개요

Business Core는 인바운드 메시지를 처리할 때 어떤 워크플로우를 실행할지 결정하며, 이 과정을 **워크플로우 매칭**이라고 부릅니다.
매칭은 아래 2단계로 수행됩니다.

1. **1단계 - Message Key Lookup**: `messageName` 기준으로 후보 워크플로우를 빠르게 좁힙니다.
2. **2단계 - Filter Evaluation**: `workflow_filter`를 평가해 최종 실행 대상을 결정합니다.

## 왜 2단계로 나누는가?

| 이유 | 설명 |
|------|------|
| 성능 | `messageName` 인덱스로 후보를 먼저 줄인 뒤 필터만 평가하므로 불필요한 JSON 파싱을 최소화합니다. |
| 유연성 | 후보를 좁힌 뒤 `workflow_filter`로 payload 내부 값을 세밀하게 분기할 수 있습니다. |
| SECS 특화 | HSMS 프로토콜은 `eventId`, `transactionId`를 추가 키로 사용하므로 1단계에서 한 번 더 정리할 수 있습니다. |

---

## 구조 다이어그램

```text
BusinessRuntimeEngine
        │
        ▼
BusinessWorkflowMatcherImpl
        │
        ├── 1단계: Message Key Lookup
        │       │
        │       ├── modelRuntime.findWorkflowsByMessageName(messageName)
        │       │       → workflowsByMessageName 인덱스 O(1) 조회
        │       │
        │       └── [SECS 프로토콜이면]
        │               extractEventId(payload)
        │               extractTransactionId(payload)
        │               → matchesSecsKey()로 후보 추가 필터링
        │
        └── 2단계: Filter Evaluation
                │
                ├── payloadExtractor.extractMessageVariables(payload)
                │       → payload 전체를 Map으로 변환
                ├── payloadExtractor.buildContextVariables(record)
                │       → 내부 런타임 문맥 구성
                │
                └── for each candidate WorkflowRuntimeEntry
                        BusinessWorkflowFilterEvaluator.evaluate(entry, filterContext)
                                │
                                ├── filter 없음 → true
                                ├── and/or AST 재귀 평가
                                ├── payload.metadata / payload.data 기준 값 조회
                                └── 통과 시 matched list에 추가

결과: BusinessWorkflowMatchResult(matchedWorkflows, filterContext)
```

---

## 핵심 클래스/인터페이스

| 클래스/인터페이스 | 역할 |
|---|---|
| `BusinessWorkflowMatcher` | 매처 포트 인터페이스 |
| `BusinessWorkflowMatcherImpl` | 2단계 매칭 구현체 |
| `BusinessWorkflowPayloadExtractor` | payload JSON을 runtime에서 사용할 Map으로 변환 |
| `BusinessWorkflowFilterEvaluator` | `workflow_filter` canonical JSON AST 평가기 |
| `BusinessWorkflowMatchResult` | 매칭 결과 모델 |
| `BusinessWorkflowFilterContext` | 원본 record, payload map, 내부 런타임 문맥을 보관 |
| `WorkflowRuntimeEntry` | 단일 워크플로우 엔트리 |
| `TcModelRuntime` | `messageName` 인덱스를 포함한 모델 런타임 캐시 |

---

## 1단계 상세: Message Key Lookup

```java
modelRuntime.findWorkflowsByMessageName(record.messageName())
```

- `TcModelRuntime` 내부 `workflowsByMessageName` 맵에서 O(1) 조회합니다.
- 결과가 비어 있으면 즉시 빈 결과를 반환하므로 불필요한 필터 평가를 하지 않습니다.

### SECS 프로토콜 추가 필터링

```text
protocolType == SECS
    └── extractEventId(payload)
    └── extractTransactionId(payload)
        → matchesSecsKey(entry, eventId, transactionId)
```

- `WorkflowRuntimeEntry.eventId` 또는 `transactionId`가 `null`이면 해당 조건은 와일드카드로 간주합니다.
- 두 필드가 모두 `null`이면 같은 `messageName`을 가진 모든 SECS 메시지에 대해 후보가 됩니다.

---

## 2단계 상세: Filter Evaluation

### 공개 조회 소스

현재 `workflow_filter` 공개 계약은 Kafka payload envelope의 두 블록만 읽을 수 있습니다.

| `from` 값 | 조회 대상 | 예시 |
|---|---|---|
| `data` | payload의 `data` 객체 | `{"from":"data","path":"status"}` |
| `metadata` | payload의 `metadata` 객체 | `{"from":"metadata","path":"eventType"}` |

중요 정책:

- `path`는 선택한 블록 기준 상대 경로입니다.
- `data.status`, `metadata.eventType` 같은 절대 경로는 저장/평가 단계에서 거절됩니다.
- payload 밖 런타임 메타값(`topic`, `partition`, `offset`, `payloadRef`, `record.messageName`)은 공개 `workflow_filter` 계약에서 접근할 수 없습니다.

### payload envelope 예시

```json
{
  "metadata": {
    "eventType": "EQP_CONDITION_CHECK",
    "timestamp": "2026-01-21T02:11:24.521212200Z",
    "source": "WSC-CONSUMER"
  },
  "data": {
    "eqpId": "PHOTO_002",
    "status": "PASS",
    "errorCode": null
  }
}
```

### `workflow_filter` JSON 구조

루트는 반드시 그룹 노드입니다.

```json
{
  "and": [
    {
      "from": "data",
      "path": "status",
      "comparison": "equals",
      "expected": "PASS",
      "transforms": ["trim", "upper"]
    },
    {
      "or": [
        {
          "from": "metadata",
          "path": "eventType",
          "comparison": "equals",
          "expected": "EQP_CONDITION_CHECK"
        },
        {
          "from": "data",
          "path": "eqpId",
          "comparison": "contains",
          "expected": "PHOTO"
        }
      ]
    }
  ]
}
```

### 조건 노드 필드 의미

| 필드 | 의미 |
|------|------|
| `from` | `data` 또는 `metadata` |
| `path` | 선택한 블록 기준 상대 경로 |
| `comparison` | 비교 연산 |
| `expected` | 비교 우변 값 |
| `transforms` | 비교 전에 적용할 변환 체인 |

### 지원 comparison

| comparison | 설명 |
|---|---|
| `equals` | 동등 비교 |
| `not_equals` | 불일치 |
| `greater_than` | 초과 |
| `greater_than_or_equal` | 이상 |
| `less_than` | 미만 |
| `less_than_or_equal` | 이하 |
| `contains` | 문자열 포함 |
| `in` | 우변 리스트 포함 여부 |

숫자 비교는 현재 구현과 동일하게 숫자 우선 정규화 정책을 따릅니다.

### 지원 transforms

| transform | 설명 |
|---|---|
| `trim` | 앞뒤 공백 제거 |
| `lower` | 소문자 변환 |
| `upper` | 대문자 변환 |
| `split(delim, idx)` | 구분자로 분리 후 idx 요소 추출 |
| `substring(start, end)` | 부분 문자열 |
| `length` | 길이 계산 |
| `toint` | 정수 변환 |
| `tolong` | long 변환 |
| `add(n)` | 덧셈 |
| `sub(n)` | 뺄셈 |

### 평가 정책

- `workflow_filter`가 비어 있으면 필터는 자동 통과입니다.
- JSON 파싱 실패 또는 구조 검증 실패는 `BusinessWorkflowFilterEvaluationException`으로 처리합니다.
- 값이 누락되면 해당 조건은 `false`입니다.
- transform 실패 시 이전 값을 유지하고 `warn` 로그를 남깁니다.
- 동일한 filter 문자열은 파싱 결과를 캐시해 반복 파싱 비용을 줄입니다.

### 금지 예시

아래 예전 규약은 더 이상 허용하지 않습니다.

```json
{
  "rows": [
    {
      "left": {
        "var": { "name": "data.status", "source": "MSG" },
        "xform": ["trim", "upper"]
      },
      "op": "eq",
      "right": "PASS"
    }
  ]
}
```

금지 이유:

- `rows`, `left`, `op`, `right`는 예전 flat AND 계약입니다.
- `MSG`, `CTX`, `AUTO`는 payload envelope 기준 설명이 아닙니다.
- `data.status` 같은 절대 경로는 상대 경로 규칙을 위반합니다.

---

## 운영 관점 요약

- 성능 최적화의 핵심은 `messageName` 인덱스로 후보를 먼저 줄이는 1단계입니다.
- 표현력 확장의 핵심은 `and` / `or` 재귀 AST를 사용하는 2단계입니다.
- 운영 표준의 핵심은 `workflow_filter` 공개 계약을 `data`, `metadata`, 상대 `path` 기준으로 고정한 것입니다.

---

## 관련 문서

- [워크플로우 액션 타입](04-workflow-action-types.md)
- [tc-business-core-app 운영 표준](../../../apps/tc-business-core-app/docs/Architecture/01-mdf-action-data-index-standard.md)
