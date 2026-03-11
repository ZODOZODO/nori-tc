# 05. 모델 런타임 캐시 (Model Runtime Cache)

## 개요

Business Core는 인바운드 메시지 처리 시 매번 DB를 조회하지 않고,
워크플로우/메시지 정의/변수/MDF를 **메모리 인덱스**로 미리 구성해 빠르게 조회한다.
이 인메모리 구조를 **모델 런타임 캐시**라고 부른다.

모든 장비는 `eqpId → modelVersionKey` 바인딩을 가지며,
같은 `modelVersionKey`를 가진 장비들은 **동일한 `TcModelRuntime` 인스턴스**를 공유한다.

---

## 왜 인메모리 캐시가 필요한가?

| 이유 | 설명 |
|------|------|
| 처리 속도 | Kafka 메시지를 수신할 때마다 DB를 조회하면 레이턴시 급증 |
| 동시성 | 수십~수백 장비의 메시지를 병렬 처리 — DB 커넥션 풀 고갈 방지 |
| 인덱스 구조 | `messageName`, `eventId/transactionId` 등 복합 키 인덱스를 메모리에서 O(1) 조회 |

---

## 구조 다이어그램

```
BusinessModelRuntimeCache
        │
        └── AtomicReference<BusinessModelRuntimeSnapshot>
                │
                ├── eqpModelBindings: Map<eqpId, modelVersionKey>
                └── modelRuntimes: Map<modelVersionKey, TcModelRuntime>


TcModelRuntime (modelVersionKey 1개당 1 인스턴스)
        │
        ├── workflowsByMessageName: Map<messageName, List<WorkflowRuntimeEntry>>
        ├── secsWorkflowsByKey: Map<SecsWorkflowKey, List<WorkflowRuntimeEntry>>
        ├── secsMessagesByName: Map<String, TcModelSecsMessage>
        ├── socketMessagesByName: Map<String, TcModelSocketMessage>
        ├── variableIds: Map<VariableRuntimeKey, TcModelVariableId>
        └── mdfRuntimeDefinition: MdfRuntimeDefinition


BusinessRuntimeEngine (처리 요청)
        │
        └── snapshot.findRuntimeByEqpId(record.eqpId())
                → eqpModelBindings.get(eqpId) → modelVersionKey
                → modelRuntimes.get(modelVersionKey) → TcModelRuntime
```

---

## 핵심 클래스

| 클래스 | 역할 |
|--------|------|
| `BusinessModelRuntimeCache` | 캐시 관리 (로드/갱신/CAS 교체) |
| `BusinessModelRuntimeAssembler` | DB 조회 → `TcModelRuntime` 조립 |
| `BusinessModelRuntimeSnapshot` | 불변 스냅샷 (eqpBindings + modelRuntimes) |
| `TcModelRuntime` | 단일 모델 버전의 인덱스 구조 |
| `WorkflowRuntimeEntry` | 단일 워크플로우 행 (messageName, filter, actionName …) |
| `MdfRuntimeDefinition` | XML 기반 MDF 메시지 정의 |
| `BusinessModelCacheProperties` | 캐시 설정 (pageSize, loadOnStartup, failFastOnStartup) |

---

## TcModelRuntime 내부 인덱스

### workflowsByMessageName

```
key:   messageName (String)
value: List<WorkflowRuntimeEntry> — order 오름차순 정렬
```

- `findWorkflowsByMessageName(messageName)` → O(1) 조회
- SECS/SOCKET 공통 인덱스

### secsWorkflowsByKey

```
key:   SecsWorkflowKey (messageName + eventId + transactionId)
value: List<WorkflowRuntimeEntry>
```

- eventId/transactionId 조건이 있는 SECS 전용 정밀 인덱스
- `findSecsWorkflows(messageName, eventId, transactionId)` → O(1) 조회

### variableIds

```
key:   VariableRuntimeKey (variableIdType + variableId)
value: TcModelVariableId
```

- SECS VID/DVVAL 등 변수 정의 조회에 사용

---

## 캐시 적재 흐름

