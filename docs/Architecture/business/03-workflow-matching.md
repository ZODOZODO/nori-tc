# 03. 워크플로우 매칭 (Workflow Matching)

## 개요

Business Core는 장비에서 수신한 인바운드 메시지를 처리할 때, 어떤 워크플로우를 실행할지 결정하는 과정을 **워크플로우 매칭**이라고 부른다.
매칭은 2단계로 이루어진다.

1. **1단계 — Message Key Lookup**: messageName(+SECS이면 eventId/transactionId)으로 후보 목록을 좁힌다.
2. **2단계 — Filter Evaluation**: JSON Rule 형식의 `workflow_filter`를 평가해 최종 실행 대상을 결정한다.

## 왜 2단계로 나누는가?

| 이유 | 설명 |
|------|------|
| 성능 | messageName 인덱스로 후보를 O(1)로 줄인 뒤, 필터만 순차 평가 → 불필요한 JSON 파싱 최소화 |
| 유연성 | filter는 message payload 내 임의의 변수를 조건식으로 기술 가능 → DB 설정만으로 조건 확장 |
| SECS 특화 | HSMS 프로토콜은 eventId/transactionId를 키로 추가 필터링해야 하므로 프로토콜별 분기를 1단계에서 처리 |

---

## 구조 다이어그램

```
BusinessRuntimeEngine
        │
        ▼ (worker thread)
BusinessWorkflowMatcherImpl
        │
        ├─── 1단계: Message Key Lookup ───────────────────────────────
        │       │
        │       ├── modelRuntime.findWorkflowsByMessageName(messageName)
        │       │       → workflowsByMessageName 인덱스 O(1) 조회
        │       │
        │       └── [SECS 프로토콜이면] extractEventId / extractTransactionId
        │               → matchesSecsKey() 로 후보를 추가 필터링
        │
        └─── 2단계: Filter Evaluation ─────────────────────────────────
                │
                ├── payloadExtractor.extractMessageVariables(payload)
                │       → MSG 변수 Map 구성
                ├── payloadExtractor.buildContextVariables(record)
                │       → CTX 변수 Map 구성 (eqpId, messageName, timestamp …)
                │
                └── for each candidate WorkflowRuntimeEntry
                        BusinessWorkflowFilterEvaluator.evaluate(entry, filterContext)
                                │
                                ├── filter 없음 → true (패스)
                                ├── rows/conditions AND 평가
                                └── 통과 시 matched list 에 추가

        결과: BusinessWorkflowMatchResult(matchedWorkflows, filterContext)
```

---

## 핵심 클래스/인터페이스

| 클래스/인터페이스 | 역할 |
|---|---|
| `BusinessWorkflowMatcher` | 매처 포트 인터페이스 |
| `BusinessWorkflowMatcherImpl` | 2단계 매칭 구현체 |
| `BusinessWorkflowPayloadExtractor` | payload JSON → MSG/CTX 변수 Map 변환 |
| `BusinessWorkflowFilterEvaluator` | `workflow_filter` JSON Rule 평가 |
| `BusinessWorkflowMatchResult` | 매칭 결과 (matched list + filterContext) |
| `BusinessWorkflowFilterContext` | 필터 평가 시 참조 데이터 묶음 |
| `WorkflowRuntimeEntry` | 단일 워크플로우 행 정보 (messageName, filter, actionName …) |
| `TcModelRuntime` | messageName 인덱스를 포함한 모델 런타임 캐시 |

---

## 1단계 상세: Message Key Lookup

```
modelRuntime.findWorkflowsByMessageName(record.messageName())
```

- `TcModelRuntime` 내부의 `workflowsByMessageName` 맵에서 O(1) 조회
- 결과가 비어 있으면 즉시 빈 결과(`BusinessWorkflowMatchResult`) 반환 — 불필요한 filter 평가 없음

### SECS 프로토콜 추가 필터링

```
protocolType == SECS
    └── extractEventId(payload)
    └── extractTransactionId(payload)
        → matchesSecsKey(entry, eventId, transactionId)
```

`WorkflowRuntimeEntry`에 `eventId` 또는 `transactionId` 필드가 `null`이면 해당 조건을 **와일드카드**로 간주한다.
즉, 두 필드 모두 null인 워크플로우는 모든 SECS 메시지에 매칭된다.

---

## 2단계 상세: Filter Evaluation

### 변수 소스 타입

| 소스 타입 | 조회 대상 | 예시 |
|-----------|-----------|------|
| `MSG` | message payload에서 추출한 변수 | `{"var":{"name":"status","source":"MSG"}}` |
| `CTX` | context 변수 (eqpId, messageName 등) | `{"var":{"name":"eqpId","source":"CTX"}}` |
| `AUTO` | MSG 우선, 없으면 CTX 폴백 | `{"var":{"name":"status"}}` (source 생략 = AUTO) |

### workflow_filter JSON 구조

