# 05. 인메모리 캐시 (Model Runtime / Param / Work Cache)

## 개요

Business Core는 인바운드 메시지 처리 시 매번 DB를 조회하지 않고,
워크플로우 정의·파라미터·work 정보를 **인메모리 캐시**로 관리하여 hot path 성능을 확보한다.

| 캐시 | 대상 데이터 | 적재 방식 | 갱신 방식 |
|------|------------|-----------|-----------|
| **Model Runtime Cache** | 워크플로우/메시지 정의/변수/MDF | 앱 기동 시 전체 로드 | 모델 변경 시 부분 reload (CAS) |
| **Model Param Cache** | model/eqp 파라미터 (`paramName → paramValue`) | 앱 기동 시 전체 로드 | DB 변경 시 부분 reload (CAS) |
| **Work Cache** | work + lot + carrier + controlJob + processJob | 첫 접근 시 eqpId 단위 lazy 로드 | work create/update/delete 시 syncWork |

세 캐시 모두 **Port-Adapter 구조**(core에 포트 인터페이스, db-adapter에 구현체)를 따른다.

---

## 공통 설계 원칙

### Port-Adapter 구조

```
tc-business-core (인터페이스)          tc-business-db-adapter (구현체)
─────────────────────────────          ──────────────────────────────
BusinessModelRuntimeProvider      ←→   BusinessModelRuntimeCache
BusinessModelParamProvider        ←→   BusinessModelParamCache
BusinessModelParamMutationPort    ←→   BusinessModelParamCache
BusinessWorkProvider              ←→   BusinessWorkCache
BusinessWorkMutationPort          ←→   BusinessWorkCache
```

### CAS(Compare-And-Swap) 기반 무중단 갱신

Model Runtime Cache와 Model Param Cache는 **새 스냅샷을 만든 뒤 CAS로 교체**한다.
읽기 경로는 항상 완성된 스냅샷만 바라보므로, 갱신 중간 상태가 노출되지 않는다.

```java
while (true) {
    XxxSnapshot current = snapshotRef.get();
    XxxSnapshot next = ... // 새 스냅샷 조립
    if (snapshotRef.compareAndSet(current, next)) {
        return; // 교체 성공
    }
    // 동시 갱신 경합 시 재시도
}
```

---

## Part 1. Model Runtime Cache

### 구조 다이어그램

```
BusinessModelRuntimeCache
        │
        └── AtomicReference<BusinessModelRuntimeSnapshot>
                │
                ├── eqpModelBindings: Map<eqpId, modelVersionKey>
                ├── eqpKeyBindings:   Map<eqpId, eqpKey>           ← Param/Work Cache에서 참조
                └── modelRuntimes:    Map<modelVersionKey, TcModelRuntime>


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

### 핵심 클래스

| 클래스 | 역할 |
|--------|------|
| `BusinessModelRuntimeCache` | 캐시 관리 (로드/갱신/CAS 교체) |
| `BusinessModelRuntimeAssembler` | DB 조회 → `TcModelRuntime` 조립 |
| `BusinessModelRuntimeSnapshot` | 불변 스냅샷 (eqpBindings + eqpKeyBindings + modelRuntimes) |
| `TcModelRuntime` | 단일 모델 버전의 인덱스 구조 |
| `WorkflowRuntimeEntry` | 단일 워크플로우 행 (messageName, filter, actionName …) |
| `MdfRuntimeDefinition` | XML 기반 MDF 메시지 정의 |
| `BusinessModelCacheProperties` | 캐시 설정 (pageSize, loadOnStartup, failFastOnStartup) |

### TcModelRuntime 내부 인덱스

#### workflowsByMessageName

```
key:   messageName (String)
value: List<WorkflowRuntimeEntry> — order 오름차순 정렬
```

- `findWorkflowsByMessageName(messageName)` → O(1) 조회
- SECS/SOCKET 공통 인덱스

#### secsWorkflowsByKey

```
key:   SecsWorkflowKey (messageName + eventId + transactionId)
value: List<WorkflowRuntimeEntry>
```

- eventId/transactionId 조건이 있는 SECS 전용 정밀 인덱스
- `findSecsWorkflows(messageName, eventId, transactionId)` → O(1) 조회

#### variableIds

```
key:   VariableRuntimeKey (variableIdType + variableId)
value: TcModelVariableId
```

- SECS VID/DVVAL 등 변수 정의 조회에 사용

### 캐시 적재 흐름

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
                └── TcModelRuntime.from(...)
                        → messageName 인덱스 구성
                        → SECS 키 인덱스 구성
                        → 변수 인덱스 구성
```

