# 08. Model MDF 작성 가이드 (Model MDF Authoring Guide)

## 개요

이 문서는 현재 `nori-tc`와 `nori-tc-ui` 구현이 실제로 인식하는 **Model MDF(XML) 작성 규칙**을 정리합니다.

목표는 업로드한 MDF가 아래 경로를 안정적으로 타도록 만드는 것입니다.

1. UI에서 MDF message dropdown/preview에 정상 노출
2. Business Core 모델 런타임 캐시에 정상 파싱
3. workflow의 `action_data_index.mdfTemplateName`으로 정확히 선택
4. `PUBLISH_EQP_COMMAND` 또는 `PUBLISH_MES_COMMAND` 액션에서 최종 메시지로 조립

---

## 결론 먼저

- 한 `modelVersionKey`에는 MDF가 **DB 기준 1건만** 저장됩니다. 메시지가 여러 개 필요하면 **XML 파일 1개 안에 여러 `<message>`** 를 넣어야 합니다.
- 루트는 `<mdf>`를 사용하고, 실제 메시지는 **루트 바로 아래**에 `<message>` 형태로 두는 방식을 권장합니다.
- 메시지 식별자는 `<message name="...">`의 `name`이며, workflow의 `action_data_index.mdfTemplateName`과 **정확히 동일해야** 합니다.
- EQP 전송용 메시지는 `target="EQP"`를 명시하고, workflow의 `action_name`은 별도로 `PUBLISH_EQP_COMMAND`여야 합니다.
- 템플릿 본문은 `<template>`에 작성하고, 치환 위치는 `{EQPID}`, `{CARID}`, `{LOTID}`처럼 작성합니다.
- `action_data_index`는 `from=data|metadata`만 지원하므로, `eqpId`처럼 context 값이 필요하면 **MDF `<field source="CTX">`** 로 작성하는 것이 정석입니다.

---

## 현재 구현이 MDF를 타는 흐름

```text
Model UI / Upload API
    │
    ├── UTF-8 + well-formed XML 검증 후 tc_model_mdf 저장
    │
BusinessModelRuntimeAssembler
    │
    ├── modelVersionKey 기준 MDF 1건 조회
    └── BusinessMdfRuntimeParser.parse(...)
            │
Workflow 실행
    │
    ├── workflow.action_name = PUBLISH_EQP_COMMAND / PUBLISH_MES_COMMAND
    ├── action_data_index.mdfTemplateName 으로 MDF message 선택
    └── BusinessMdfMessageComposer 가 field 값을 치환해 최종 메시지 생성
```

핵심은 **MDF XML만 맞아도 충분하지 않고**, workflow의 `action_name`과 `action_data_index`도 현재 계약에 맞아야 실제 실행 경로를 탑니다.

---

## 권장 XML 형식

현재 구현 기준으로 가장 안전한 형식은 아래와 같습니다.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<mdf>
  <message
      name="TOOL_CONDITION_REQUEST_EQP"
      target="EQP"
      action="PUBLISH_EQP_COMMAND"
      output="RAW_MESSAGE">
    <template>CMD=TOOL_CONDITION_REQUEST EQPID={EQPID} CARID={CARID} LOTID={LOTID}</template>

    <field name="EQPID" var="eqpId" source="CTX" required="true" />
    <field name="CARID" var="data.carId" source="MSG" required="true">
      <xform>trim</xform>
    </field>
    <field name="LOTID" var="data.lotId" source="MSG" required="true">
      <xform>trim</xform>
    </field>
  </message>
