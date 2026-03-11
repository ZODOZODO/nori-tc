# 01. MDF/action_data_index 운영 표준

## 1) 배경/목표
- `tc-business-core-app`는 `modelVersionKey` 단위로 MDF(XML)를 로드해 메시지 템플릿을 조립합니다.
- `action_data_index`는 MDF 템플릿 필드 값을 어떤 경로/상수/변환으로 채울지 정의합니다.
- 본 문서의 목표는 운영 표준을 고정해, 모델 등록 품질과 장애 대응 일관성을 확보하는 것입니다.

## 2) MDF XML 지원 포맷
현재 구현 기준으로 MDF XML은 아래 2가지 포맷을 지원합니다.

### 2-1. 명시형 포맷 (`<message ...>`)
```xml
<mdf>
  <message name="TOOL_CONDITION_REQUEST_EQP" target="EQP" action="PUBLISH_EQP_COMMAND" output="RAW_MESSAGE">
    <template>CMD=TOOL_CONDITION_REQUEST EQPID={EQPID}</template>
    <field name="EQPID" var="eqpId" source="CTX" required="true"/>
  </message>
</mdf>
```

명시형에서 주로 사용하는 속성:
- `name`: MDF 메시지 정의 이름
- `target`: `EQP` 또는 `MES`
- `action`: `PUBLISH_EQP_COMMAND` 또는 `PUBLISH_MES_COMMAND`
- `output`: `RAW_MESSAGE` 또는 `DATA`
- `template`: 템플릿 문자열
- `field`: 필드 단위 매핑 정의

### 2-2. 축약형 포맷 (`<TOOL_CONDITION_REQUEST_EQP>...</...>`)
```xml
<mdf>
  <TOOL_CONDITION_REQUEST_EQP>CMD=TOOL_CONDITION_REQUEST EQPID={EQPID}</TOOL_CONDITION_REQUEST_EQP>
  <TOOL_CONDITION_REPLY_MES>EQPID={EQPID} STATUS={STATUS} ERRORCODE={ERRORCODE}</TOOL_CONDITION_REPLY_MES>
</mdf>
```

축약형 규칙:
- 태그명이 메시지명입니다.
- `_EQP`, `_MES` suffix로 target을 추론합니다.
- 텍스트 본문이 template입니다.

## 3) `action_data_index` 표준 JSON 스펙

### 3-1. 허용 키
- 루트: `mdf`, `messageName`, `message`, `fields`
- 필드 객체: `var`, `source`, `xform`, `fixed`, `required`

운영 표준 키는 아래로 고정합니다.
- 루트 선택자: `mdf`
- 필드 맵: `fields`

### 3-2. 기본값
- `source` 미지정 시: `AUTO`
- `required` 미지정 시: `true`

### 3-3. 필드값 우선순위
동일 필드에 대해 값 해석 우선순위는 다음과 같습니다.
1. `action_data_index.fields[field]`
2. MDF `<field name="...">` 정의
3. 기본 규칙: `AUTO + field 경로 + required=false`

### 3-4. 표준 JSON 스켈레톤
```json
{
  "mdf": "TOOL_CONDITION_REPLY_MES",
  "fields": {
    "EQPID": { "var": "eqpId", "source": "CTX", "required": true },
    "STATUS": { "var": "data.status", "source": "MSG", "xform": ["trim", "upper"] },
    "ERRORCODE": { "fixed": "E000" }
  }
}
```

## 4) 값 해석/변환 규칙

### 4-1. 필드 식 지원
- 문자열 경로식: `"EQPID": "eqpId"`
- 객체식: `var/source/xform/fixed/required`

### 4-2. source 의미
- `MSG`: `messageVariables`에서 조회
- `CTX`: `contextVariables`에서 조회
- `AUTO`: `MSG` 우선 조회 후 없으면 `CTX`

### 4-3. xform 체인
- `xform` 배열은 순차 적용합니다.
- 지원 변환은 구현체 기준으로 사용합니다. 예: `trim`, `upper`, `split(...)`, `substring(...)`, `toInt`, `toLong`, `add`, `sub`, `length`.

### 4-4. 누락/실패 정책
- `required=true`이고 값이 없으면 실패(예외) 처리합니다.
- `required=false`이고 값이 없으면 빈 문자열(`""`)로 대체합니다.
- `xform` 적용 실패 시 이전 값을 유지하고 `warn` 로그를 남깁니다.

## 5) EQP/MES 표준 예시

### 5-1. 최소형 예시 (`action_data_index`가 메시지명만 지정)
```text
action_data_index = TOOL_CONDITION_REQUEST_EQP
```

동작:
- MDF 메시지명을 직접 선택합니다.
- 필드 값은 MDF `<field>` 정의 또는 기본 규칙으로 계산합니다.

### 5-2. 권장형 예시 (운영 표준)
```json
{
  "mdf": "TOOL_CONDITION_REPLY_MES",
  "fields": {
    "EQPID": { "var": "eqpId", "source": "CTX", "required": true },
    "STATUS": { "var": "data.status", "source": "MSG", "xform": ["trim", "upper"] },
    "ERRORCODE": { "fixed": "E000" },
    "ERRORMSG": { "var": "data.errorMessage", "required": false },
    "MATID": { "var": "data.materialId", "required": false },
    "CARID": { "var": "data.carrierId", "required": false },
    "PORTID": { "var": "data.portId", "required": false },
    "STOCKERID": { "var": "data.stockerId", "required": false },
    "SHELFID": { "var": "data.shelfId", "required": false }
  }
}
```