페이지 로드: 대용량 모델도 `pageSize` 단위로 반복 조회 → OOM 방지

### 장비 바인딩과 runtime 공유

```
eqpModelBindings:
    "EQP-001" → modelVersionKey = 100
    "EQP-002" → modelVersionKey = 100   ← 동일 모델 버전
    "EQP-003" → modelVersionKey = 200

eqpKeyBindings:
    "EQP-001" → eqpKey = 1
    "EQP-002" → eqpKey = 2
    "EQP-003" → eqpKey = 3

modelRuntimes:
    100 → TcModelRuntime (EQP-001, EQP-002 공유)
    200 → TcModelRuntime (EQP-003 전용)
```

- 같은 모델 버전이면 인스턴스 1개로 메모리 절감
- `removeEqpBinding` 시 다른 장비가 참조 중이면 runtime은 유지

### eqpId → eqpKey 매핑 (eqpKeyBindings)

파라미터 캐시와 Work 캐시는 내부적으로 DB PK(`eqpKey`)를 키로 사용한다.
업무 처리 경로에서는 항상 문자열 식별자 `eqpId`가 전달되므로,
`BusinessModelRuntimeSnapshot`에 `eqpId → eqpKey` 매핑을 보유한다.

```java
// BusinessModelRuntimeProvider 인터페이스의 default 메서드
default Optional<Long> findEqpKeyByEqpId(final String eqpId) {
    return currentSnapshot().findEqpKeyByEqpId(eqpId);
}
```

`eqpKeyBindings`는 `BusinessModelRuntimeCache` 부트스트랩 시 `TcEqp` 목록으로부터 구성되며,
`updateEqpBinding` / `removeEqpBinding` 시 함께 갱신된다.

### 갱신 트리거

| 트리거 | 설명 |
|--------|------|
| 앱 기동 `@PostConstruct` | 전체 초기 로드 |
| `reloadAll()` | 전체 재적재 (UI 요청으로 호출 가능) |
| `reloadModelRuntime(modelVersionKey)` | 특정 모델 버전만 갱신 |
| `updateEqpBinding(eqpId, modelVersionKey)` | 장비 바인딩 + 해당 모델 적재 |
| `removeEqpBinding(eqpId)` | 바인딩 제거, 참조 없어지면 runtime도 제거 |

---

## Part 2. Model Param Cache

### 파라미터 우선순위

워크플로우 액션은 장비/모델별 설정값을 파라미터로 조회한다.
동일한 `paramName`이 양쪽에 모두 존재하면 **EQP 파라미터 값**이 사용된다.
이를 통해 모델 공통 파라미터를 특정 장비에서만 개별 조정할 수 있다.

| 파라미터 종류 | 키 | 우선순위 |
|--------------|----|----------|
| Model 파라미터 | `modelVersionKey → paramName → paramValue` | 낮음 (기본값) |
| EQP 파라미터 | `eqpKey → paramName → paramValue` | 높음 (오버라이드) |

### 구조 다이어그램

```
BusinessModelParamCache
        │
        └── AtomicReference<BusinessModelParamSnapshot>
                │
                ├── modelParams: Map<modelVersionKey, Map<paramName, paramValue>>
                └── eqpParams:   Map<eqpKey,          Map<paramName, paramValue>>


조회 경로 (findParam)
        │
        ├── runtimeProvider.findEqpKeyByEqpId(eqpId)          → eqpKey
        ├── runtimeProvider.findModelVersionKeyByEqpId(eqpId)  → modelVersionKey
        │
        └── snapshot.resolveParam(eqpKey, modelVersionKey, paramName)
                │
                ├── [1] eqpParams.get(eqpKey).get(paramName)            → EQP 파라미터 우선
                └── [2] modelParams.get(modelVersionKey).get(paramName)  → Model 파라미터 fallback
```

### 핵심 클래스

