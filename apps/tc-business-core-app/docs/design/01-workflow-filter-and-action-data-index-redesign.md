> 작성일: 2026-03-13

# 01. Workflow Filter / Action Data Index 재설계

## 1. 개요

### 1.1 배경

현재 `workflow_filter`와 `action_data_index`는 기능 자체보다 JSON 키 이름을 먼저 이해해야 하는 구조입니다.
특히 아래 문제가 누적되어 초심자와 운영 담당자 모두가 규칙을 유추하기 어렵습니다.

- `workflow_filter`는 `rows`, `left`, `op`, `right` 같은 일반적인 비즈니스 문서에서 직관적이지 않은 키를 사용합니다.
- `workflow_filter`는 flat AND 평가만 지원하므로, 실제 현장에서 필요한 복합 분기식(`AND`/`OR` 중첩)을 표현할 수 없습니다.
- `MSG`, `CTX`, `AUTO`, `source`, `xform` 같은 용어는 코드 구현을 아는 사람에게만 의미가 전달되고, 실제 Kafka payload 관점에서는 무엇을 읽는지 즉시 이해하기 어렵습니다.
- `action_data_index`는 값 조회 규칙과 MDF 템플릿 선택 규칙이 함께 들어 있어, `messageName`이 MDF 템플릿 선택자라는 사실을 문서만 보고는 파악하기 어렵습니다.
- 현재 `action_data_index`는 `var/source/xform/fixed/required` 중심으로 설계되어 있어, 실제 의도인 "어디서 어떤 값을 가져올지"보다 내부 구현 개념이 전면에 드러납니다.

본 재설계는 위 문제를 해결하기 위해 외부 계약을 Kafka payload envelope 기준으로 다시 정리하고, 실제 코드 반영 범위와 문서 반영 범위를 함께 확정하는 데 목적이 있습니다.

### 1.2 목표

본 문서는 `workflow_filter`와 `action_data_index`의 새 표준 계약을 정의하고, 후속 구현 시 지켜야 할 정책을 확정합니다.

- `workflow_filter`를 `and` / `or` 기반 중첩 JSON 트리로 재설계합니다.
- `workflow_filter`와 `action_data_index` 모두 Kafka payload의 `data`, `metadata` 블록 기준으로 값을 조회하도록 용어를 통일합니다.
- `path`는 선택한 블록 기준 상대 경로만 허용하도록 규칙을 단순화합니다.
- `action_data_index`를 비교 없는 값 조회 전용 구조로 재정의합니다.
- MDF 템플릿 선택은 자동 선택을 제거하고 `mdfTemplateName` 명시 선택으로 고정합니다.
- 실제 구현 시 수정되어야 할 코드, 테스트, 문서 범위를 한 번에 식별할 수 있도록 반영 대상을 명확히 정리합니다.

### 1.3 참조 문서

- 기준 아키텍처: `docs/Architecture/business/03-workflow-matching.md`
- 액션 실행 기준 문서: `docs/Architecture/business/04-workflow-action-types.md`
- 현행 app 표준 문서: `apps/tc-business-core-app/docs/Architecture/01-mdf-action-data-index-standard.md`
- app 문서 진입점: `apps/tc-business-core-app/docs/README.md`

---

## 2. 범위와 전제

### 2.1 범위

- `workflow_filter` 외부 JSON 계약 재정의
- `action_data_index` 외부 JSON 계약 재정의
- MDF 템플릿 선택 정책 재정의
- Business runtime / UI preview / 모델 저장 검증 경로에 대한 반영 범위 정의
- `nori-tc-ui` 모델 상세 structured editor / local preview / 저장 오류 노출 반영 범위 정의
- `tc-business-core-app` 및 루트 `docs` 기준 문서 재작성 범위 정의

### 2.2 비범위

