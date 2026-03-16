> 작성일: 2026-03-16

# 02. DCSPECREQ_REP / DATACOLL / DCOP Item 설계

## 1. 개요

### 1.1 배경

MES는 TC에게 `DCSPECREQ_REP` 메시지를 통해 "이 lot의 공정 중에 특정 항목들의 값을 수집해서 나중에 보고해라"고 요청합니다.
TC는 이 요청을 받아 두었다가, workflow에 명시적으로 배치된 `COLLECT_DCDATA` action이 실행될 때마다 값을 누적 수집하고,
`DATACOLL` action이 실행될 때 MES에 보고합니다.

이 흐름을 구현하기 위해 아래 문제를 해결해야 합니다.

- `DCSPECREQ_REP` 수신 시점부터 `DATACOLL` 보고 시점까지 **lot 단위 상태를 안정적으로 유지**해야 합니다.
  lot 처리는 수분~수십 분에 걸쳐 진행될 수 있습니다.
- 여러 DCOP Item은 각자 다른 workflowName/variableId/collectionRule/calculationRule을 가지므로
  **유연한 수집 알고리즘**이 필요합니다.
- `Calculation Rule`은 기존 `workflow_filter` / `action_data_index`의 transform 함수와 동일한 인터페이스를
  제공해야 사용자가 일관된 문법을 사용할 수 있습니다.
  현재 이 함수들이 두 클래스에 중복 구현되어 있으므로 **공통 추출이 선행**되어야 합니다.

### 1.2 목표

- `DCSPECREQ_REP → COLLECT_DCDATA → DATACOLL` 전체 흐름을 정의합니다.
- 상태 저장소로 Redis를 사용하는 근거와 설계를 확정합니다.
- DCOP Item의 Collection Rule / Calculation Rule / Order Rule 알고리즘을 정의합니다.
- `BusinessTransformSupport` 공통 추출 설계를 확정합니다.
- `COLLECT_DCDATA` / `DATACOLL` TCAction의 책임과 실행 흐름을 정의합니다.

### 1.3 참조 문서

- 기능 가이드: `docs/Architecture/business/09-dcspecreq-datacoll-dcop-item-guide.md`
- 워크플로우 액션 타입: `docs/Architecture/business/04-workflow-action-types.md`
- 구현 작업 계획: `apps/tc-business-core-app/docs/tasks/02-dcspecreq-datacoll-dcop-item-build-plan.md`

---

## 2. 범위와 전제

### 2.1 범위

- `DCSPECREQ_REP` 수신 처리 및 Redis 저장
- DCOP Item 기반 수집 엔진 (Collection Rule / Calculation Rule / Order Rule)
- `COLLECT_DCDATA` TCAction 구현 (명시적 수집 action)
- `DATACOLL` TCAction 구현
- `BusinessTransformSupport` 공통 유틸 추출 (기존 중복 제거 포함)
- Business Redis 어댑터 포트/어댑터 신규 구현

### 2.2 비범위

- 본 문서는 설계 문서이며 실제 코드 구현 결과를 포함하지 않습니다.
- `tc_model_dcop_item` DB 스키마 변경은 포함하지 않습니다. 기존 컬럼을 그대로 사용합니다.
- Kafka envelope 포맷 자체를 변경하지 않습니다.
- MDF 선언 방식 변경은 포함하지 않습니다.
- UI(nori-tc-ui)의 DCOP Item 편집 화면 개선은 별도 작업입니다.

### 2.3 확정 전제

- `DCSPECREQ_REP`의 `data.dcspecValue`는 `{ key: "" }` 형태의 JSON object입니다.
  key는 수집 대상 항목명, value는 TC가 채워야 할 공란입니다.
- `dcopItemName`은 `dcspecValue`의 key와 동일한 이름이어야 매핑됩니다.
- 수집 상태는 Redis에 저장합니다 (in-memory 방식은 아래 2절에서 기각).
- AVERAGE는 모든 개별 값을 저장하지 않고 `count + sum`으로 집계합니다.
  `sum / count`는 전체 배열 평균과 수학적으로 동일하며, 저장 크기가 `COLLECT_DCDATA` 실행 횟수와 무관합니다.
- Calculation Rule은 transform compact text 문법을 그대로 사용합니다.
- Business Redis는 6380 포트, 기존 `tc-business-redis-adapter` 인프라를 재사용합니다.