| 클래스 | 위치 | 역할 |
|--------|------|------|
| `BusinessModelParamSnapshot` | `tc-business-domain` | 불변 스냅샷. model/eqp 파라미터 맵 보유 |
| `BusinessModelParamProvider` | `tc-business-core` | 파라미터 조회 포트 인터페이스 |
| `BusinessModelParamMutationPort` | `tc-business-core` | 파라미터 캐시 갱신 포트 인터페이스 |
| `BusinessModelParamCache` | `tc-business-db-adapter` | 위 두 포트의 구현체. `@Component` |

### 캐시 적재 흐름

```
@PostConstruct (BusinessModelParamCache.initialize())
        │
        └── reloadAll()
                │
                ├── [1] eqpStore.findAll(page) → 전체 TcEqp 목록 로드
                │
                ├── [2] 고유 modelVersionKey 수집
                │       └── modelParamStore.findAllByModelVersionKey(key, page) 반복
                │               → Map<modelVersionKey, Map<paramName, paramValue>>
                │
                └── [3] 각 eqp의 appliedParamVersion 기준 EQP 파라미터 로드
                        └── eqpParamStore.findAllByEqpKeyAndVersion(eqpKey, version) 반복
                                → Map<eqpKey, Map<paramName, paramValue>>
```

**EQP 파라미터 버전(`appliedParamVersion`)**:
`TcEqp.appliedParamVersion` 필드에 현재 적용 중인 버전이 저장된다.
이 버전의 파라미터만 캐시에 올린다. 버전 미지정 설비는 빈 맵으로 처리한다.

```
TcEqp.appliedParamVersion = "v1.2"
    └── eqpParamStore.findAllByEqpKeyAndVersion(eqpKey, "v1.2")
            → 해당 버전의 파라미터만 로드
```

### CAS 기반 부분 갱신