```
@PostConstruct (BusinessModelRuntimeCache.initialize())
        │
        └── BusinessModelRuntimeAssembler.assemble(modelVersionKey)
                │
                ├── [1] TcModelStore.findByModelVersionKey(key) → TcModel 조회
                ├── [2] TcModelMdfStore.findByModelVersionKey(key) → MDF XML 조회
                ├── [3] BusinessMdfRuntimeParser.parse(mdf) → MdfRuntimeDefinition 파싱
                ├── [4] TcModelWorkflowStore.findAllByModelVersionKey(key) [페이지 로드]
                ├── [5] TcModelSecsMessageStore.findAllByModelVersionKey(key) [페이지 로드]
                ├── [6] TcModelSocketMessageStore.findAllByModelVersionKey(key) [페이지 로드]
                ├── [7] TcModelVariableIdStore.findAllByModelVersionKey(key) [페이지 로드]
                └── TcModelRuntime.from(model, workflows, secsMessages, socketMessages, variableIds, mdf)
                        → messageName 인덱스 구성
                        → SECS 키 인덱스 구성
                        → 변수 인덱스 구성
```

페이지 로드: 대용량 모델도 `pageSize` 단위로 반복 조회 → OOM 방지

---

## CAS(Compare-And-Swap) 기반 무중단 갱신

캐시 갱신은 **새 스냅샷을 만든 뒤 CAS로 교체**한다.
읽기 경로는 항상 완성된 스냅샷만 바라보므로, 갱신 중간 상태가 노출되지 않는다.

```java
while (true) {
    BusinessModelRuntimeSnapshot current = snapshotRef.get();
    BusinessModelRuntimeSnapshot next = ... // 새 스냅샷 조립
    if (snapshotRef.compareAndSet(current, next)) {
        return; // 교체 성공
    }
    // 실패 시 재시도 (동시 갱신 경합)
}
```

### 갱신 트리거

| 트리거 | 설명 |
|--------|------|
| 앱 기동 `@PostConstruct` | 전체 초기 로드 |
| `reloadAll()` | 전체 재적재 (UI 요청으로 호출 가능) |
| `reloadModelRuntime(modelVersionKey)` | 특정 모델 버전만 갱신 |
| `updateEqpBinding(eqpId, modelVersionKey)` | 장비 바인딩 + 해당 모델 적재 |
| `removeEqpBinding(eqpId)` | 바인딩 제거, 참조 없어지면 runtime도 제거 |

---

## 장비 바인딩과 runtime 공유

```
eqpModelBindings:
    "EQP-001" → modelVersionKey = 100
    "EQP-002" → modelVersionKey = 100   ← 동일 모델 버전
    "EQP-003" → modelVersionKey = 200

modelRuntimes:
    100 → TcModelRuntime (EQP-001, EQP-002 공유)
    200 → TcModelRuntime (EQP-003 전용)
```

- 같은 모델 버전이면 인스턴스 1개로 메모리 절감
- `removeEqpBinding` 시 다른 장비가 참조 중이면 runtime은 유지

---

## 설정 프로퍼티

```properties
# config/tc-business.properties (예시)
tc.business.cache.load-on-startup=true       # 기동 시 캐시 자동 로드 (기본 true)
tc.business.cache.fail-fast-on-startup=true  # 로드 실패 시 앱 기동 실패 처리
tc.business.cache.page-size=500              # 페이지 로드 단위
```

| 설정 | 기본값 | 설명 |
|------|--------|------|
| `load-on-startup` | `true` | 기동 시 자동 적재 |
| `fail-fast-on-startup` | `true` | 적재 실패 시 앱 중단 |
| `page-size` | `500` | DB 페이지 단위 |

---

## 운영 포인트

| 항목 | 설명 |
|------|------|
| 기동 실패 | `fail-fast-on-startup=true`이면 캐시 적재 실패 → 앱 기동 실패 (fail-fast) |
| 모델 미등록 | 바인딩에 없는 eqpId → runtime 없음 → WORKFLOW_NOT_FOUND 처리 |
| 동시 갱신 | CAS 재시도 루프 → 경합 상황에서도 안전 |
| 메모리 | 워크플로우/메시지 정의가 많은 모델은 pageSize 조정 필요 |
| runtime 제거 | removeEqpBinding 후 참조 장비가 없으면 GC 대상 |
| 갱신 로그 | INFO 레벨 — `eqpBindings`, `runtimeCount` 출력 |

---

## 관련 문서

- [Business: 워크플로우 매칭](03-workflow-matching.md) — messageName 인덱스 사용
- [공통: DB 스타터 선택](../common/08-db-starter-selection.md) — 페이지 로드 기반 DB 조회
- [공통: 외부 설정 로딩](../common/03-external-config-loading.md) — `tc.business.cache.*` 설정