---

## 3. 상태 저장소 설계 결정: Redis

### 3.1 메모리(in-memory) 방식 검토

| 항목 | 내용 |
|---|---|
| 구현 방식 | `ConcurrentHashMap<String, DatacollState>` (key = `eqpId:correlationId`) |
| 장점 | 단순, 빠름, 외부 의존 없음 |
| 단점 1 | 프로세스 재시작 시 수집 중인 lot의 collectionState 유실 → 빈 값으로 DATACOLL 보고 |
| 단점 2 | DATACOLL action 없이 lot이 중단(오류/취소)되면 메모리에서 제거되지 않아 누수 발생 |
| 단점 3 | 누수 방지를 위한 TTL 메커니즘을 직접 구현해야 함 |

→ **기각**: "메모리 누수 없어야 하고 안정성이 있어야 함" 요구사항을 충족하지 못함.

### 3.2 Redis 방식 채택

| 항목 | 내용 |
|---|---|
| 재시작 안전성 | 프로세스 재시작 후에도 수집 상태 유지 |
| 메모리 누수 방지 | TTL 지원으로 자동 만료 |
| 기존 인프라 재사용 | `tc-business-redis-adapter` (Business Redis 6380) 이미 존재 |
| GC 영향 없음 | JVM heap 외부 저장으로 GC 압박 없음 |

### 3.3 Redis 용량 계산 (집계 방식 기준)

AVERAGE는 `count + sum`으로 집계하므로 `COLLECT_DCDATA` 실행 횟수와 무관하게 크기가 고정됩니다.

| 항목 | 계산 | 결과 |
|---|---|---|
| DCOP item당 collectionState | rule + 집계값 ~100 bytes | ~100 bytes |
| lot당 크기 (1,000 DCOP items) | 1,000 × 100 bytes + dcspecValue + JSON 오버헤드 | ~150KB |
| 1,000 설비 × 동시 1 lot | 1,000 × 150KB | ~150MB |
| 1,000 설비 × 동시 10 lot | 10,000 × 150KB | ~1.5GB |
| 8GB Redis 최대 동시 lot 수 | 8GB / 150KB | ~53,000 lot |

현재 운영 규모에서 충분히 감당 가능합니다.

### 3.4 Redis Key 구조

```
Key:   tc:business:core:datacoll:{eqpId}:{correlationId}
Value: JSON (DatacollState)
TTL:   tc.business.core.redis.datacoll-ttl-seconds (운영 정책 기준)
```

Value JSON 구조:

```json
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

---

## 4. DCOP Item 수집 알고리즘

### 4.1 Collection Rule

`COLLECT_DCDATA` action 실행 시 Redis의 `collectionState`를 다음 규칙으로 갱신합니다.
같은 workflowName의 `COLLECT_DCDATA`가 여러 번 실행되면 Rule에 따라 누적됩니다.

| Rule | 갱신 방식 |
|---|---|
| **FIRST** | 해당 항목의 `collectionState`가 비어 있을 때만 저장. 이후 실행 무시 |
| **LAST** | 매 실행마다 `value` 덮어쓰기 |
| **AVERAGE** | `count += 1`, `sum += 수집값` |
| **MIN** | `value` 없으면 저장. 있으면 현재 값보다 작을 때만 갱신 |
| **MAX** | `value` 없으면 저장. 있으면 현재 값보다 클 때만 갱신 |

DATACOLL action 실행 시 최종값 결정:

| Rule | 최종값 결정 |
|---|---|
| FIRST | `value` 그대로 사용 |
| LAST | `value` 그대로 사용 |
| AVERAGE | `sum / count` (count가 0이면 빈 문자열) |
| MIN | `value` 그대로 사용 |
| MAX | `value` 그대로 사용 |

### 4.2 Calculation Rule

Collection Rule로 최종값을 결정한 뒤 후처리로 적용하는 계산 함수입니다.

- `BusinessTransformSupport.applyTransform(value, TransformSpec)`을 호출합니다.
- 문법: `workflow_filter` / `action_data_index`의 `transforms`와 동일한 compact text 형식.
  예: `"add(10)"`, `"toint"`, `"sub(5)"`
- null이면 Collection Rule 최종값을 그대로 사용합니다.
- 적용 실패 시 이전 값을 유지하고 warn 로그를 남깁니다.

### 4.3 Order Rule 수집 순서

`dcspecValue`를 채울 때의 순서입니다.

```
Step 1: orderRule=0 인 DCOP Item들 → dcopItemName ASC 정렬 → 순서대로 수집
Step 2: orderRule=1 인 DCOP Item들 → dcopItemName ASC 정렬 → 순서대로 수집
Step 3: orderRule=2 ...
        (수집 가능한 모든 DCOP Item 처리 완료까지 반복)