</mdf>
```

위 예시는 EQP로 아래 문자열을 보내기 위한 권장 작성 예시입니다.

```text
CMD=TOOL_CONDITION_REQUEST EQPID={EQPID} CARID={CARID} LOTID={LOTID}
```

### 왜 이 형식을 권장하는가?

| 항목 | 권장 이유 |
|------|-----------|
| `<mdf>` 루트 | UI가 wrapper로 인식하기 쉽고, 문서/구현 관례와 맞습니다. |
| 루트 직계 `<message>` | 현재 파서는 **루트 바로 아래 element**를 메시지로 읽습니다. 중간 wrapper를 넣으면 의도와 다르게 해석될 수 있습니다. |
| `name` 명시 | UI dropdown과 `mdfTemplateName` 매핑의 기준입니다. |
| `target="EQP"` 명시 | 이름 suffix 추론에 의존하지 않고 의도를 분명히 합니다. |
| `action="PUBLISH_EQP_COMMAND"` 명시 | 현재 workflow `action_name`과 같은 의도를 문서적으로 맞춰 두는 용도입니다. |
| `<template>` 사용 | inline text fallback보다 안전하고 가독성이 좋습니다. |
| `<field>` 명시 | `CTX`, `fixed`, `required`, `xform`까지 제어할 수 있습니다. |

주의:

- 현재 Core 실행 경로에서 실제 액션 디스패치는 MDF의 `action` 속성이 아니라 **workflow의 `action_name`** 으로 결정됩니다.
- `output` 속성은 파서가 읽기는 하지만, 현재 EQP/MES publish 경로는 사실상 **target 기반 실행기**가 동작을 결정합니다.
- 따라서 `action`, `output`은 명시해 두는 편이 좋지만, **실제 연결의 핵심은 `workflow.action_name` + `action_data_index.mdfTemplateName`** 입니다.

---

## EQP 전송용 workflow 연결 예시

MDF XML만 올려도 자동으로 실행되지는 않습니다.
실제 실행을 위해서는 workflow row도 아래처럼 연결되어야 합니다.

### 1. 최소 연결 예시

`action_name`

```text
PUBLISH_EQP_COMMAND
```

`action_data_index`

```json
{
  "mdfTemplateName": "TOOL_CONDITION_REQUEST_EQP",
  "fields": {}
}
```

이 경우 실제 필드 값은 MDF XML의 `<field>` fallback 정의를 사용합니다.

### 2. payload 값 일부를 workflow에서 override하는 예시

```json
{
  "mdfTemplateName": "TOOL_CONDITION_REQUEST_EQP",
  "fields": {
    "CARID": {
      "from": "data",
      "path": "carId",
      "transforms": ["trim"]
    },
    "LOTID": {
      "from": "data",
      "path": "lotId",
      "transforms": ["trim"]
    }
  }
}
```

이 경우 우선순위는 아래와 같습니다.

1. `action_data_index.fields[field]`
2. MDF XML `<field>`
3. 둘 다 없으면 빈 문자열

---

## MDF `<field>` 와 `action_data_index.fields` 차이

둘은 비슷해 보이지만 **지원 범위가 다릅니다.**

| 구분 | MDF `<field>` | `action_data_index.fields` |
|------|---------------|-----------------------------|
| 목적 | 템플릿의 기본/fallback 매핑 | workflow별 override |
| 값 소스 | `MSG`, `CTX`, `AUTO` | `data`, `metadata` |
| path 예시 | `data.carId`, `metadata.eventType`, `eqpId` | `carId`, `eventType` |
| 고정값 | `fixed` 지원 | 미지원 |
| 필수 여부 | `required` 지원 | 미지원 |
| 변환 | `xform`, `<xform>` 지원 | `transforms` 지원 |

### 중요한 차이

- MDF `<field source="CTX" var="eqpId">`는 `contextVariables.eqpId`를 읽습니다.
- `action_data_index.fields`는 `from=data|metadata`만 지원하므로 `eqpId` 같은 context 값을 직접 읽을 수 없습니다.
- 따라서 EQP 전송 메시지에서 `EQPID`를 장비 컨텍스트에서 채우려면, **`action_data_index`가 아니라 MDF `<field>`에 두는 것이 맞습니다.**

---

## 축약 포맷도 가능하지만 비권장

현재 파서는 아래 같은 축약 포맷도 읽을 수 있습니다.

```xml
<mdf>
  <TOOL_CONDITION_REQUEST_EQP>CMD=TOOL_CONDITION_REQUEST EQPID={EQPID} CARID={CARID} LOTID={LOTID}</TOOL_CONDITION_REQUEST_EQP>
</mdf>
```

다만 운영용/정식 작성 방식으로는 권장하지 않습니다.

이유는 다음과 같습니다.

- `name`, `target`, `action`, `output`를 명시적으로 드러내기 어렵습니다.
- `CTX`, `fixed`, `required`, `xform` 같은 필드 정책을 세밀하게 줄 수 없습니다.
- placeholder만 보고 자동 생성되는 field는 기본적으로 `AUTO + required=false`라서, 값이 없으면 조용히 빈 문자열로 바뀔 수 있습니다.
- UI에서도 `<message name="...">` 형식이 dropdown/preview 기준으로 더 명확합니다.

즉, **빠른 테스트용은 가능하지만 정석 구현은 명시적 `<message>` 형식**입니다.

---

## 작성 시 주의사항

### 1. 한 모델 버전에는 MDF가 1개만 저장됩니다

- 여러 MDF 파일을 따로 두는 방식이 아니라,
- **하나의 XML 안에 여러 `<message>`를 넣는 방식**으로 작성해야 합니다.

예시:

```xml
<mdf>
  <message name="TOOL_CONDITION_REQUEST_EQP" target="EQP">
    <template>CMD=TOOL_CONDITION_REQUEST EQPID={EQPID}</template>
  </message>
  <message name="TOOL_CONDITION_REPLY_MES" target="MES">
    <template>EQPID={EQPID} STATUS={STATUS}</template>
  </message>
</mdf>
```

### 2. 루트 바로 아래에 메시지를 둬야 합니다

아래처럼 중간 wrapper를 넣는 것은 비권장입니다.

```xml
<mdf>
  <messages>
    <message name="TOOL_CONDITION_REQUEST_EQP" target="EQP">
      <template>...</template>
    </message>
  </messages>