- 본 문서는 설계 문서이며 실제 코드 구현 결과를 포함하지 않습니다.
- Kafka envelope 포맷 자체를 변경하지 않습니다.
- DB 신규 컬럼, 신규 테이블, 신규 스키마 버전 도입은 포함하지 않습니다.
- `workflow_filter`와 `action_data_index` 외의 workflow/action 도메인 구조 개편은 포함하지 않습니다.
- UI 화면 전체 레이아웃 개편은 포함하지 않으며, 모델 상세의 계약 정합성 확보를 위한 editor / preview / 저장 오류 노출만 포함합니다.

### 2.3 확정 전제

- `tc_model_workflow.workflow_filter`와 `action_data_index` 기존 컬럼을 그대로 사용합니다.
- `workflow_filter`는 최대 4000자면 충분하다고 가정하며 DB 스키마는 변경하지 않습니다.
- Kafka payload는 최상위 `metadata`, `data` 블록을 가지는 envelope 구조를 따릅니다.
- `workflow_filter`와 `action_data_index`는 Kafka payload 내부 `data`, `metadata`만 읽을 수 있습니다.
- Kafka payload 밖의 런타임 메타값(`topic`, `partition`, `offset`, `payloadRef`, 내부 `record.messageName` 등)은 접근 불가입니다.
- `from` 값은 `data`, `metadata`만 허용하며 `all`은 사용하지 않습니다.
- `path`는 선택 블록 기준 상대 경로만 허용합니다.
- `action_data_index`는 값 조회만 담당하며, MDF 템플릿 선택은 `mdfTemplateName`으로 명시합니다.
- MDF 템플릿 자동 선택(`actionName + target` fallback)은 제거합니다.
- 과거 계약(`rows/left/op/right`, `MSG/CTX/AUTO`, `var/source/xform`, `messageName`)은 새 계약 설명 섹션에서만 이전 규약으로 제한적으로 언급합니다.

---

## 3. Kafka payload envelope 기준

새 계약의 모든 조회 규칙은 Kafka payload의 최상위 envelope 구조를 기준으로 합니다.

표준 예시는 아래와 같습니다.

```json
{
  "metadata": {
    "eventType": "EQP_UPDATE_JARFILE_REP",
    "timestamp": "2026-01-21T02:11:24.545202300Z",
    "source": "TC-COMM-GATEWAY-APP",
    "traceId": "0000000000001"
  },
  "data": {
    "eqpId": "PHOTO_002",
    "interfaceType": "SOCKET",
    "STATUS": "PASS",
    "ERRORMSG": null,
    "ERRORCODE": null
  }
}
```

다른 예시 payload도 동일한 envelope 규칙을 따릅니다.

```json
{
  "metadata": {
    "eventType": "EQP_CONDITION_CHECK",
    "timestamp": "2026-01-21T02:11:24.521212200Z",
    "source": "WSC-CONSUMER",
    "correlationId": "L-0121010"
  },
  "data": {
    "lotId": "L-0121010-1",
    "status": "PASS",
    "errorCode": null,
    "errorMessage": null,
    "eqpId": "PHOTO_002",
    "portId": "PHOTO_002_P2",
    "stockerId": null,
    "carId": "TESTCARID_3",
    "slotId": "50",
    "slotMap": "11111111111111111111"
  }
}
```

조회 규칙은 다음과 같이 고정합니다.

- `from: "data"`면 payload의 `data` 객체를 시작점으로 조회합니다.
- `from: "metadata"`면 payload의 `metadata` 객체를 시작점으로 조회합니다.
- `path`는 선택한 시작점 기준 상대 경로입니다.

허용 예시:

- `{"from":"data","path":"status"}`
- `{"from":"data","path":"eqpId"}`
- `{"from":"metadata","path":"eventType"}`
- `{"from":"metadata","path":"traceId"}`

금지 예시:

- `{"from":"data","path":"data.status"}`
- `{"from":"metadata","path":"metadata.eventType"}`
- `{"from":"data","path":"metadata.eventType"}`

즉, `from`이 어떤 블록을 읽을지 결정하고, `path`는 그 아래 세부 필드만 지정합니다.

---

