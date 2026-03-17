> 작성일: 2026-03-16

# 09. DCSPECREQ_REP / DATACOLL / DCOP Item 가이드

## 개요

이 문서는 MES로부터 `DCSPECREQ_REP`를 수신한 시점부터 `DATACOLL`을 MES에 보고하는 시점까지의
전체 흐름과 그 핵심인 **DCOP Item 수집 규칙**을 설명합니다.

### 핵심 개념

- **DCSPECREQ_REP**: MES가 TC에게 "나중에 이 항목들의 값을 수집해서 보고해라"고 알리는 요청 응답.
  `dcspecValue`에 수집 대상 항목 목록(key)과 공란(value)이 담겨 있습니다.
- **DCOP Item**: 어떤 workflow의 `COLLECT_DCDATA` action이 실행될 때 어떤 값을 어떤 규칙으로 수집할지 정의한 모델 설정입니다.
  `dcopItemName`이 `dcspecValue`의 key와 동일하게 매핑됩니다.
- **COLLECT_DCDATA**: workflow에 명시적으로 배치하는 TCAction입니다. 이 action이 실행될 때 현재
  workflowName과 일치하는 DCOP Item의 variableId 값을 수집하고 Redis에 누적합니다.
- **DATACOLL**: TC가 MES에게 수집 완료된 값을 채워서 보고하는 TCAction입니다.
  Redis에 누적된 수집 결과를 최종 처리하여 `dcspecValue`에 채운 뒤 MES로 발행합니다.

---

## 전체 흐름

```
MES → TC : DCSPECREQ_REP
           data.dcspecValue = { "Temperature": "", "Humidity": "", "Pressure": "" }
                ↓
TC : dcspecValue key 목록과 초기 수집 상태를 Redis에 저장
     key: tc:business:core:datacoll:{eqpId}:{correlationId}
     (TTL 설정으로 메모리 누수 방지)
                ↓
TC : workflow 진행 중 ...
     workflow에 COLLECT_DCDATA action이 정의된 경우에만 수집 실행
     (이벤트 발생마다 자동 수집하지 않음. 반드시 workflow에 명시적으로 배치해야 함)
                ↓
workflow action = COLLECT_DCDATA (TCAction)
                ↓
TC : 현재 workflowName과 일치하는 DCOP Item 목록 조회
     → variableId에 해당하는 값을 Collection Rule에 따라 Redis collectionState에 누적
     (같은 workflowName으로 여러 번 COLLECT_DCDATA가 실행되면 Collection Rule에 따라 누적)
                ↓
workflow action = DATACOLL (TCAction)
                ↓
TC : Redis에서 수집 상태 읽기
     → Collection Rule 최종값 결정 (AVERAGE = sum/count, MIN/MAX = 누적 최솟값/최댓값 등)
     → Calculation Rule 적용 (BusinessTransformSupport 함수 재사용)
     → Order Rule 순서에 따라 dcspecValue 채우기
                ↓
TC → MES : DATACOLL (채워진 dcspecValue 포함)
                ↓
TC : Redis에서 해당 key 즉시 삭제
```

---

## DCSPECREQ_REP 메시지 구조

MES로부터 수신하는 메시지입니다.

```json
{
  "metadata": {
    "eventType": "DCSPECREQ_REP",
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
    "slotMap": "11111111111111111111",
    "dcspecValue": {
      "Temperature": "",
      "Humidity": "",
      "Pressure": ""
    }
  }
}
```

### 핵심 필드

| 필드 | 설명 |
|---|---|
| `metadata.correlationId` | lot 식별자. Redis key 구성에 사용 |
| `data.eqpId` | 설비 식별자. Redis key 구성에 사용 |
| `data.dcspecValue` | 수집 대상 항목 목록. key=항목명(dcopItemName과 동일), value=공란(TC가 채워야 할 자리) |

---

## DATACOLL 메시지 구조

TC가 MES로 보내는 메시지입니다.

```json
{
  "metadata": {
    "eventType": "DATACOLL",
    "timestamp": "2026-01-21T02:11:24.545202300Z",
    "source": "TC-COMM-BUSINESS-APP",
    "correlationId": "L-0121010"
  },
  "data": {
    "eqpId": "PHOTO_002",
    "interfaceType": "SOCKET",
    "dcspecValue": {
      "Temperature": "23.5",
      "Humidity": "60",
      "Pressure": "1013"
    }
  }
}
```

`dcspecValue`의 각 key에 DCOP Item 수집 결과가 채워집니다.

---

## DCOP Item 필드 정의

`tc_model_dcop_item` 테이블에 저장되는 모델별 수집 설정입니다.

