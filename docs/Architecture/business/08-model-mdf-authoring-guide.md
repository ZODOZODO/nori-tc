# 08. Model MDF 작성 가이드 (Model MDF Authoring Guide)

## 개요

이 문서는 **Model MDF(XML) 작성 규칙**을 정리합니다.

MDF의 역할은 **메시지 구조 선언**입니다.
MDF는 어떤 workflow에서 사용될지 알지 못하며, 알 필요도 없습니다.
값 바인딩은 workflow의 `action_data_index`가 담당합니다.

업로드한 MDF가 아래 경로를 정상적으로 타도록 작성하는 것이 목표입니다.

1. UI에서 MDF message dropdown/preview에 정상 노출
2. Business Core 모델 런타임 캐시에 정상 파싱
3. workflow의 `action_data_index.mdfTemplateName`으로 정확히 선택
4. EQP/MES에 맞는 포맷으로 최종 메시지 조립

---

## 핵심 원칙

| 원칙 | 설명 |
|------|------|
| **MDF = 선언체** | MDF는 메시지 구조와 field 목록만 선언합니다. 값을 어디서 가져올지는 알지 못합니다. |
| **값 바인딩은 workflow 담당** | workflow의 `action_data_index.fields`가 field별 값을 제공합니다. |
| **field var = lookup key** | `field`의 `var` 속성은 `action_data_index.fields`에서 값을 찾아올 key 이름입니다. |
| **output = 직렬화 포맷** | `RAW_MESSAGE`는 template 문자열 직렬화, `KAFKA`는 Kafka 메시지 포맷 직렬화입니다. |

---

## 권장 MDF 형식

```xml
<?xml version="1.0" encoding="UTF-8"?>
<mdf>
  <message name="TOOL_CONDITION_REQUEST" target="EQP" output="RAW_MESSAGE">
    <template>CMD=TOOL_CONDITION_REQUEST EQPID={EQPID} CARID={CARID} LOTID={LOTID}</template>
    <field name="EQPID" var="EQPID" required="true" />
    <field name="CARID" var="CARID" required="false" />
    <field name="LOTID" var="LOTID" required="false" />
  </message>
  <message name="TOOL_CONDITION_REPLY" target="MES" output="KAFKA">
    <field name="EQPID" var="EQPID" required="true" />
    <field name="STATUS" var="STATUS" required="true" />
    <field name="CARID" var="CARID" required="false" />
    <field name="LOTID" var="LOTID" required="false" />
  </message>
</mdf>
```

---

## 속성 설명

### `<message>` 속성

| 속성 | 필수 | 설명 |
|------|------|------|
| `name` | 필수 | 메시지 식별자. workflow의 `mdfTemplateName`과 정확히 일치해야 합니다. |
| `target` | 필수 | 전송 대상. `EQP` 또는 `MES` |
| `output` | 필수 | 직렬화 포맷. `RAW_MESSAGE` 또는 `KAFKA` |

### `output` 포맷 설명

**`output="RAW_MESSAGE"`** — template 문자열 직렬화

- `<template>` 안의 `{EQPID}` 자리에 값을 치환해 문자열로 만듭니다.
- EQP 장비 전송용 텍스트 명령 포맷에 사용합니다.
- `<template>` 요소가 필수입니다.

```text
결과 예시: CMD=TOOL_CONDITION_REQUEST EQPID=TESTEQP01 CARID=T_CARID01 LOTID=T_LOTID01
```

**`output="KAFKA"`** — Kafka 메시지 포맷 직렬화

- `<template>` 없이 field 목록을 Kafka `data` 블록으로 직렬화합니다.
- `metadata` 블록(eventType, timestamp, source, correlationId 등)은 시스템이 자동으로 채웁니다.
- MES 전송용 이벤트 메시지에 사용합니다.

```json
결과 예시:
{
  "metadata": {
    "eventType": "TOOL_CONDITION_REPLY",
    "timestamp": "2026-01-21T02:11:24.545Z",
    "source": "TC-COMM-BUSINESS-APP",
    "correlationId": "L-0121010"
  },
  "data": {
    "EQPID": "TESTEQP01",
    "STATUS": "OK",
    "CARID": "T_CARID01",
    "LOTID": "T_LOTID01"
  }
}
```

### `<field>` 속성

| 속성 | 필수 | 설명 |
|------|------|------|
| `name` | 필수 | template `{EQPID}` 자리의 치환 위치 식별자. `=` 기준 왼쪽 값. |
| `var` | 권장 | `action_data_index.fields`에서 값을 찾아올 key 이름. `=` 기준 오른쪽 값. 생략하면 `name`과 동일하게 처리됩니다. |
| `required` | 선택 | `true`이면 `action_data_index.fields`에 해당 var key가 없을 때 에러. 기본값 `true` |

#### `name`과 `var`의 관계

`EQPID={EQPID}` 형태에서:

- `name="EQPID"` → template의 `{EQPID}` 자리를 식별하는 이름
- `var="EQPID"` → `action_data_index.fields["EQPID"]`로 값을 조회하는 key

두 값이 다를 수 있습니다:

```xml
<!-- action_data_index.fields["equipmentId"] 값을 {EQPID} 자리에 채웁니다 -->
<field name="EQPID" var="equipmentId" required="true" />
```