## 4. `workflow_filter` 공개 계약 변경

### 4.1 기존 문제

현재 `workflow_filter`는 아래 특징을 가집니다.

- `rows` 배열 안에 단일 비교식을 나열하는 구조
- 각 조건이 사실상 `AND`만 지원
- `left`, `op`, `right` 중심 표현
- `MSG`, `CTX`, `AUTO`와 같은 구현 중심 용어 사용

이 구조는 운영 문서 관점에서 다음 한계를 가집니다.

- 키 이름만 봐서는 "무엇을 어디서 읽는지"가 드러나지 않습니다.
- `(A == "TEST" || B >= 4) && (C == "NO" || D <= 10)` 같은 실제 조건식을 표현할 수 없습니다.
- payload envelope 기준으로 보면 `MSG`, `CTX`보다 `data`, `metadata`가 훨씬 직접적입니다.

### 4.2 새 루트 구조

`workflow_filter`는 그룹 노드와 조건 노드만 허용하는 JSON 트리로 재설계합니다.

루트는 반드시 그룹 노드여야 합니다.

```json
{ "and": [ ... ] }
```

또는

```json
{ "or": [ ... ] }
```

### 4.3 그룹 노드 규칙

그룹 노드는 아래 두 가지 키만 허용합니다.

- `and`
- `or`

의미:

- `and`: 하위 노드가 모두 참이어야 전체가 참
- `or`: 하위 노드 중 하나라도 참이면 전체가 참

규칙:

- 그룹 노드는 `and` 또는 `or` 중 하나만 가져야 합니다.
- 그룹 배열은 비어 있으면 안 됩니다.
- 그룹 노드 안에는 그룹 노드와 조건 노드가 재귀적으로 섞여 들어갈 수 있습니다.

### 4.4 조건 노드 규칙

조건 노드는 아래 필드로 고정합니다.

- `from`
- `path`
- `comparison`
- `expected`
- `transforms` (선택)

예시:

```json
{
  "from": "data",
  "path": "status",
  "comparison": "equals",
  "expected": "PASS",
  "transforms": ["trim", "upper"]
}
```

각 필드 의미:

- `from`: `data` 또는 `metadata`
- `path`: 선택 블록 기준 상대 경로
- `comparison`: 비교 방식
- `expected`: 우변 고정값
- `transforms`: 비교 전에 순차 적용할 변환 체인

### 4.5 비교 연산

허용 비교 연산은 아래로 고정합니다.

- `equals`
- `not_equals`
- `greater_than`
- `greater_than_or_equal`
- `less_than`
- `less_than_or_equal`
- `contains`
- `in`

설계 원칙:

- 연산 이름은 축약형(`eq`, `gte`) 대신 full word를 사용합니다.
- `comparison`만 보고 의미를 유추할 수 있어야 합니다.
- 숫자 비교는 현재 구현과 동일하게 숫자 우선 정규화 정책을 유지합니다.

### 4.6 transform 규칙

기존 `xform`은 `transforms`로 이름을 변경합니다.

예시:

```json
{
  "from": "data",
  "path": "status",
  "comparison": "equals",
  "expected": "PASS",
  "transforms": ["trim", "upper"]
}
```

지원 transform 종류는 현행 구현과 동일한 범위를 유지합니다.
예:

- `trim`
- `lower`
- `upper`
- `split(...)`
- `substring(...)`
- `length`
- `toint`
- `tolong`
- `add(...)`
- `sub(...)`

### 4.7 허용 예시

```json
{
  "and": [
    {
      "or": [
        {
          "from": "data",
          "path": "A",
          "comparison": "equals",
          "expected": "TEST"
        },
        {
          "from": "data",
          "path": "B",
          "comparison": "greater_than_or_equal",
          "expected": 4
        }
      ]
    },
    {
      "or": [
        {
          "from": "data",
          "path": "C",
          "comparison": "equals",
          "expected": "NO"
        },
        {
          "from": "data",
          "path": "D",
          "comparison": "less_than_or_equal",
          "expected": 10
        }
      ]
    }
  ]
}
```