```

DB 조회 정렬 기준: `order_rule ASC, dcop_item_name ASC`

---

## 5. BusinessTransformSupport 공통 추출 설계

### 5.1 현황

`BusinessActionDataIndexHybridResolver`와 `BusinessWorkflowFilterEvaluator`에
`TransformSpec` record + `applyTransform()` + 헬퍼 메서드들이 byte-level 수준으로 동일하게 중복 구현되어 있습니다.

차이점: `transformSubstring()`에서 `FilterEvaluator` 쪽만 `args.isEmpty()` 방어 코드가 추가됨.

### 5.2 추출 설계

**신규 파일**:
`libs/business/tc-business-core/src/main/java/com/nori/tc/business/core/workflow/internal/support/BusinessTransformSupport.java`

**공개 범위**:
- `TransformSpec` record: `public`
- `applyTransform(Object value, TransformSpec transform)`: `public static`
- 헬퍼 메서드들: `package-private` 또는 `private static`

**통일 기준**:
- `transformSubstring()`은 `FilterEvaluator` 쪽의 방어 코드(`args.isEmpty()` 체크)가 더 안전하므로 해당 버전으로 통일합니다.

**변경 대상**:
- `BusinessActionDataIndexHybridResolver`: 내부 `TransformSpec` record 제거 → `BusinessTransformSupport.TransformSpec` 사용
- `BusinessWorkflowFilterEvaluator`: 내부 `TransformSpec` record 제거 → `BusinessTransformSupport.TransformSpec` 사용

**재사용 대상**:
- `CollectDcdataTcAction`의 Calculation Rule 적용: `BusinessTransformSupport.applyTransform()` 호출

---

## 6. TCAction 설계

### 6.1 COLLECT_DCDATA TCAction

#### 역할

workflow에 명시적으로 배치하는 수집 action입니다.
이 action이 포함된 workflow의 `workflowName`과 일치하는 DCOP Item의 값을 수집하고 Redis `collectionState`를 갱신합니다.
동일한 workflowName의 `COLLECT_DCDATA`가 여러 번 실행되면 Collection Rule에 따라 값이 누적됩니다.

> 이벤트 발생마다 자동으로 수집되지 않습니다. workflow에 `COLLECT_DCDATA` action이 없으면 해당 workflow는 수집에 참여하지 않습니다.

#### 실행 흐름

```
1. context에서 eqpId, correlationId, 현재 workflowName 확인
2. Redis에서 DatacollState 조회 (없으면 DCSPECREQ_REP 미수신으로 간주, 수집 건너뜀)
3. workflowName과 일치하는 DCOP Item 목록 조회
4. 각 DCOP Item에 대해:
   a. variableId로 현재 이벤트 payload에서 값 추출
   b. Collection Rule에 따라 Redis collectionState 갱신
      - FIRST: collectionState 없을 때만 저장
      - LAST: 항상 덮어쓰기
      - AVERAGE: count += 1, sum += 수집값
      - MIN: 없으면 저장, 더 작으면 갱신
      - MAX: 없으면 저장, 더 크면 갱신