특정 모델 버전 또는 특정 설비의 파라미터만 갱신할 때,
기존 스냅샷의 다른 엔트리는 유지하고 해당 키만 교체한다. ([CAS 패턴](#cascas-기반-무중단-갱신) 참고)

```java
// reloadModelParams(modelVersionKey) 예시
while (true) {
    BusinessModelParamSnapshot base = snapshotRef.get();
    Map<Long, Map<String, String>> nextModelParams = base.copyAllModelParams(); // 기존 전체 복사
    nextModelParams.put(modelVersionKey, newParams);                            // 해당 키 교체

    BusinessModelParamSnapshot next =
        BusinessModelParamSnapshot.of(nextModelParams, base.copyAllEqpParams());

    if (snapshotRef.compareAndSet(base, next)) {
        return;
    }
}
```

### 갱신 트리거

| 트리거 | 메서드 | 설명 |
|--------|--------|------|
| 앱 기동 | `initialize()` + `reloadAll()` | 전체 model/eqp 파라미터 초기 적재 |
| 모델 파라미터 변경 | `reloadModelParams(modelVersionKey)` | 특정 모델 버전 파라미터만 갱신 |
| EQP 파라미터 변경 | `reloadEqpParams(eqpKey)` | 특정 설비 파라미터 갱신 (`appliedParamVersion` 기준 재로드) |

`reloadEqpParams(eqpKey)` 내부에서는 `eqpKey → eqpId` 역조회가 필요하다.
`eqpKeyBindings`를 선형 탐색하여 해결한다. (호출 빈도가 낮아 허용됨)

### 조회 API

```java
Optional<String>       findParam(String eqpId, String paramName);      // EQP 우선
Map<String, String>    findAllParams(String eqpId);                    // EQP + Model 병합
Map<String, String>    findModelParams(long modelVersionKey);
Map<String, String>    findEqpParams(String eqpId);
BusinessModelParamSnapshot currentParamSnapshot();
```

---

## Part 3. Work Cache

### 저장 방식 선택 근거

세 가지 방식을 분석하여 **인메모리 캐시(lazy, per-eqpId)**를 선택했다.

| 항목 | 인메모리 캐시 | Redis | DB (RDB) |
|------|--------------|-------|----------|
| 조회 속도 | 최고 (~μs) | 빠름 (~1ms) | 보통 (~5-20ms) |
| 다중 인스턴스 일관성 | ✅ stable 중 동일 인스턴스 처리 | ✅ 공유 상태 | ✅ 항상 최신 |
| lot/carrier/port 조회 | 선형 탐색 (설비당 10~20건) | 보조 인덱스 관리 필요 | SQL JOIN |
| Kafka rebalance 대응 | evictEqp → lazy reload | 영향 없음 | 영향 없음 |
| 구현 복잡도 | 낮음 | 높음 | 낮음 |

**선택 이유:**
1. **Mailbox 패턴**: eqpId 단위 순차 처리 보장 → 같은 eqpId 메시지는 동시 처리되지 않음
2. **Kafka partition**: Gateway는 `route_partition` 기준 고정 발행. Business는 자동 분배이나, stable 운영 중 동일 partition → 동일 인스턴스 소비. rebalance 시 lazy reload로 자동 복구.
3. **work 건수 소수**: 설비당 평균 10~20건, 최대 200건 미만 → 선형 탐색으로 충분
4. **기존 패턴 일관성**: `BusinessModelRuntimeCache`와 동일한 구조 유지

### 구조 다이어그램

```
BusinessWorkCache
        │
        └── ConcurrentHashMap<eqpId, EqpWorkContext>
                │
                └── EqpWorkContext
                        └── Map<workId, WorkEntry>
                                └── WorkEntry
                                        ├── TcWork
                                        ├── List<TcWorkLot>
                                        ├── List<TcWorkCarrier>
                                        ├── TcWorkControlJob     (nullable)
                                        └── List<TcWorkProcessJob>


조회 경로 (findByEqpIdAndLotId)
        │
        ├── [1] getOrLoad(eqpId) → computeIfAbsent → 필요 시 lazy load
        └── [2] EqpWorkContext.findByLotId(lotId) → 선형 탐색
```

### 핵심 클래스

| 클래스 | 위치 | 역할 |
|--------|------|------|
| `WorkEntry` | `tc-business-domain` | work 전체 컨텍스트 (불변 record) |
| `EqpWorkContext` | `tc-business-domain` | eqpId 단위 work 컨텍스트 맵 (불변 record) |
| `BusinessWorkProvider` | `tc-business-core` | work 조회 포트 인터페이스 |
| `BusinessWorkMutationPort` | `tc-business-core` | work 캐시 변경 포트 인터페이스 |
| `BusinessWorkCache` | `tc-business-db-adapter` | 위 두 포트의 구현체. `@Component` |

### WorkEntry 구성

```java
public record WorkEntry(
    TcWork work,                        // tc_work (PK: workKey, Unique: eqpKey + workId)
    List<TcWorkLot> lots,               // tc_work_lot (lotId, carrierId, chamberId ...)
    List<TcWorkCarrier> carriers,       // tc_work_carrier (carrierId, portId, qty ...)
    TcWorkControlJob controlJob,        // tc_work_controljob (nullable)
    List<TcWorkProcessJob> processJobs  // tc_work_processjob (recipeId, state ...)
)
```

- 컬렉션 필드는 생성 시 `List.copyOf()`로 불변화
- `controlJob`이 null이면 `processJobs`는 항상 빈 리스트
- `TcWorkParam`은 포함하지 않음 — Model Param Cache가 담당
- `TcWorkCarrierSlot`은 포함하지 않음 — 필요 시 별도 DB 조회

### Lazy Loading 흐름

```
findByEqpIdAndLotId("EQP-001", "LOT-123")
        │
        └── getOrLoad("EQP-001")
                │
                ├── [캐시 HIT]  EqpWorkContext 즉시 반환
                │
                └── [캐시 MISS] loadContext("EQP-001")
                        │
                        ├── runtimeProvider.findEqpKeyByEqpId("EQP-001") → eqpKey = 42
                        └── workStore.findAllByEqpKey(42, page) 반복 조회
                                └── 각 TcWork에 대해 loadWorkEntry(work)
                                        ├── workLotStore.findAllByWorkKey(workKey, page)
                                        ├── workCarrierStore.findAllByWorkKey(workKey, page)
                                        ├── workControlJobStore.findAllByWorkKey(workKey, limit=1)
                                        └── workProcessJobStore.findAllByControlJobKey(cjKey, page)
                        → EqpWorkContext 구성 후 캐시에 저장
```

`ConcurrentHashMap.computeIfAbsent` 사용 → 동일 eqpId 동시 요청 시 DB 로드는 한 번만 수행.

### 선형 탐색 조회

lot/carrier/port 기반 조회는 보조 인덱스 없이 선형 탐색으로 구현한다.

```
EqpWorkContext.findByLotId("LOT-123")
        └── entries.values() 순회
                → WorkEntry.hasLotId("LOT-123")
                        └── lots 순회 → lot.lotId().equals("LOT-123")
```

설비당 work 평균 10~20건, 최대 200건 미만 → 20건 × 5 lots = 100 비교 → 약 10μs (무시 가능)

### 캐시 동기화 흐름

**DB가 source of truth. DB 반영 후 반드시 캐시를 동기화한다.**

```
[work create/update]
        ├── [1] DB upsert (workStore.upsert, lotStore.upsert ...)
        └── [2] mutationPort.syncWork(eqpId, work, lots, carriers, controlJob, processJobs)
                        └── cache.computeIfPresent(eqpId, ctx -> ctx.put(new WorkEntry(...)))
                                ※ 캐시가 로드된 상태일 때만 갱신.
                                  미로드 상태면 다음 접근 시 전체 lazy reload로 일관성 확보.

[work delete]
        ├── [1] DB delete (workStore.deleteByWorkKey)
        └── [2] mutationPort.evictWork(eqpId, workKey)
                        └── cache.computeIfPresent(eqpId, ctx -> ctx.remove(workId))
```

동기화 실패 시 `evictEqp(eqpId)` 호출 → 다음 접근 시 DB 전체 reload로 자동 복구.

### EqpWorkContext 불변성

`EqpWorkContext`는 불변 record이므로 변경 연산은 항상 **새 인스턴스를 반환**한다.

```java
public EqpWorkContext put(WorkEntry entry) {
    Map<String, WorkEntry> next = new LinkedHashMap<>(entries);
    next.put(entry.work().workId(), entry);
    return new EqpWorkContext(next); // 새 인스턴스
}

public EqpWorkContext remove(String workId) {
    Map<String, WorkEntry> next = new LinkedHashMap<>(entries);
    next.remove(workId);
    return new EqpWorkContext(next);
}
```

`cache.computeIfPresent`는 반환값으로 캐시 엔트리를 교체하므로 불변 record와 자연스럽게 결합된다.

### Kafka Rebalance 대응

Business Core는 Kafka consumer group auto-assignment를 사용한다.
Rebalance 발생 시 인스턴스가 바뀌어도 Work 캐시는 **lazy reload로 자동 복구**된다.

```
Kafka Rebalance 발생
        └── 새 인스턴스가 해당 partition 소비 시작
                └── 첫 메시지 처리 → 캐시 MISS → DB reload → 정상 처리 재개
```

### evictEqp 사용 시나리오

| 시나리오 | 처리 |
|----------|------|
| Kafka rebalance | 별도 evict 불필요 (lazy reload로 자동 복구) |
| syncWork 예외 발생 | `evictEqp` 호출 → 다음 접근 시 전체 reload |
| 외부 batch로 work 일괄 변경 | `evictEqp` 호출 → 전체 재로드 |
| 운영 강제 재로드 | `evictEqp` 호출 |

### 조회 API

```java
Optional<WorkEntry> findByEqpId(String eqpId);
List<WorkEntry>     findAllByEqpId(String eqpId);

Optional<WorkEntry> findByEqpIdAndLotId(String eqpId, String lotId);
List<WorkEntry>     findAllByEqpIdAndLotId(String eqpId, String lotId);

Optional<WorkEntry> findByEqpIdAndCarrierId(String eqpId, String carrierId);
List<WorkEntry>     findAllByEqpIdAndCarrierId(String eqpId, String carrierId);

Optional<WorkEntry> findByEqpIdAndPortId(String eqpId, String portId);
List<WorkEntry>     findAllByEqpIdAndPortId(String eqpId, String portId);
```

---

## 전체 캐시 구조 비교

```
                    ┌─────────────────────────────────────────────────────┐
                    │              Business Core App                      │
                    │                                                     │
                    │  BusinessModelRuntimeProvider                       │
                    │      eqpId → modelVersionKey, TcModelRuntime        │
                    │      eqpId → eqpKey  (eqpKeyBindings)              │
                    │                                                     │
                    │  BusinessModelParamProvider                         │
                    │      eqpId + paramName → paramValue (EQP 우선)     │
                    │                                                     │
                    │  BusinessWorkProvider                               │
                    │      eqpId → WorkEntry (lot, carrier, ...)          │
                    └─────────────────────────────────────────────────────┘
                                            │
                    ┌───────────────────────┼────────────────────────────┐
                    │                       │                            │
         ┌──────────▼──────────┐  ┌────────▼─────────┐  ┌─────────────▼────────────┐
         │ ModelRuntimeCache   │  │ ModelParamCache   │  │ WorkCache                │
         │                     │  │                   │  │                          │
         │ AtomicReference     │  │ AtomicReference   │  │ ConcurrentHashMap        │
         │ <RuntimeSnapshot>   │  │ <ParamSnapshot>   │  │ <eqpId, EqpWorkContext>  │
         │                     │  │                   │  │                          │
         │ 기동 시 전체 로드   │  │ 기동 시 전체 로드 │  │ 첫 접근 시 lazy 로드     │
         │ CAS 교체            │  │ CAS 교체          │  │ computeIfAbsent          │
         └─────────────────────┘  └───────────────────┘  └──────────────────────────┘
                    │                       │                            │
                    └───────────────────────┴────────────────────────────┘
                                            │
                                       DB (MyBatis)
```

---

## 설정 프로퍼티

```properties
# config/tc-business.properties (예시)
tc.business.cache.load-on-startup=true       # 기동 시 캐시 자동 로드
tc.business.cache.fail-fast-on-startup=true  # 로드 실패 시 앱 기동 실패 처리
tc.business.cache.page-size=500              # DB 페이지 로드 단위 (모든 캐시 공통)
```

| 설정 | 적용 캐시 | 설명 |
|------|-----------|------|
| `load-on-startup` | Runtime, Param | 기동 시 자동 적재. Work Cache는 항상 lazy 로드. |
| `fail-fast-on-startup` | Runtime, Param | 적재 실패 시 앱 기동 실패 |
| `page-size` | 전체 | DB 페이지 단위 |

---

## 운영 포인트

| 항목 | 캐시 | 설명 |
|------|------|------|
| 기동 실패 | Runtime, Param | `fail-fast-on-startup=true`이면 캐시 적재 실패 → 앱 기동 실패 |
| 모델 미등록 | Runtime | 바인딩에 없는 eqpId → runtime 없음 → WORKFLOW_NOT_FOUND 처리 |
| eqpKey 미등록 | Param, Work | eqpId가 스냅샷에 없으면 WARN 후 empty 반환 |
| appliedParamVersion 미설정 | Param | 해당 설비의 EQP 파라미터는 빈 맵으로 처리 |
| work 정보 없음 | Work | 캐시 MISS + DB에도 없으면 빈 EqpWorkContext 캐싱 |
| Kafka rebalance | Work | lazy reload로 자동 복구. 별도 evict 불필요 |
| syncWork 실패 | Work | `evictEqp` 호출 후 다음 접근 시 DB reload |
| 파라미터 갱신 | Param | `reloadModelParams` 또는 `reloadEqpParams` 호출 |
| CAS 경합 | Runtime, Param | 재시도 루프로 안전하게 처리. 실운영 경합 빈도는 낮음 |
| runtime 제거 | Runtime | `removeEqpBinding` 후 참조 장비가 없으면 GC 대상 |
| 로그 | 전체 | INFO: 캐시 로드/갱신. WARN: 미등록 eqpId. DEBUG: 단건 동기화 |

---

## 관련 문서

- [Business: 워크플로우 매칭](03-workflow-matching.md) — messageName 인덱스 사용
- [Business: 3단계 큐 구조](02-three-stage-queue-structure.md) — Mailbox 패턴 (eqpId 단위 순차 처리 보장)
- [공통: DB 스타터 선택](../common/08-db-starter-selection.md) — 페이지 로드 기반 DB 조회 패턴
- [공통: 외부 설정 로딩](../common/03-external-config-loading.md) — `tc.business.cache.*` 설정