### 4.8 금지 규칙

아래 입력은 새 계약에서 금지합니다.

- `rows`, `conditions`
- `left`, `expr`
- `op`, `operator`
- `right`
- `MSG`, `CTX`, `AUTO`
- `path: "data.status"` 같은 절대 경로
- `from: "all"`
- 변수 대 변수 비교
- `expected`에 객체를 넣는 구조

---

## 5. `action_data_index` 공개 계약 변경

### 5.1 역할 재정의

새 `action_data_index`는 "어떤 MDF 템플릿을 사용할지"와 "그 템플릿 필드 값을 Kafka payload에서 어떻게 가져올지"만 정의합니다.

즉, `workflow_filter`와 구조적으로 유사하지만 차이점은 분명합니다.

- `workflow_filter`: 값을 읽고 비교해서 true/false를 결정
- `action_data_index`: 값을 읽고 변환해서 최종 문자열/필드 값을 결정

따라서 `action_data_index`에는 `comparison`, `expected`가 없습니다.

### 5.2 루트 구조

루트는 아래 형태로 고정합니다.

```json
{
  "mdfTemplateName": "TOOL_CONDITION_REPLY_MES",
  "fields": {
    "EQPID": {
      "from": "data",
      "path": "eqpId"
    },
    "EVENT_TYPE": {
      "from": "metadata",
      "path": "eventType"
    },
    "STATUS": {
      "from": "data",
      "path": "STATUS",
      "transforms": ["trim", "upper"]
    }
  }
}
```

루트 필드:

- `mdfTemplateName`
- `fields`

### 5.3 `mdfTemplateName`

`messageName`, `message`, `mdf` 같은 이전 키는 더 이상 외부 계약에서 사용하지 않습니다.
MDF 템플릿 선택자는 `mdfTemplateName` 하나로 고정합니다.

이름 변경 이유:

- 기존 `messageName`은 Kafka message name과 혼동됩니다.
- 실제 역할은 "어떤 MDF 템플릿을 쓸지"이므로 `mdfTemplateName`이 의미를 직접 드러냅니다.

### 5.4 `fields` 규칙

`fields`는 템플릿 필드명 → 값 조회 규칙 맵입니다.

지원 형태는 두 가지입니다.

문자열 shorthand:

```json
{
  "mdfTemplateName": "TOOL_CONDITION_REPLY_MES",
  "fields": {
    "EQPID": "eqpId"
  }
}
```

객체식:

```json
{
  "mdfTemplateName": "TOOL_CONDITION_REPLY_MES",
  "fields": {
    "EQPID": {
      "from": "data",
      "path": "eqpId"
    }
  }
}
```

shorthand 기본값:

- 문자열 shorthand는 `from: "data"`를 기본값으로 봅니다.

### 5.5 필드 객체 규칙

필드 객체는 아래 키만 허용합니다.

- `from`
- `path`
- `transforms`

금지 키:

- `comparison`
- `expected`
- `fixed`
- `required`
- `var`
- `source`
- `xform`

### 5.6 값 누락 정책

`action_data_index`는 값 조회 전용 구조이므로, 값이 없을 때는 실패보다 빈 문자열 대체를 우선합니다.

정책:

- 조회값이 없으면 `""`
- `transforms` 적용 중 실패하면 이전 값을 유지하고 warn 로그

즉, 필드 하나의 누락이 곧바로 전체 발행 실패를 만들지 않도록 기본 정책을 단순화합니다.

---

## 6. MDF 템플릿 선택 정책

### 6.1 명시 선택 원칙

MDF 템플릿 선택은 자동이 아니라 명시 선택으로 고정합니다.

원칙:

- `action_data_index`가 존재하면 `mdfTemplateName`이 있어야 합니다.
- `mdfTemplateName`으로 정확히 하나의 MDF 템플릿을 선택합니다.
- `actionName + target(EQP/MES)` 기반 자동 선택 fallback은 제거합니다.

