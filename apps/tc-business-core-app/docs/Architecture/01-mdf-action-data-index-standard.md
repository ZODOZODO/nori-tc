# 01. Workflow Filter / MDF Action Data Index 운영 표준

## 1. 목적

- `tc-business-core-app`에서 사용하는 `workflow_filter`, `action_data_index`, MDF(XML) 작성 기준을 하나의 운영 표준으로 고정합니다.
- 모델 등록자, 운영 담당자, 개발 담당자가 같은 공개 계약을 기준으로 등록/검증/장애 대응을 수행하도록 정렬합니다.
- 실제 구현 코드와 app/root 문서가 동일한 canonical 용어를 사용하도록 유지합니다.

## 2. 적용 범위

- `tc_model_workflow.workflow_filter`
- `tc_model_workflow.action_data_index`
- `modelVersionKey` 단위 MDF(XML) 템플릿 정의
- 모델 상세 화면 preview / 저장 검증 시 노출되는 공개 계약

본 문서는 DB 스키마 변경이나 UI 레이아웃 변경이 아니라, 운영 표준과 공개 JSON 계약을 설명하는 문서입니다.

## 3. 핵심 원칙

- `workflow_filter`는 `and` / `or` 기반 JSON AST만 허용합니다.
- `workflow_filter` 조건 노드는 `from`, `path`, `comparison`, `expected`, `transforms`만 허용합니다.
- `action_data_index` 루트는 `mdfTemplateName`, `fields`만 허용합니다.
- `action_data_index.fields[field]`는 문자열 shorthand 또는 `from`, `path`, `transforms` 객체만 허용합니다.
- 값 조회 시작점은 Kafka payload envelope의 `data`, `metadata` 두 블록으로 고정합니다.
- `path`는 선택한 블록 기준 상대 경로만 허용합니다.
- `action_data_index`가 존재하면 MDF 템플릿 선택은 `mdfTemplateName` 명시 선택만 허용합니다.
- `action_data_index`가 비어 있으면 기존 raw message/data fallback을 유지합니다.
- transform 실패 시 예외를 삼키지 않되, 공개 정책상 이전 값을 유지하고 `warn` 로그를 남깁니다.

## 4. Kafka payload envelope 기준

모든 공개 조회 규칙은 아래 envelope 구조를 기준으로 설명합니다.

```json
{
  "metadata": {
    "eventType": "EQP_CONDITION_CHECK",
    "timestamp": "2026-01-21T02:11:24.521212200Z",
    "source": "WSC-CONSUMER",
    "traceId": "0000000000001"
  },
  "data": {
    "eqpId": "PHOTO_002",
    "status": "PASS",
    "errorCode": null,
    "errorMessage": null
  }
}
```

조회 규칙:

- `from: "data"`면 payload의 `data` 객체에서 조회합니다.
- `from: "metadata"`면 payload의 `metadata` 객체에서 조회합니다.
- `path`는 해당 블록 기준 상대 경로입니다.

허용 예시:

- `{"from":"data","path":"status"}`
- `{"from":"data","path":"eqpId"}`
- `{"from":"metadata","path":"eventType"}`

금지 예시:

- `{"from":"data","path":"data.status"}`
- `{"from":"metadata","path":"metadata.eventType"}`
- `{"from":"data","path":"metadata.eventType"}`

## 5. `workflow_filter` 표준

### 5.1 루트 구조

루트는 반드시 그룹 노드여야 합니다.

```json
{ "and": [ ... ] }
```

또는

```json
{ "or": [ ... ] }
```

규칙:

- 그룹 노드는 `and` 또는 `or` 중 하나만 가질 수 있습니다.
- 그룹 배열은 비어 있으면 안 됩니다.
- 그룹 노드 안에는 그룹 노드와 조건 노드가 재귀적으로 섞여 들어갈 수 있습니다.

### 5.2 조건 노드 구조

조건 노드는 아래 필드만 허용합니다.

- `from`
- `path`
- `comparison`
- `expected`
- `transforms` 선택

```json
{
  "from": "data",
  "path": "status",
  "comparison": "equals",
  "expected": "PASS",
  "transforms": ["trim", "upper"]
}
```

### 5.3 comparison 표준 집합

- `equals`
- `not_equals`
- `greater_than`
- `greater_than_or_equal`
- `less_than`
- `less_than_or_equal`
- `contains`
- `in`