예상 결과:
- MES `PUBLISH_MES_COMMAND`에서 `data`를 MDF 기반으로 조립
- `metadata`(correlationId/key 정책)는 기존 발행 계층 정책 유지

### 5-3. 장애 유도형 예시
```json
{
  "mdf": "TOOL_CONDITION_REQUEST_EQP",
  "fields": {
    "EQPID": { "var": "eqpId", "source": "CTX", "required": true },
    "STATUS": { "var": "data.status", "xform": ["uppper"] }
  }
}
```

예상 결과:
- `STATUS`의 `uppper`는 오타이므로 변환 실패
- 변환 실패 시 이전 값 유지 + `warn` 로그
- `EQPID` 누락 시(필수값) 즉시 실패

## 6) 사용자 요청 샘플 반영 예시
아래 MDF 템플릿은 운영에서 사용 가능한 표준 샘플입니다.

```xml
<mdf>
  <TOOL_CONDITION_REQUEST_EQP>CMD=TOOL_CONDITION_REQUEST EQPID={EQPID}</TOOL_CONDITION_REQUEST_EQP>
  <TOOL_CONDITION_REPLY_MES>EQPID={EQPID} STATUS={STATUS} ERRORCODE={ERRORCODE} ERRORMSG={ERRORMSG} MATID={MATID} CARID={CARID} PORTID={PORTID} STOCKERID={STOCKERID} SHELFID={SHELFID}</TOOL_CONDITION_REPLY_MES>
</mdf>
```

권장 `action_data_index`:
- EQP 발행 워크플로우: `TOOL_CONDITION_REQUEST_EQP`
- MES 발행 워크플로우: 5-2 권장형 JSON 사용

## 7) 실패 정책 및 운영 관측성

### 7-1. 주요 실패 케이스
- MDF XML 파싱 실패
- 필수 속성 누락(`name`, `template`, target 추론 불가)
- `action_data_index` JSON 파싱 실패
- 필수값 누락(`required=true`)
- MDF 메시지 후보 복수 매칭 충돌

### 7-2. 로그 레벨 가이드
- `info`: 초기화/재로드 완료, 발행 성공
- `debug`: 선택된 MDF 메시지, 필드 수, payload 요약
- `trace`: xform 중간값(고빈도이므로 최소 지점만)
- `warn`: 복구 가능한 이상(`required=false` 누락 대체, xform 실패 대체)
- `error`: 파싱 실패, 필수값 누락, 발행 실패

## 8) 운영 체크리스트

### 8-1. MDF 등록 전 점검
- XML이 UTF-8이며 파싱 가능한지 확인
- 메시지명 중복이 없는지 확인
- `_EQP`/`_MES` suffix 또는 target 속성이 명확한지 확인
- 템플릿 placeholder와 field 정의가 대응되는지 확인

### 8-2. action_data_index 작성 점검
- 표준 키(`mdf`, `fields`, `var`, `source`, `xform`, `fixed`, `required`)만 사용
- 필수 필드는 `required=true`로 명시
- `source`가 데이터 위치와 맞는지 확인
- `xform` 오타/인자 개수 검증

### 8-3. 배포 전 검증
- README → 표준 문서 링크 확인
- MDF 샘플과 action_data_index를 테스트 환경에서 1회 검증
- EQP/MES 발행 결과가 기대 문자열/데이터와 일치하는지 확인

### 8-4. 장애 발생 시 확인 순서
1. MDF XML 파싱/선택 로그 확인
2. `action_data_index` 파싱 로그 확인
3. 필수값 누락 여부 확인
4. xform 경고 발생 여부 확인
5. 발행 계층 error 로그 및 traceId/correlationId 연계 확인

## 9) 문서-코드 정합성 검증 포인트
다음 코드를 기준으로 본 문서 규칙이 유지되어야 합니다.
- [BusinessMdfRuntimeParser.java](../../../libs/business/adapter/tc-business-db-adapter/src/main/java/com/nori/tc/business/adapters/db/modelcache/BusinessMdfRuntimeParser.java)
- [BusinessActionDataIndexHybridResolver.java](../../../libs/business/tc-business-core/src/main/java/com/nori/tc/business/core/workflow/internal/support/BusinessActionDataIndexHybridResolver.java)
- [BusinessMdfMessageComposer.java](../../../libs/business/tc-business-core/src/main/java/com/nori/tc/business/core/workflow/internal/support/BusinessMdfMessageComposer.java)

문서와 구현이 불일치하면 구현을 기준으로 문서를 즉시 갱신합니다.

## 10) 범위/가정
- 본 문서는 운영 표준 문서화 범위입니다.
- 코드 API/DB 스키마/런타임 동작 변경은 포함하지 않습니다.
- SQL 벤더별 등록 스크립트는 제외하며, 규약/예시/체크리스트 중심으로 유지합니다.