### 6.2 선택 실패 정책

아래 경우는 실패로 처리합니다.

- `action_data_index`는 있는데 `mdfTemplateName`이 없음
- `mdfTemplateName`에 해당하는 MDF 템플릿이 없음
- 찾은 MDF 템플릿의 target이 현재 액션 target과 다름

### 6.3 raw message fallback 정책

`action_data_index`가 비어 있으면 MDF 조립을 시도하지 않고 기존 raw message fallback을 유지합니다.

즉:

- `action_data_index` 없음 → 기존 raw message 사용 가능
- `action_data_index` 있음 → `mdfTemplateName` 필수

---

## 7. 런타임/저장/UI 반영 범위

새 계약은 `tc-business-core-app` 문서만의 문제가 아니라 runtime, UI preview, 저장 검증까지 함께 반영되어야 합니다.

### 7.1 주요 코드 반영 대상

#### Business runtime

- `libs/business/tc-business-core/src/main/java/com/nori/tc/business/core/workflow/internal/matching/BusinessWorkflowFilterEvaluator.java`
- `libs/business/tc-business-core/src/main/java/com/nori/tc/business/core/workflow/internal/matching/BusinessWorkflowPayloadExtractor.java`
- `libs/business/tc-business-core/src/main/java/com/nori/tc/business/core/workflow/internal/support/BusinessActionDataIndexHybridResolver.java`
- `libs/business/tc-business-core/src/main/java/com/nori/tc/business/core/workflow/internal/support/BusinessMdfMessageComposer.java`

#### UI preview / 저장 검증

- `libs/ui/adapter/tc-ui-web-adapter/src/main/java/com/nori/tc/ui/adapters/web/controller/support/ModelDetailPreviewSupport.java`
- workflow/action_data_index 상세 저장 경로를 담당하는 UI backend controller / adapter 검증 경로

#### `nori-tc-ui` frontend

- `nori-tc-ui/src/features/model/lib/model-detail-editor.ts`
- `nori-tc-ui/src/features/model/components/ModelDetailPanel.tsx`
- `nori-tc-ui/src/features/model/components/ModelPage.tsx`

### 7.2 반영 방향

- `workflow_filter` 평가기는 flat row 순회에서 재귀 AST 평가 방식으로 전환해야 합니다.
- payload 조회는 Kafka envelope의 `data`, `metadata` 블록만 읽도록 고정해야 합니다.
- `action_data_index` 해석기는 `mdfTemplateName`, `fields`, `from`, `path`, `transforms`만 읽도록 단순화해야 합니다.
- MDF 조립기는 `mdfTemplateName` explicit 선택 정책만 허용해야 합니다.
- UI preview는 새 canonical 용어(`and`, `or`, `data`, `metadata`, `comparison`, `expected`, `transforms`, `mdfTemplateName`)를 사용해야 합니다.
- 모델 저장 경로는 새 JSON 계약 구조를 서버 단에서 검증해야 합니다.

### 7.3 `apps/tc-business-core-app` 반영 범위

`apps/tc-business-core-app` 자체는 runtime 구현보다 문서 역할이 중심입니다.
따라서 app 레벨에서는 아래 문서 반영이 핵심입니다.

- 새 design 문서 추가
- 새 tasks 문서 추가
- app Architecture 표준 문서 재작성
- app README 링크/설명 갱신

즉, 실제 구현은 `libs/business`, `libs/ui`, 루트 `docs`에서 주로 일어나고, app은 그 구현의 공식 문서 진입점 역할을 합니다.

### 7.4 `nori-tc-ui` 반영 범위

`nori-tc-ui`는 서버가 이미 새 canonical 계약을 기준으로 preview와 저장 검증을 수행하더라도,
모델 상세 structured editor가 예전 계약을 생성하면 실제 운영자가 UI를 통해 값을 수정하는 순간 정합성이 다시 깨질 수 있습니다.