### 5.4 transforms 규칙

- `transforms`는 배열이며 순차 적용합니다.
- 문자열 compact 형식과 `{ "name": "...", "args": [...] }` object 형식을 모두 허용합니다.
- 지원 transform 범위는 현재 구현 기준을 따릅니다. 예: `trim`, `lower`, `upper`, `split(...)`, `substring(...)`, `length`, `toint`, `tolong`, `add(...)`, `sub(...)`
- transform 적용 실패 시 이전 값을 유지하고 `warn` 로그를 남깁니다.

### 5.5 권장 예시

```json
{
  "and": [
    {
      "or": [
        {
          "from": "data",
          "path": "status",
          "comparison": "equals",
          "expected": "PASS",
          "transforms": ["trim", "upper"]
        },
        {
          "from": "metadata",
          "path": "eventType",
          "comparison": "equals",
          "expected": "EQP_CONDITION_CHECK"
        }
      ]
    },
    {
      "from": "data",
      "path": "eqpId",
      "comparison": "contains",
      "expected": "PHOTO"
    }
  ]
}
```

### 5.6 실패 정책

- JSON 파싱 실패 또는 구조 검증 실패는 `workflow_filter evaluation` 예외로 처리합니다.
- 비교 대상 값이 없으면 조건 결과는 `false`입니다.
- payload 바깥 런타임 메타값(`topic`, `partition`, `offset`, `payloadRef`, 내부 `record.messageName` 등)은 공개 계약에서 조회할 수 없습니다.

### 5.7 금지 규약

아래 예전 계약은 저장 검증 기준에서 허용하지 않습니다.

- `rows`, `conditions`
- `left`, `expr`
- `op`, `operator`
- `MSG`, `CTX`, `AUTO`
- `var`, `source`, `xform`
- `from=all`

## 6. `action_data_index` 및 MDF 표준

### 6.1 루트 구조

루트는 아래 두 키로 고정합니다.

- `mdfTemplateName`
- `fields`

```json
{
  "mdfTemplateName": "TOOL_CONDITION_REPLY_MES",
  "fields": {
    "EQPID": "eqpId",
    "STATUS": {
      "from": "data",
      "path": "status",
      "transforms": ["trim", "upper"]
    },
    "EVENT_TYPE": {
      "from": "metadata",
      "path": "eventType"
    }
  }
}
```

### 6.2 fields 표준

단일 필드는 아래 두 형식만 허용합니다.

문자열 shorthand:

```json
{ "EQPID": "eqpId" }
```

- 의미: `from=data`, `path=eqpId`, `transforms=[]`

객체형:

```json
{
  "STATUS": {
    "from": "data",
    "path": "status",
    "transforms": ["trim", "upper"]
  }
}
```

객체형 허용 키:

- `from`
- `path`
- `transforms`

### 6.3 MDF 템플릿 선택 정책

- `action_data_index`가 존재하면 `mdfTemplateName`이 반드시 있어야 합니다.
- MDF 템플릿 선택은 `mdfTemplateName` explicit 선택만 허용합니다.
- `actionName + target(EQP/MES)` 기반 자동 템플릿 선택 fallback은 사용하지 않습니다.
- `mdfTemplateName`이 없거나, 템플릿이 없거나, target이 다르면 액션 실행 실패로 처리합니다.

### 6.4 필드 값 조립 우선순위

필드 값 우선순위는 아래 순서입니다.

1. `action_data_index.fields[field]`
2. MDF `<field>` 정의
3. 빈 문자열

추가 정책:

- `action_data_index` 공개 계약은 비교/상수/필수 여부를 직접 표현하지 않습니다.
- 값이 없으면 빈 문자열로 치환합니다.
- transform 실패 시 이전 값을 유지하고 `warn` 로그를 남깁니다.

### 6.5 MDF `<field>` fallback 규칙

MDF XML의 `<field>` 정의는 action override가 없는 경우에만 fallback으로 사용합니다.

예:

```xml
<field name="EQPID" var="eqpId" source="CTX" required="true"/>
```

주의:

- `var`, `source`, `xform`, `fixed`, `required`는 MDF XML fallback 규칙에서만 남아 있습니다.
- 이 키들은 `action_data_index` 공개 JSON 계약에는 더 이상 허용되지 않습니다.