| 필드 | 타입 | 설명 |
|---|---|---|
| `dcopItemName` | String | 아이템 명칭. **`dcspecValue`의 key 이름과 동일해야 합니다.** `(modelVersionKey, dcopItemName)` unique |
| `workflowName` | String | 이 이름의 workflow에서 `COLLECT_DCDATA` action이 실행될 때 수집합니다 |
| `variableId` | String | 해당 workflow 이벤트에서 수집할 값의 변수 ID |
| `collectionRule` | Enum | 수집 방식. FIRST / LAST / AVERAGE / MIN / MAX |
| `calculationRule` | String | 수집 결과에 후처리로 적용할 계산 함수. transform compact text 문법 사용 |
| `orderRule` | Integer | 수집 순서. 0부터 시작, 낮을수록 먼저 수집 |

### dcopItemName ↔ dcspecValue key 매핑 규칙

DCSPECREQ_REP에서 수신한 `dcspecValue`의 key명과 `dcopItemName`이 동일한 경우에만 매핑됩니다.

```
dcspecValue key  →  dcopItemName
"Temperature"    ↔  dcopItemName = "Temperature"
"Humidity"       ↔  dcopItemName = "Humidity"
"Pressure"       ↔  dcopItemName = "Pressure"
```

매핑되지 않는 `dcspecValue` key는 빈 문자열(`""`)로 보고됩니다.

---

## Collection Rule 상세

`COLLECT_DCDATA` action이 실행될 때마다 variableId에 해당하는 값을 어떻게 누적/결정할지 정의합니다.
같은 workflowName을 가진 `COLLECT_DCDATA`가 여러 번 실행되면 Rule에 따라 누적됩니다.

| Rule | 설명 | Redis 저장 방식 |
|---|---|---|
| **FIRST** | `COLLECT_DCDATA`가 처음 실행됐을 때의 variableId 값 | 최초 값 1개만 저장. 이후 실행은 무시 |
| **LAST** | `COLLECT_DCDATA`가 마지막으로 실행됐을 때의 variableId 값 | 매 실행마다 값 덮어쓰기 |
| **AVERAGE** | 매 실행의 variableId 값을 누적하여 평균 | `count`와 `sum`을 누적. 최종 = sum / count |
| **MIN** | 매 실행의 variableId 값 중 최솟값 | 현재 최솟값만 유지. 더 작은 값이 오면 갱신 |
| **MAX** | 매 실행의 variableId 값 중 최댓값 | 현재 최댓값만 유지. 더 큰 값이 오면 갱신 |

> **AVERAGE 구현 참고**: 모든 개별 값을 저장하지 않고 `count`와 `sum`만 누적합니다.
> `sum / count`는 전체 배열 평균과 수학적으로 동일하며, Redis 저장 크기가 이벤트 횟수와 무관하게 고정됩니다.

---

## Calculation Rule 상세

Collection Rule로 최종값을 결정한 뒤 후처리로 적용하는 계산 함수입니다.

- `null`이면 계산 없이 Collection Rule 결과를 그대로 사용합니다.
- **workflow의 `workflow_filter` / `action_data_index`에서 사용하는 `transforms`와 완전히 동일한 함수명, 동일한 동작**을 보장합니다.
- 공통 구현체 `BusinessTransformSupport`를 재사용합니다.

### 지원 함수 (transform compact text 문법)

| 함수 | 예시 | 설명 |
|---|---|---|
| `add(n)` | `add(10)` | 수집값 + n |
| `sub(n)` | `sub(5)` | 수집값 - n |
| `toint` | `toint` | 정수 변환 |
| `tolong` | `tolong` | long 변환 |
| `trim` | `trim` | 앞뒤 공백 제거 |
| `upper` | `upper` | 대문자 변환 |
| `lower` | `lower` | 소문자 변환 |
| `length` | `length` | 문자열 길이 반환 |
| `substring(s,e)` | `substring(0,3)` | 부분 문자열 추출 |
| `split(d,i)` | `split(,,0)` | 구분자로 분할 후 인덱스번째 반환 |

---

## Order Rule 수집 순서

DATACOLL 메시지의 `dcspecValue`에 값을 채울 때의 순서입니다.

```
orderRule=0 인 DCOP Item들 → dcopItemName ASC 정렬 → 순서대로 수집 및 dcspecValue 채우기
orderRule=1 인 DCOP Item들 → dcopItemName ASC 정렬 → 순서대로 수집 및 dcspecValue 채우기
orderRule=2 ...
(수집 가능한 모든 DCOP Item 처리 완료까지 반복)
```

DB 조회 정렬 기준: `order_rule ASC, dcop_item_name ASC`

---

## Redis 저장 구조

설비 + lot 단위로 격리하여 저장합니다.