따라서 `nori-tc-ui`에서는 아래 항목이 함께 반영되어야 합니다.

- `workflow_filter` structured editor는 flat row 기반 `rows/left/op/right` 편집기가 아니라, `and` / `or` 그룹과 조건 노드(`from`, `path`, `comparison`, `expected`, `transforms`)를 표현할 수 있어야 합니다.
- `workflow_filter` structured editor가 새 canonical JSON을 읽을 때 `and` / `or` 구조를 잃어버리거나 blank 상태로 열리면 안 됩니다.
- 새 canonical 구조를 UI가 아직 완전하게 구조화 편집하지 못하는 경우, 부분 파싱으로 잘못된 structured 값으로 바꾸는 대신 raw mode fallback으로 안전하게 열어야 합니다.
- `action_data_index` structured editor는 `messageName`, `mdf`, `var`, `source`, `xform`, `fixed`, `required` 중심 UI를 제거하고 `mdfTemplateName`, `fields`, `from`, `path`, `transforms` 기준으로 전환해야 합니다.
- `action_data_index.fields`의 문자열 shorthand는 `from=data`, `path=<value>` 의미로 구조화 편집기에 정상 로드되거나, 최소한 raw mode에서 의미 손실 없이 유지되어야 합니다.
- structured editor에서 값을 적용한 뒤 테이블 셀에 보이는 local preview fallback도 서버 preview와 같은 canonical 용어(`and`, `or`, `from`, `path`, `comparison`, `expected`, `transforms`, `mdfTemplateName`)를 사용해야 합니다.
- modal 안내 문구, placeholder, select option, 도움말에서 예전 용어(`MSG`, `CTX`, `AUTO`, `Var`, `Source`, `Xform`, `Operator`, `Right`, `MDF Message`)를 제거해야 합니다.
- 저장 API가 400과 원인 메시지를 반환할 때 사용자가 어떤 row의 `workflow_filter` 또는 `action_data_index`가 잘못되었는지 화면에서 다시 확인하고 수정할 수 있어야 합니다.

현재 코드 기준으로 특히 영향이 큰 지점은 아래와 같습니다.

- `model-detail-editor.ts`의 parse / build / summarize 로직은 예전 계약(`rows`, `var`, `source`, `xform`, `mdf`, `messageName`)을 기준으로 작성되어 있어 새 canonical JSON을 lossless 하게 다루지 못합니다.
- `ModelDetailPanel.tsx`의 structured modal은 `Var / Source / Xform / Operator / Right`, `MDF Message / Fixed / Required` UI를 사용하고 있어 새 계약과 직접 대응되지 않습니다.
- `ModelPage.tsx`는 로컬 편집 후 `previewValues`를 비우고 클라이언트 summarize fallback에 의존하므로, fallback 요약 로직도 새 계약 기준으로 맞아야 합니다.

---

## 8. 문서 반영 범위

### 8.1 app 문서

아래 문서는 새 계약에 맞춰 직접 수정되거나 신규 작성되어야 합니다.

- `apps/tc-business-core-app/docs/design/01-workflow-filter-and-action-data-index-redesign.md`
- `apps/tc-business-core-app/docs/tasks/01-workflow-filter-and-action-data-index-build-plan.md`
- `apps/tc-business-core-app/docs/Architecture/01-mdf-action-data-index-standard.md`
- `apps/tc-business-core-app/docs/README.md`

### 8.2 루트 문서

아래 문서는 새 계약을 source of truth 기준으로 동기화해야 합니다.

- `docs/Architecture/business/03-workflow-matching.md`
- `docs/Architecture/business/04-workflow-action-types.md`

### 8.3 문서 적용 원칙

- 새 canonical 용어만 표준 문서 본문에 사용합니다.
- 예전 용어는 "이전 계약" 설명 섹션에서만 제한적으로 언급합니다.
- 문서와 구현이 다르면 구현 기준으로 문서를 갱신하는 기존 원칙을 유지합니다.

---

## 9. 실패 정책 및 검증 정책