### 6.6 권장 예시

```json
{
  "mdfTemplateName": "TOOL_CONDITION_REPLY_MES",
  "fields": {
    "EQPID": "eqpId",
    "STATUS": {
      "from": "data",
      "path": "status",
      "transforms": ["trim", "upper"]
    },
    "ERRORCODE": {
      "from": "data",
      "path": "errorCode"
    },
    "EVENT_TYPE": {
      "from": "metadata",
      "path": "eventType"
    }
  }
}
```

### 6.7 금지 규약

아래 예전 계약은 저장 검증 기준에서 허용하지 않습니다.

- 루트: `mdf`, `messageName`, `message`
- 필드: `var`, `source`, `xform`, `fixed`, `required`
- 절대 경로: `data.status`, `metadata.eventType`

## 7. UI preview / 저장 검증 기준

- 모델 상세 preview는 새 계약을 한 줄 요약 문자열로 보여줍니다.
- `workflow_filter` preview는 전체 `and` / `or` 식을 축약합니다.
- `action_data_index` preview는 `mdfTemplateName`과 첫 번째 field spec을 보여줍니다.
- 저장 API는 새 계약 구조 검증을 수행하며, 잘못된 JSON은 400 응답으로 거절합니다.

예시 preview:

- `or(data.status {comparison=equals, expected="PASS", transforms=[trim, upper]}, metadata.eventType {comparison=equals, expected="EQP_CONDITION_CHECK"})`
- `mdfTemplateName=TOOL_CONDITION_REPLY_MES / EQPID {from=data, path=eqpId, transforms=[trim]}`

## 8. 운영 체크리스트

### 8.1 `workflow_filter` 등록 전

- 루트가 `and` 또는 `or`인지 확인
- 조건 노드에 `from`, `path`, `comparison`, `expected`가 모두 있는지 확인
- `from` 값이 `data` 또는 `metadata`인지 확인
- `path`가 상대 경로인지 확인
- 옛 키(`rows`, `left`, `op`, `MSG`, `CTX`)가 남아 있지 않은지 확인

### 8.2 `action_data_index` 등록 전

- `mdfTemplateName`이 있는지 확인
- `fields`가 JSON object인지 확인
- 필드 객체가 `from`, `path`, `transforms`만 사용하는지 확인
- `metadata` 값이 필요하면 `from=metadata`로 명시했는지 확인
- 절대 경로(`data.status`, `metadata.eventType`)를 쓰지 않았는지 확인

### 8.3 MDF 등록 전

- XML이 UTF-8이며 파싱 가능한지 확인
- 메시지명 중복이 없는지 확인
- target이 명시형 속성 또는 `_EQP` / `_MES` suffix로 식별 가능한지 확인
- 템플릿 placeholder와 `<field>` 정의가 대응되는지 확인

## 9. 관련 문서

- [설계 문서](../design/01-workflow-filter-and-action-data-index-redesign.md)
- [구현 계획](../tasks/01-workflow-filter-and-action-data-index-build-plan.md)
- [루트 아키텍처: 워크플로우 매칭](../../../../docs/Architecture/business/03-workflow-matching.md)
- [루트 아키텍처: 워크플로우 액션 타입](../../../../docs/Architecture/business/04-workflow-action-types.md)

## 10. 문서-코드 정합성 기준

다음 구현을 기준으로 본 문서를 유지합니다.

- [BusinessWorkflowFilterEvaluator.java](../../../../libs/business/tc-business-core/src/main/java/com/nori/tc/business/core/workflow/internal/matching/BusinessWorkflowFilterEvaluator.java)
- [BusinessActionDataIndexHybridResolver.java](../../../../libs/business/tc-business-core/src/main/java/com/nori/tc/business/core/workflow/internal/support/BusinessActionDataIndexHybridResolver.java)
- [BusinessMdfMessageComposer.java](../../../../libs/business/tc-business-core/src/main/java/com/nori/tc/business/core/workflow/internal/support/BusinessMdfMessageComposer.java)
- [ModelDetailWorkflowJsonSupport.java](../../../../libs/ui/adapter/tc-ui-web-adapter/src/main/java/com/nori/tc/ui/adapters/web/controller/support/ModelDetailWorkflowJsonSupport.java)

문서와 구현이 어긋나면 구현 기준으로 즉시 갱신합니다.