---

## workflow와의 연동

MDF 자체는 선언만 합니다. 실제 값은 workflow의 `action_data_index`가 제공합니다.

### action_data_index 형식

```json
{
  "mdfTemplateName": "TOOL_CONDITION_REQUEST",
  "fields": {
    "EQPID": { "from": "data", "path": "eqpId" },
    "CARID": { "from": "data", "path": "carId" },
    "LOTID": { "from": "data", "path": "lotId" }
  }
}
```

- `mdfTemplateName`: 사용할 MDF message의 `name`
- `fields` key: MDF field의 `var` 이름과 일치해야 합니다
- `from`: `data` 또는 `metadata` (Kafka 메시지의 블록 이름)
- `path`: 해당 블록 내 field 경로 (절대 경로 불가, 상대 경로만 허용)

### 흐름 요약

```text
MDF XML 선언 (name, var, required, target, output, template)
    ↓
workflow action_data_index.fields[var] → 값 제공
    ↓
BusinessMdfMessageComposer
    → output=RAW_MESSAGE: template 치환 → 문자열
    → output=KAFKA: field values → Kafka data 블록
```

---

## 한 모델 버전에 메시지 여러 개 넣기

한 `modelVersionKey`에는 MDF가 DB 기준 1건만 저장됩니다.
메시지가 여러 개 필요하면 **XML 파일 1개 안에 여러 `<message>`** 를 넣습니다.

```xml
<mdf>
  <message name="TOOL_CONDITION_REQUEST" target="EQP" output="RAW_MESSAGE">
    <template>CMD=TOOL_CONDITION_REQUEST EQPID={EQPID}</template>
    <field name="EQPID" var="EQPID" required="true" />
  </message>
  <message name="TOOL_CONDITION_REPLY" target="MES" output="KAFKA">
    <field name="EQPID" var="EQPID" required="true" />
    <field name="STATUS" var="STATUS" required="true" />
  </message>
</mdf>
```

---

## 축약 포맷 (비권장)

이름 suffix(`_EQP`, `_MES`)로 target을 추론하는 방식도 지원하지만, 비권장합니다.

```xml
<mdf>
  <TOOL_CONDITION_REQUEST_EQP>CMD=REQ EQPID={EQPID}</TOOL_CONDITION_REQUEST_EQP>
</mdf>
```

비권장 이유:
- `target`, `output` 속성을 명시할 수 없습니다.
- 항상 `RAW_MESSAGE` output으로만 동작합니다.
- field 선언 없이 placeholder만 자동 보강되며, 이 경우 `required=false`가 기본값이라 값이 없어도 에러가 나지 않습니다.

---

## 작성 시 주의사항

### 1. 루트 바로 아래에 메시지를 두어야 합니다

중간 wrapper를 넣으면 파서가 의도대로 해석하지 않을 수 있습니다.

```xml
<!-- 비권장: 중간 wrapper 사용 -->
<mdf>
  <messages>
    <message name="TOOL_CONDITION_REQUEST" target="EQP" output="RAW_MESSAGE">
      <template>...</template>
    </message>
  </messages>
</mdf>
```

### 2. `action_data_index` path는 상대 경로만 허용됩니다

```json
// 잘못된 예시 (절대 경로)
{ "from": "data", "path": "data.carId" }

// 올바른 예시 (상대 경로)
{ "from": "data", "path": "carId" }
```

### 3. 업로드 성공과 런타임 성공은 다릅니다

UI 업로드 단계에서는 UTF-8 인코딩, XML well-formed 여부만 검증합니다.
Business Core 런타임에서는 추가로 아래 조건이 맞아야 합니다.

- 메시지 정의가 1개 이상 존재
- message 이름이 중복되지 않음
- `RAW_MESSAGE` output이면 `<template>` 필수
- workflow의 `mdfTemplateName`과 정확히 일치하는 message가 존재
- required field의 var key가 `action_data_index.fields`에 존재

### 4. DOCTYPE / 외부 엔티티는 사용하지 않습니다

보안 설정상 XXE 방지를 위해 외부 엔티티/DTD 사용을 차단합니다.

---

## 관련 구현 위치

| 위치 | 역할 |
|------|------|
| `libs/ui/adapter/tc-ui-web-adapter/.../ModelController.java` | MDF 업로드 API |
| `libs/business/adapter/tc-business-db-adapter/.../BusinessMdfRuntimeParser.java` | MDF XML 파싱 |
| `libs/business/tc-business-core/.../BusinessMdfMessageComposer.java` | output 타입별 메시지 조립 |
| `libs/business/tc-business-core/.../BusinessActionDataIndexHybridResolver.java` | `action_data_index` 계약 해석 |
| `nori-tc-ui/src/features/model/lib/mdf-message-parser.ts` | UI dropdown용 message 이름/타입 추출 |

---

## 관련 문서

- [Business: 워크플로우 액션 타입](04-workflow-action-types.md)
- [Business: 모델 런타임 캐시](05-model-runtime-cache.md)
- [tc-business-core-app 운영 표준](../../../apps/tc-business-core-app/docs/Architecture/01-mdf-action-data-index-standard.md)