### 9.1 구조 검증 실패

아래 경우는 구조 검증 실패로 처리해야 합니다.

- `workflow_filter` 루트가 그룹 노드가 아님
- 그룹 노드가 `and`, `or`를 동시에 가짐
- 그룹 배열이 비어 있음
- 조건 노드에서 `from`, `path`, `comparison`, `expected` 중 필수 키 누락
- `from` 값이 `data`, `metadata`가 아님
- `path`가 비어 있음
- `path`가 `data.` 또는 `metadata.` 접두어를 가진 절대 경로 표현
- `action_data_index`에 `mdfTemplateName` 또는 `fields`가 없음
- `action_data_index.fields` 하위에 금지 키가 들어 있음

### 9.2 런타임 평가 실패

- `workflow_filter` JSON 파싱 실패
- `workflow_filter` 구조 검증 실패
- `action_data_index` JSON 파싱 실패
- `mdfTemplateName`으로 MDF 템플릿을 찾지 못함
- MDF target mismatch

### 9.3 누락/변환 정책

`workflow_filter`:

- 조회값이 없을 때는 비교 결과가 false가 되도록 평가합니다.
- 구조 오류와 값 누락을 구분합니다.

`action_data_index`:

- 조회값이 없으면 빈 문자열 `""`
- transform 실패 시 이전 값 유지

### 9.4 로그 정책

- `debug`: 선택된 비교식/템플릿/필드 수 요약
- `warn`: transform 실패, `action_data_index` 값 누락 대체
- `error`: JSON 파싱 실패, MDF 템플릿 선택 실패, 구조 검증 실패

### 9.5 UI 편집 안전성 정책

- `nori-tc-ui`는 유효한 새 canonical JSON을 열었을 때 의미를 잃은 blank structured form으로 바꾸면 안 됩니다.
- structured editor가 현재 지원하지 못하는 입력은 부분 변환하지 않고 raw mode로 유지해야 합니다.
- UI가 값을 다시 serialize 할 때 예전 계약 키(`rows`, `left`, `op`, `right`, `messageName`, `mdf`, `var`, `source`, `xform`, `fixed`, `required`)를 재생성하면 안 됩니다.
- 저장 전 미리보기와 저장 후 서버 preview가 서로 다른 용어 체계를 사용하지 않도록 canonical 요약 규칙을 공유해야 합니다.

---

## 10. 기대 효과와 주의사항

### 10.1 기대 효과

- 문서만 봐도 `workflow_filter`와 `action_data_index` 구조를 이해할 수 있습니다.
- Kafka payload envelope 기준으로 `data`, `metadata`를 직접 지정하므로 데이터 위치 판단이 쉬워집니다.
- `workflow_filter`가 실제 현장 조건식 수준의 `AND`/`OR` 중첩을 표현할 수 있습니다.
- `action_data_index`는 비교 없는 값 조회 전용으로 단순화되어 역할이 명확해집니다.
- `mdfTemplateName` 명시 선택으로 MDF 템플릿 선택 의미가 분명해집니다.

### 10.2 주의사항

- 새 계약은 기존 계약과 하위 호환되지 않습니다.
- `path` 절대 경로를 허용하지 않으므로 운영자가 익숙한 `data.status` 표기 관성을 문서와 검증으로 교정해야 합니다.
- `action_data_index`는 값 조회 전용이므로, 기존 `fixed`/`required` 개념을 기대하는 운영 패턴은 새 표준에 맞게 재정의해야 합니다.
- `apps/tc-business-core-app` 문서만 업데이트하면 끝나는 작업이 아니며, 실제 구현과 루트 문서가 함께 반영되어야 정합성이 맞습니다.
- `nori-tc-ui` structured editor가 예전 계약을 계속 생성하면, 서버 구현이 완료되어 있어도 UI에서 열기/수정/재저장하는 과정에서 유효한 `workflow_filter` / `action_data_index`가 손실되거나 400 검증 오류가 발생할 수 있습니다.