</mdf>
```

현재 파서는 루트 직계 element를 메시지로 읽기 때문에, 이런 구조는 의도대로 해석되지 않을 수 있습니다.

### 3. `action_data_index`는 구계약을 쓰면 안 됩니다

허용되는 루트 키는 아래 두 개뿐입니다.

```json
{
  "mdfTemplateName": "...",
  "fields": {}
}
```

아래 같은 예전 키는 현재 공개 계약에서 허용되지 않습니다.

- `messageName`
- `mdf`
- `var`
- `source`
- `xform`
- `fixed`
- `required`

### 4. `action_data_index` path는 절대 경로를 쓰면 안 됩니다

아래는 잘못된 예시입니다.

```json
{
  "mdfTemplateName": "TOOL_CONDITION_REQUEST_EQP",
  "fields": {
    "CARID": {
      "from": "data",
      "path": "data.carId"
    }
  }
}
```

올바른 예시는 아래입니다.

```json
{
  "mdfTemplateName": "TOOL_CONDITION_REQUEST_EQP",
  "fields": {
    "CARID": {
      "from": "data",
      "path": "carId"
    }
  }
}
```

### 5. 업로드 성공과 런타임 성공은 동일하지 않습니다

UI 업로드 단계에서는 주로 아래만 검증합니다.

- UTF-8 인코딩
- XML well-formed 여부

하지만 Business Core 런타임에서는 추가로 아래 조건이 맞아야 합니다.

- 메시지 정의가 1개 이상 존재
- message 이름이 중복되지 않음
- 축약 포맷이면 이름에서 `_EQP` / `_MES` target 추론 가능
- workflow의 `mdfTemplateName`과 정확히 일치하는 message가 존재

따라서 운영용 MDF는 반드시 이 문서 기준으로 작성하는 것이 안전합니다.

### 6. DOCTYPE / 외부 엔티티는 사용하지 않습니다

보안 설정상 XXE 방지를 위해 외부 엔티티/DTD 사용을 차단합니다.

---

## 실무 권장안

EQP로 아래 메시지를 보내려는 목적이라면:

```text
CMD=TOOL_CONDITION_REQUEST EQPID={EQPID} CARID={CARID} LOTID={LOTID}
```

권장 구현은 아래 조합입니다.

1. MDF XML은 명시적 `<message>` 형식으로 작성
2. `name="TOOL_CONDITION_REQUEST_EQP"`
3. `target="EQP"`
4. workflow `action_name`은 `PUBLISH_EQP_COMMAND`
5. `action_data_index.mdfTemplateName`은 `TOOL_CONDITION_REQUEST_EQP`
6. `EQPID`는 MDF `<field source="CTX" var="eqpId">`
7. `CARID`, `LOTID`는 MDF `<field source="MSG" var="data.xxx">` 또는 workflow `action_data_index.fields` override로 처리

즉, **메시지 선택은 workflow가 하고, 실제 값 채움은 MDF `<field>`와 `action_data_index.fields`가 함께 담당한다**고 이해하면 됩니다.

---

## 관련 구현 위치

| 위치 | 역할 |
|------|------|
| `libs/ui/adapter/tc-ui-web-adapter/src/main/java/com/nori/tc/ui/adapters/web/controller/ModelController.java` | MDF 업로드 API |
| `libs/ui/adapter/tc-ui-db-adapter/src/main/java/com/nori/tc/ui/adapter/db/JpaModelDetailCommandPort.java` | UTF-8/XML 형식 검증 후 MDF 저장 |
| `libs/business/adapter/tc-business-db-adapter/src/main/java/com/nori/tc/business/adapters/db/modelcache/BusinessModelRuntimeAssembler.java` | model version 기준 MDF 1건 조회 후 런타임 조립 |
| `libs/business/adapter/tc-business-db-adapter/src/main/java/com/nori/tc/business/adapters/db/modelcache/BusinessMdfRuntimeParser.java` | MDF XML 파싱 |
| `libs/business/tc-business-core/src/main/java/com/nori/tc/business/core/workflow/internal/support/BusinessMdfMessageComposer.java` | 템플릿 치환 |
| `libs/business/tc-business-core/src/main/java/com/nori/tc/business/core/workflow/internal/support/BusinessActionDataIndexHybridResolver.java` | `action_data_index` 계약 해석 |
| `nori-tc-ui/src/features/model/lib/mdf-message-parser.ts` | UI dropdown용 message 이름 추출 |

---

## 관련 문서

- [Business: 워크플로우 액션 타입](04-workflow-action-types.md)
- [Business: 모델 런타임 캐시](05-model-runtime-cache.md)
- [tc-business-core-app 운영 표준](../../../apps/tc-business-core-app/docs/Architecture/01-mdf-action-data-index-standard.md)