```
Key: tc:business:core:datacoll:{eqpId}:{correlationId}

Value (JSON):
{
  "dcspecValue": {
    "Temperature": "",
    "Humidity": "",
    "Pressure": ""
  },
  "collectionState": {
    "Temperature": { "rule": "AVERAGE", "count": 5, "sum": 117.5 },
    "Humidity":    { "rule": "LAST",    "value": "60" },
    "Pressure":    { "rule": "MIN",     "value": "1010" }
  }
}
```

| 항목 | 내용 |
|---|---|
| **저장 시점** | DCSPECREQ_REP 수신 직후 |
| **갱신 시점** | `COLLECT_DCDATA` action 실행 시 → collectionState 업데이트 |
| **읽기 시점** | DATACOLL action 실행 시 |
| **삭제 시점** | DATACOLL 보고 완료 후 즉시 |
| **TTL** | 안전망 TTL 설정 (운영 정책 기준 설정. 예: 24시간) |
| **구현 위치** | `tc-business-redis-adapter` (Business Redis 6380) |

---

## TCAction 목록

### COLLECT_DCDATA TCAction

workflow에 명시적으로 배치하는 수집 action입니다.
이 action이 포함된 workflow의 이름(`workflowName`)과 일치하는 DCOP Item의 값을 수집합니다.

실행 흐름:
1. context에서 현재 workflowName 확인
2. Redis에서 `DatacollState` 조회 (없으면 수집 건너뜀)
3. workflowName과 일치하는 DCOP Item 목록 조회
4. 각 DCOP Item의 variableId 값 추출 → Collection Rule에 따라 Redis `collectionState` 누적 갱신

### DATACOLL TCAction

수집 결과를 MES에 보고하는 action입니다.

실행 흐름:
1. Redis에서 `tc:business:core:datacoll:{eqpId}:{correlationId}` 읽기
2. 각 dcopItemName별로 `collectionState`에서 최종값 결정 (Collection Rule 적용)
3. `calculationRule`이 있으면 `BusinessTransformSupport`로 후처리
4. `orderRule` → `dcopItemName ASC` 순서로 `dcspecValue` 채우기
5. DATACOLL 메시지를 MES로 Kafka 발행
6. Redis key 즉시 삭제

---

## 관련 도메인 모델

| 위치 | 역할 |
|---|---|
| `libs/db/tc-db-domain/.../model/TcModelDcopItem.java` | DCOP Item 도메인 모델 |
| `libs/db/tc-db-domain/.../common/model/DcopCollectionRule.java` | Collection Rule enum (FIRST/LAST/AVERAGE/MIN/MAX) |
| `libs/db/tc-db-core/.../model/store/TcModelDcopItemStore.java` | DCOP Item 조회 인터페이스 |
| `libs/business/adapter/tc-business-redis-adapter/` | Business Redis 어댑터 |
| `libs/business/tc-business-core/.../support/BusinessTransformSupport.java` | Calculation Rule 공통 함수 (transform 재사용) |

## 구현 위치

| 구성 요소 | 위치 |
|---|---|
| `DcspecreqRepTcAction` | `libs/business/tc-business-core/.../workflow/action/DcspecreqRepTcAction.java` |
| `CollectDcdataTcAction` | `libs/business/tc-business-core/.../workflow/action/CollectDcdataTcAction.java` |
| `DatacollTcAction` | `libs/business/tc-business-core/.../workflow/action/DatacollTcAction.java` |
| `DatacollStatePort` | `libs/business/tc-business-core/.../datacoll/DatacollStatePort.java` |
| `DcopItemPort` | `libs/business/tc-business-core/.../datacoll/DcopItemPort.java` |
| `DcopCollectionEngine` | `libs/business/tc-business-core/.../datacoll/DcopCollectionEngine.java` |
| `DatacollState` | `libs/business/tc-business-core/.../domain/datacoll/DatacollState.java` |
| `DatacollRedisAdapter` | `libs/business/adapter/tc-business-redis-adapter/.../redis/datacoll/DatacollRedisAdapter.java` |
| `DatacollRedisProperties` | `libs/business/adapter/tc-business-redis-adapter/.../redis/datacoll/DatacollRedisProperties.java` |
| `DcopItemDbAdapter` | `libs/business/adapter/tc-business-db-adapter/.../db/datacoll/DcopItemDbAdapter.java` |

---

## 관련 문서

- [Business: 모델 MDF 작성 가이드](08-model-mdf-authoring-guide.md)
- [Business: 워크플로우 액션 타입](04-workflow-action-types.md)
- [Business: 모델 런타임 캐시](05-model-runtime-cache.md)
- [설계 문서](../../../apps/tc-business-core-app/docs/design/02-dcspecreq-datacoll-dcop-item-design.md)
- [구현 작업 계획](../../../apps/tc-business-core-app/docs/tasks/02-dcspecreq-datacoll-dcop-item-build-plan.md)