5. DatacollStatePort.update()로 Redis 갱신
```

#### 실패 정책

| 상황 | 처리 방식 |
|---|---|
| Redis에 DatacollState 없음 | warn 로그 후 수집 건너뜀 (DCSPECREQ_REP 없이 진행 중인 lot) |
| variableId에 해당하는 값 없음 | 해당 DCOP Item 건너뜀, debug 로그 |
| Redis 갱신 실패 | error 로그, 예외 전파 |

---

### 6.2 DATACOLL TCAction

#### 역할

workflow action type이 `DATACOLL`일 때 실행됩니다.
Redis에서 수집 상태를 읽어 최종값을 결정하고, MES에 DATACOLL 메시지를 보고한 뒤 Redis를 정리합니다.

#### 실행 흐름

```
1. context에서 eqpId와 correlationId 추출
2. Redis에서 DatacollState 읽기 (key: tc:business:core:datacoll:{eqpId}:{correlationId})
3. DatacollState가 없으면 warn 로그 후 중단 (DCSPECREQ_REP를 받지 않은 상태)
4. modelRuntime에서 해당 modelVersionKey의 DCOP Item 목록 조회
5. Order Rule 순서 (orderRule ASC, dcopItemName ASC)로 DCOP Item 정렬
6. 각 DCOP Item에 대해:
   a. collectionState에서 Collection Rule 최종값 결정
   b. calculationRule이 있으면 BusinessTransformSupport.applyTransform() 적용
   c. dcspecValue[dcopItemName]에 결과값 설정
7. MDF 메시지 조립 (output=KAFKA, target=MES)
8. DATACOLL Kafka 메시지 발행
9. Redis key 즉시 삭제
```

#### 실패 정책

| 상황 | 처리 방식 |
|---|---|
| Redis에 DatacollState 없음 | warn 로그 후 DATACOLL 발행 건너뜀 (lot 상태 오류로 간주) |
| 특정 dcopItemName의 collectionState 없음 | 해당 항목 빈 문자열(`""`)로 처리 후 계속 |
| Calculation Rule 적용 실패 | Collection Rule 결과값 그대로 유지, warn 로그 |
| Redis 삭제 실패 | error 로그. TTL로 최종 정리됨 (누수 방지) |

---

## 7. 코드 반영 범위

### 7.1 신규 생성 파일

| 파일 경로 | 설명 |
|---|---|
| `libs/business/tc-business-core/.../support/BusinessTransformSupport.java` | TransformSpec + applyTransform 공통 유틸 |
| `libs/business/tc-business-core/.../port/DatacollStatePort.java` | Redis 저장소 포트 인터페이스 |
| `libs/business/adapter/tc-business-redis-adapter/.../datacoll/DatacollRedisAdapter.java` | Redis 저장소 구현 |
| `libs/business/adapter/tc-business-redis-adapter/.../datacoll/DatacollRedisProperties.java` | TTL 등 Redis 속성 |
| `libs/business/tc-business-core/.../action/CollectDcdataTcAction.java` | COLLECT_DCDATA TCAction (명시적 수집) |
| `libs/business/tc-business-core/.../action/DatacollTcAction.java` | DATACOLL TCAction (MES 보고) |

### 7.2 수정 파일

| 파일 경로 | 변경 내용 |
|---|---|
| `libs/business/tc-business-core/.../support/BusinessActionDataIndexHybridResolver.java` | 내부 TransformSpec → BusinessTransformSupport 사용 |
| `libs/business/tc-business-core/.../matching/BusinessWorkflowFilterEvaluator.java` | 내부 TransformSpec → BusinessTransformSupport 사용 |
| `apps/tc-business-core-app/config/tc-business-core.properties` | `datacoll-ttl-seconds` 설정 추가 |

---

## 8. 실패 정책 및 로그 정책

### 8.1 로그 레벨 기준

| 상황 | 레벨 |
|---|---|
| DCSPECREQ_REP 수신 → Redis 저장 성공 | DEBUG |
| COLLECT_DCDATA action 실행 → collectionState 갱신 성공 | DEBUG |
| Redis에 DatacollState 없이 COLLECT_DCDATA action 실행 | WARN |
| DATACOLL 보고 완료 | INFO |
| Redis에 DatacollState 없이 DATACOLL action 실행 | WARN |
| 특정 DCOP Item의 collectionState 없음 | WARN |
| Calculation Rule 적용 실패 | WARN |
| Redis 저장/조회/삭제 실패 | ERROR |

### 8.2 TTL과 누수 방지

- TTL은 `tc.business.core.redis.datacoll-ttl-seconds`로 설정합니다.
- DATACOLL 보고 후 즉시 삭제하는 것이 기본이지만, 삭제 실패 시에도 TTL이 자동으로 만료합니다.
- TTL을 운영 정책에 맞게 충분히 길게 설정해야 합니다 (lot 처리 시간보다 여유롭게).