```json
{
  "rows": [
    {
      "left": {
        "var": { "name": "status", "source": "MSG" },
        "xform": ["trim", "lower"]
      },
      "op": "eq",
      "right": "ok"
    },
    {
      "expr": {
        "var": { "name": "eqpId", "source": "CTX" }
      },
      "operator": "contains",
      "right": "FAB1"
    }
  ]
}
```

- `rows`(또는 `conditions`) 배열의 각 row는 **AND** 로 평가 → 하나라도 false이면 전체 false
- `left`와 `expr`은 동의어 (호환성 유지)
- `op`과 `operator`도 동의어

### 지원 연산자

| 연산자 | 기호 별칭 | 설명 |
|--------|-----------|------|
| `eq` | `==` | 동등 비교 (숫자 정규화 적용) |
| `ne` | `!=`, `<>` | 불일치 |
| `gt` | `>` | 초과 |
| `gte` | `>=` | 이상 |
| `lt` | `<` | 미만 |
| `lte` | `<=` | 이하 |
| `contains` | — | 문자열 포함 |
| `in` | — | 우변 리스트 내 포함 여부 |

### 지원 변환 함수 (xform)

| 함수 | 설명 | 예시 |
|------|------|------|
| `trim` | 앞뒤 공백 제거 | `"xform": ["trim"]` |
| `lower` | 소문자 변환 | `"xform": ["lower"]` |
| `upper` | 대문자 변환 | `"xform": ["upper"]` |
| `split(delim, idx)` | 구분자로 분리 후 idx 요소 추출 | `"xform": ["split(\",\",0)"]` |
| `substring(start, end)` | 부분 문자열 | `"xform": ["substring(0,4)"]` |
| `length` | 문자열 길이 | `"xform": ["length"]` |
| `toint` | 정수 변환 | `"xform": ["toint"]` |
| `tolong` | long 변환 | `"xform": ["tolong"]` |
| `add(n)` | 덧셈 | `"xform": ["add(1)"]` |
| `sub(n)` | 뺄셈 | `"xform": ["sub(1)"]` |

- xform 변환 실패 시 **이전 값을 유지**하고 warn 로그를 남긴다 (예외 전파 없음)
- 파싱된 filter는 `ConcurrentHashMap` 캐시에 저장 → 동일 filter 반복 파싱 방지

---

## 매칭 흐름 요약

```
인바운드 레코드 수신
        │
        ▼
[1단계] messageName 인덱스 조회
        │
        ├── 결과 없음 → matchedWorkflows = [] 반환 (CONTINUE 처리)
        │
        └── 결과 있음
                │
                ▼
        [SECS 전용] eventId/transactionId 키 필터링
                │
                ▼
        [2단계] filter 평가 (AND 조건)
                │
                ├── filter 없음 → 자동 통과
                ├── filter 통과 → matched 목록에 추가
                └── filter 실패 → 목록에서 제외
                        │
                        ▼
                [평가 예외 발생 시]
                        └── BusinessWorkflowFilterEvaluationException
                                → TaskHandlingPolicyEvaluator → DLQ 처리
```

---

## 매칭 결과 및 후속 처리

| 상황 | `matchedWorkflows` | 후속 동작 |
|------|--------------------|-----------|
| 매칭 없음 | `[]` (빈 리스트) | WORKFLOW_NOT_FOUND → CONTINUE (정상 처리, Kafka ACK) |
| 1개 이상 매칭 | 워크플로우 목록 | 순서대로 액션 실행 |
| filter 파싱/평가 예외 | — | `BusinessWorkflowFilterEvaluationException` → 재시도/DLQ |

> **참고**: 매칭 없음은 오류가 아닌 **정상 경로**다.
> 장비에서 오는 모든 메시지가 워크플로우를 가질 필요는 없으며,
> 미매칭은 `WORKFLOW_NOT_FOUND` 카테고리로 CONTINUE 처리한다.

---

## 운영 포인트

| 항목 | 설명 |
|------|------|
| 매칭 로그 | DEBUG 레벨 — `eqpId`, `messageName`, `candidateCount`, `matchedCount` 출력 |
| filter 캐시 크기 | `ConcurrentHashMap` 무한 증가 가능 — 모델 재로드 시 새 인스턴스 생성으로 초기화됨 |
| filter 파싱 실패 캐시 | 실패 결과도 캐시 → 동일 filter에 대한 반복 파싱 시도 방지 |
| 변환 실패 정책 | xform 실패 시 이전 값 유지 + warn 로그 (silent fallback) |
| SECS 와일드카드 | `eventId`/`transactionId` null = 모든 값 허용 |

---

## 관련 문서

- [공통: 메일박스 순차 처리](../common/09-mailbox-sequential-processing.md) — 매칭 실행 컨텍스트(Worker)
- [Business: 3단계 큐 구조](02-three-stage-queue-structure.md) — 매칭이 일어나는 Worker Pool 위치
- [Business: 워크플로우 액션 타입](04-workflow-action-types.md) — 매칭 이후 액션 실행
- [Business: 모델 런타임 캐시](05-model-runtime-cache.md) — messageName 인덱스 제공
