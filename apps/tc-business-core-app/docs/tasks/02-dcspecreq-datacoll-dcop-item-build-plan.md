> 작성일: 2026-03-16

# 02. DCSPECREQ_REP / DATACOLL / DCOP Item 반영 작업 계획

## 참조 문서

- 기능 가이드: `docs/Architecture/business/09-dcspecreq-datacoll-dcop-item-guide.md`
- 설계 문서: `apps/tc-business-core-app/docs/design/02-dcspecreq-datacoll-dcop-item-design.md`
- DCOP Item 도메인: `libs/db/tc-db-domain/src/main/java/com/nori/tc/db/domain/model/TcModelDcopItem.java`
- Collection Rule enum: `libs/db/tc-db-domain/src/main/java/com/nori/tc/db/domain/common/model/DcopCollectionRule.java`
- DCOP Item Store: `libs/db/tc-db-core/src/main/java/com/nori/tc/db/core/model/store/TcModelDcopItemStore.java`
- 기존 transform 구현 참조(1): `libs/business/tc-business-core/src/main/java/com/nori/tc/business/core/workflow/internal/support/BusinessActionDataIndexHybridResolver.java`
- 기존 transform 구현 참조(2): `libs/business/tc-business-core/src/main/java/com/nori/tc/business/core/workflow/internal/matching/BusinessWorkflowFilterEvaluator.java`
- 기존 Redis 어댑터 참조: `libs/business/adapter/tc-business-redis-adapter/src/main/java/com/nori/tc/business/adapters/redis/dlq/RedisBusinessDlqPublisher.java`
- 기존 TCAction 참조: `libs/business/tc-business-core/src/main/java/com/nori/tc/business/core/workflow/api/action/` 하위 구현 파일들

---

## 진행 원칙

- 본 문서는 DCSPECREQ_REP → DATACOLL → DCOP 수집 기능을 실제 구현으로 옮기기 위한 작업 계획입니다.
- 구현 순서: `BusinessTransformSupport 추출 → Redis 포트/어댑터 → DCSPECREQ_REP 처리 → COLLECT_DCDATA TCAction → DATACOLL TCAction → 문서 갱신 → 테스트`
- DB 스키마는 변경하지 않습니다. `tc_model_dcop_item` 기존 컬럼을 그대로 사용합니다.
- 구현 완료 전까지는 체크박스를 완료 처리하지 않습니다.
- T1(BusinessTransformSupport)은 T4(COLLECT_DCDATA TCAction)의 Calculation Rule에서 의존하므로 반드시 먼저 완료합니다.
- T2(Redis 포트/어댑터)는 T3(DCSPECREQ_REP 처리), T4(COLLECT_DCDATA), T5(DATACOLL TCAction) 모두에서 의존합니다.

---

## 작업 범위

| 작업 ID | 작업 항목 | 주요 대상 |
|---|---|---|
| T1 | BusinessTransformSupport 공통 유틸 추출 | tc-business-core support 패키지 |
| T2 | Redis 포트/어댑터 구현 | tc-business-core port, tc-business-redis-adapter |
| T3 | DCSPECREQ_REP 수신 처리 | TCAction 또는 이벤트 핸들러 |
| T4 | COLLECT_DCDATA TCAction 구현 | CollectDcdataTcAction |
| T5 | DATACOLL TCAction 구현 | DatacollTcAction |
| T6 | 문서 갱신 | 09번 가이드, README |
| T7 | 테스트 및 acceptance 검증 | 단위 테스트, 통합 시나리오 |

---

## T1. BusinessTransformSupport 공통 유틸 추출

### 목적

`BusinessActionDataIndexHybridResolver`와 `BusinessWorkflowFilterEvaluator`에 동일하게 중복 구현된
`TransformSpec` record와 `applyTransform()` 메서드를 공통 유틸로 추출합니다.
이를 통해 DCOP Calculation Rule에서도 동일한 함수를 재사용합니다.

### 사전 확인 사항

- `BusinessActionDataIndexHybridResolver`의 `TransformSpec` record와 `applyTransform()` 구현 전체를 읽습니다.
- `BusinessWorkflowFilterEvaluator`의 `TransformSpec` record와 `applyTransform()` 구현 전체를 읽습니다.
- 두 구현의 차이점을 확인합니다. (알려진 차이: `transformSubstring()`에서 `FilterEvaluator` 쪽만 `args.isEmpty()` 방어 코드 있음)

### 작업 내용

#### T1-1. BusinessTransformSupport 신규 생성

- [ ] 파일 생성: `libs/business/tc-business-core/src/main/java/com/nori/tc/business/core/workflow/internal/support/BusinessTransformSupport.java`
- [ ] `TransformSpec` record를 `public record`로 선언 (name: String, args: List<Object>)
- [ ] 생성자 검증: name normalize 후 null 체크, args null이면 `List.of()`, `List.copyOf()` 적용
- [ ] `fromCompactText(String compactText)` static 메서드 구현 (compact text → TransformSpec 파싱)
- [ ] `stripQuotes(String value)` private static 헬퍼 구현
- [ ] `applyTransform(Object value, TransformSpec transform)` public static 메서드 구현
  - `split`, `trim`, `substring`, `toint`, `tolong`, `length`, `add`, `sub`, `lower`, `upper` 지원
- [ ] `transformSplit(Object value, List<Object> args)` 헬퍼 구현
- [ ] `transformSubstring(Object value, List<Object> args)` 헬퍼 구현 (**FilterEvaluator 버전 기준으로 통일**: `args.isEmpty()` 방어 코드 포함)
- [ ] `transformAddSub(Object value, List<Object> args, boolean add)` 헬퍼 구현
- [ ] `toBigDecimal(Object value)` 헬퍼 구현
- [ ] `toIntOrDefault(List<Object> args, int index, int defaultValue)` 헬퍼 구현
- [ ] 클래스/메서드 주석 작성 (이 유틸의 역할, transform 함수 목록, 재사용 대상 명시)

#### T1-2. BusinessActionDataIndexHybridResolver 수정

- [ ] 내부 `TransformSpec` record 제거
- [ ] `BusinessTransformSupport.TransformSpec` import 추가
- [ ] `fromCompactText()` 호출을 `BusinessTransformSupport.TransformSpec.fromCompactText()`로 변경
- [ ] `applyTransform()` 호출을 `BusinessTransformSupport.applyTransform()`으로 변경
- [ ] 중복된 헬퍼 메서드들 (`transformSplit`, `transformSubstring`, `transformAddSub`, `toBigDecimal`, `toIntOrDefault`, `stripQuotes`) 제거
- [ ] 컴파일 에러 없는지 확인

#### T1-3. BusinessWorkflowFilterEvaluator 수정

- [ ] 내부 `TransformSpec` record 제거
- [ ] `BusinessTransformSupport.TransformSpec` import 추가
- [ ] `fromCompactText()` 호출을 `BusinessTransformSupport.TransformSpec.fromCompactText()`로 변경
- [ ] `applyTransform()` 호출을 `BusinessTransformSupport.applyTransform()`으로 변경
- [ ] 중복된 헬퍼 메서드들 제거
- [ ] 컴파일 에러 없는지 확인

### T1 검증

- [ ] `BusinessActionDataIndexHybridResolverTest` 기존 테스트 전체 통과 확인
- [ ] `BusinessWorkflowFilterEvaluatorTest` 기존 테스트 전체 통과 확인
- [ ] `BusinessTransformSupport`에서 transform 함수 10개 각각 단위 테스트 작성 및 통과 확인
- [ ] `transformSubstring`의 `args.isEmpty()` 방어 케이스 테스트 포함

---

## T2. Redis 포트/어댑터 구현

### 목적

DCOP 수집 상태(dcspecValue + collectionState)를 저장/조회/갱신/삭제하는 Redis 저장소를
포트/어댑터 패턴으로 구현합니다.

### 사전 확인 사항

- `libs/business/adapter/tc-business-redis-adapter/src/main/java/com/nori/tc/business/adapters/redis/dlq/RedisBusinessDlqPublisher.java` 를 읽어 기존 Redis 어댑터 패턴을 파악합니다.
- `libs/business/adapter/tc-business-redis-adapter/src/main/java/com/nori/tc/business/adapters/redis/dlq/BusinessRedisProperties.java` 를 읽어 properties 패턴을 파악합니다.
- `libs/db/starter/tc-db-redis-starter/src/main/java/com/nori/tc/db/starter/redis/TcRedisCrudRepository.java` 를 읽어 CRUD 인터페이스를 파악합니다.
- `apps/tc-business-core-app/config/tc-business-core.properties` 를 읽어 설정 파일 패턴을 파악합니다.

### 작업 내용

#### T2-1. DatacollState 도메인 객체 정의

- [ ] 파일 생성: `libs/business/tc-business-core/src/main/java/com/nori/tc/business/domain/datacoll/DatacollState.java`
  - `dcspecValue: Map<String, String>` (key=dcopItemName, value=수집 결과. 초기값 "")
  - `collectionState: Map<String, ItemCollectionState>` (key=dcopItemName)
- [ ] `ItemCollectionState` record 정의 (rule, count, sum, value 필드 포함)
  - FIRST/LAST/MIN/MAX: `value` 필드 사용
  - AVERAGE: `count` + `sum` 필드 사용
- [ ] JSON 직렬화/역직렬화 가능하도록 설계 (`@JsonProperty` 등)

#### T2-2. DatacollStatePort 포트 인터페이스 정의

- [ ] 파일 생성: `libs/business/tc-business-core/src/main/java/com/nori/tc/business/core/datacoll/DatacollStatePort.java`
- [ ] `save(String eqpId, String correlationId, DatacollState state)` 메서드 정의
- [ ] `findByKey(String eqpId, String correlationId): Optional<DatacollState>` 메서드 정의
- [ ] `update(String eqpId, String correlationId, DatacollState state)` 메서드 정의
- [ ] `delete(String eqpId, String correlationId)` 메서드 정의
- [ ] 인터페이스 주석 작성

#### T2-3. DatacollRedisAdapter 구현

- [ ] 파일 생성: `libs/business/adapter/tc-business-redis-adapter/src/main/java/com/nori/tc/business/adapters/redis/datacoll/DatacollRedisAdapter.java`
- [ ] Redis key prefix: `tc:business:core:datacoll:` 상수 정의
- [ ] key 포맷: `tc:business:core:datacoll:{eqpId}:{correlationId}`
- [ ] `TcRedisCrudRepository` 주입
- [ ] `DatacollRedisProperties` 주입 (TTL 설정용)
- [ ] `save()`: JSON 직렬화 후 Redis에 TTL과 함께 저장
- [ ] `findByKey()`: Redis에서 조회 → JSON 역직렬화 → `Optional<DatacollState>` 반환. key 없으면 `Optional.empty()`
- [ ] `update()`: save()와 동일 (덮어쓰기, TTL 갱신)
- [ ] `delete()`: Redis key 즉시 삭제
- [ ] 각 메서드에 DEBUG/ERROR 로그 추가
- [ ] `@Component` 선언, `DatacollStatePort` implement

#### T2-4. DatacollRedisProperties 구현

- [ ] 파일 생성: `libs/business/adapter/tc-business-redis-adapter/src/main/java/com/nori/tc/business/adapters/redis/datacoll/DatacollRedisProperties.java`
- [ ] `@ConfigurationProperties(prefix = "tc.business.core.redis")` 적용
- [ ] `datacollTtlSeconds` 필드 (기본값: 86400 = 24시간)
- [ ] 기존 `BusinessRedisProperties` 패턴 참조

#### T2-5. 설정 파일 추가

- [ ] `apps/tc-business-core-app/config/tc-business-core.properties`에 추가:
  ```properties
  # DATACOLL 수집 상태 Redis TTL (초). lot 처리 최대 시간보다 여유롭게 설정.
  tc.business.core.redis.datacoll-ttl-seconds=86400
  ```

### T2 검증

- [ ] `DatacollRedisAdapter` 단위 테스트 작성 (save/find/update/delete 각각)
- [ ] key 없을 때 `Optional.empty()` 반환 확인
- [ ] TTL이 올바르게 적용되는지 확인

---

## T3. DCSPECREQ_REP 수신 처리

### 목적

MES로부터 `DCSPECREQ_REP` 이벤트를 수신했을 때, `dcspecValue` key 목록을 Redis에 저장합니다.

### 사전 확인 사항

- 기존 TCAction이 어떤 패키지에 위치하는지, `@TcAction` 어노테이션 구조를 파악합니다.
  (`libs/business/tc-business-core/src/main/java/com/nori/tc/business/core/workflow/api/action/` 하위)
- `BusinessWorkflowActionContext`의 구조를 확인합니다.
  eqpId와 correlationId를 어떻게 추출하는지 파악합니다.
- 기존 MES 이벤트를 처리하는 TCAction 예시 파일을 참조합니다.

### 작업 내용

#### T3-1. DcspecreqRepTcAction 구현

- [ ] 파일 생성: `libs/business/tc-business-core/src/main/java/com/nori/tc/business/core/workflow/action/DcspecreqRepTcAction.java`
  (기존 TCAction 위치 패턴 참조)
- [ ] `@TcAction` 어노테이션 적용 (actionName = `"DCSPECREQ_REP"` 또는 해당 이벤트 처리 방식 확인 후 결정)
- [ ] `DatacollStatePort` 주입
- [ ] Kafka payload에서 `eqpId` 추출 (data.eqpId)
- [ ] Kafka payload에서 `correlationId` 추출 (metadata.correlationId)
- [ ] Kafka payload에서 `dcspecValue` 추출 (data.dcspecValue → `Map<String, String>`)
- [ ] `DatacollState` 초기화:
  - `dcspecValue`: 수신한 key 목록 그대로, value는 모두 `""`
  - `collectionState`: 빈 Map
- [ ] `DatacollStatePort.save()` 호출
- [ ] INFO 로그: `eqpId`, `correlationId`, dcspecValue key 목록 기록
- [ ] 이미 동일 key가 Redis에 있는 경우 덮어쓰기 (warn 로그 후 갱신)

### T3 검증

- [ ] `DCSPECREQ_REP` 처리 후 Redis에 `DatacollState` 저장 확인
- [ ] `dcspecValue` key 목록이 올바르게 저장되는지 확인
- [ ] `correlationId`가 null인 경우 처리 확인

---

## T4. COLLECT_DCDATA TCAction 구현

### 목적

workflow에 명시적으로 배치하는 `COLLECT_DCDATA` action을 구현합니다.
이 action이 실행될 때 현재 workflowName과 일치하는 DCOP Item의 값을 수집하고 Redis `collectionState`를 갱신합니다.
이벤트 발생마다 자동 수집하지 않으며, workflow 정의에 이 action이 있을 때만 수집이 실행됩니다.

### 사전 확인 사항

- 기존 TCAction이 어떤 패키지에 위치하는지, `@TcAction` 어노테이션 구조를 파악합니다.
- `BusinessWorkflowActionContext`에서 현재 `workflowName`을 어떻게 추출하는지 파악합니다.
- `TcModelDcopItem.java`의 전체 필드 구조를 읽습니다.
- `DcopCollectionRule.java`의 enum 값을 확인합니다.
- `TcModelDcopItemStore.java`의 조회 메서드를 확인합니다.
- `BusinessTransformSupport.java`가 T1에서 완성되어 있어야 합니다.
- `DatacollStatePort`가 T2에서 완성되어 있어야 합니다.

### 작업 내용

#### T4-1. CollectDcdataTcAction 구현

- [ ] 파일 생성: `libs/business/tc-business-core/src/main/java/com/nori/tc/business/core/workflow/action/CollectDcdataTcAction.java`
- [ ] `@TcAction` 어노테이션 적용 (actionName = `"COLLECT_DCDATA"`)
- [ ] `DatacollStatePort` 주입
- [ ] `TcModelDcopItemStore` 주입

##### T4-1-1. 수집 실행 메서드 구현

- [ ] context에서 `eqpId` 추출
- [ ] context에서 `correlationId` 추출
- [ ] context에서 현재 `workflowName` 추출
- [ ] `DatacollStatePort.findByKey(eqpId, correlationId)` 호출
  - DatacollState 없으면: warn 로그 (`DatacollState not found. eqpId={}, correlationId={}`) 후 return
    (DCSPECREQ_REP 없이 진행 중인 lot. 수집 건너뜀)
- [ ] `modelVersionKey` 기준으로 `workflowName`에 해당하는 DCOP Item 목록 조회
  (workflowName이 일치하는 항목만)
- [ ] DCOP Item이 없으면 debug 로그 후 return
- [ ] 각 DCOP Item에 대해:
  - `variableId`로 현재 이벤트 payload에서 값 추출
    (추출 방법은 기존 TCAction에서 payload 접근하는 방식 참조)
  - 값이 없으면 해당 Item 건너뜀, debug 로그
  - Collection Rule에 따라 `DatacollState.collectionState` 갱신:
    - FIRST: 해당 key 없을 때만 저장
    - LAST: 항상 덮어쓰기
    - AVERAGE: `count += 1`, `sum += 수집값` (BigDecimal 변환)
    - MIN: 없으면 저장, 수집값 < 현재 최솟값일 때만 갱신
    - MAX: 없으면 저장, 수집값 > 현재 최댓값일 때만 갱신
- [ ] `DatacollStatePort.update()` 호출하여 Redis 갱신
- [ ] DEBUG 로그: `eqpId={}, workflowName={}, collectedItems={}`

##### T4-1-2. 최종값 결정 메서드 구현 (DATACOLL에서 공유 사용)

```
resolveFinalValues(DatacollState state, List<TcModelDcopItem> dcopItems): Map<String, String>
```

이 메서드는 `DATACOLL` TCAction(T5)에서도 호출합니다. `CollectDcdataTcAction` 또는 별도 헬퍼 클래스에 구현하고 공유합니다.

- [ ] Order Rule 순서 (`orderRule ASC, dcopItemName ASC`)로 dcopItems 정렬
- [ ] 각 DCOP Item에 대해:
  1. `collectionState`에서 Collection Rule 최종값 결정
     - FIRST/LAST/MIN/MAX: `value` 그대로
     - AVERAGE: `sum / count` (count=0이면 `""`)
  2. `calculationRule`이 null이 아니면 `BusinessTransformSupport.TransformSpec.fromCompactText(calculationRule)`
     파싱 후 `BusinessTransformSupport.applyTransform()` 적용
     (실패 시 이전 값 유지, warn 로그)
  3. 결과를 `Map<dcopItemName, finalValue>`에 저장
- [ ] collectionState에 없는 dcopItemName은 `""` 처리, warn 로그

### T4 검증

- [ ] FIRST Rule: `COLLECT_DCDATA` 두 번 실행 시 첫 번째 값만 저장되는지 확인
- [ ] LAST Rule: `COLLECT_DCDATA` 두 번 실행 시 마지막 값으로 갱신되는지 확인
- [ ] AVERAGE Rule: `COLLECT_DCDATA` 3회 실행 후 `sum/count` 평균값 정확성 확인
- [ ] MIN Rule: 최솟값이 올바르게 갱신되는지 확인
- [ ] MAX Rule: 최댓값이 올바르게 갱신되는지 확인
- [ ] calculationRule 적용 확인 (예: `add(10)` → 수집값+10)
- [ ] Order Rule 정렬 순서 확인 (`orderRule ASC, dcopItemName ASC`)
- [ ] collectionState 없는 항목이 `""` 반환되는지 확인
- [ ] DatacollState 없을 때 warn 로그 후 정상 종료 확인

---

## T5. DATACOLL TCAction 구현

### 목적

workflow action type이 `DATACOLL`일 때 실행됩니다.
Redis에서 수집 상태를 읽어 최종값으로 DATACOLL 메시지를 조립하고 MES에 보고한 뒤 Redis를 정리합니다.

### 사전 확인 사항

- 기존 TCAction에서 MES로 Kafka 메시지를 발행하는 패턴을 파악합니다.
- `BusinessMdfMessageComposer`의 KAFKA output 메시지 조립 방식을 파악합니다.
- `DcopCollectionEngine`이 T4에서 완성되어 있어야 합니다.
- `DatacollStatePort`가 T2에서 완성되어 있어야 합니다.

### 작업 내용

#### T5-1. DatacollTcAction 구현

- [ ] 파일 생성: `libs/business/tc-business-core/src/main/java/com/nori/tc/business/core/workflow/action/DatacollTcAction.java`
- [ ] `@TcAction` 어노테이션 적용 (actionName = `"DATACOLL"`)
- [ ] `DatacollStatePort` 주입
- [ ] `DcopCollectionEngine` 주입
- [ ] `TcModelDcopItemStore` 주입

##### T5-1-1. 실행 메서드 구현

실행 흐름:
1. context에서 `eqpId` 추출
2. context에서 `correlationId` 추출 (metadata.correlationId)
3. `DatacollStatePort.findByKey(eqpId, correlationId)` 호출
4. `DatacollState`가 없으면: WARN 로그 (`DatacollState not found for eqpId={}, correlationId={}`) 후 return
5. `modelRuntime`에서 `modelVersionKey` 조회 → `TcModelDcopItemStore.findAllByModelVersionKey()` 호출
6. `DcopCollectionEngine.resolveFinalValues(state, dcopItems)` 호출 → `Map<dcopItemName, finalValue>`
7. `state.dcspecValue()` 기준으로 key를 순회하며 최종값으로 채우기
   - dcopItemName이 매핑되는 항목: `finalValues.get(dcopItemName)` 값 사용
   - 매핑 없는 항목: `""` 그대로
8. DATACOLL 메시지 조립 (MDF 사용 또는 직접 JSON 구성 - 기존 패턴 확인 후 결정)
9. MES로 Kafka 발행
10. INFO 로그: `DATACOLL reported to MES. eqpId={}, correlationId={}, itemCount={}`
11. `DatacollStatePort.delete(eqpId, correlationId)` 호출
12. Redis 삭제 실패 시: ERROR 로그만 남기고 전파하지 않음 (TTL로 최종 정리)

### T5 검증

- [ ] DATACOLL action 실행 후 MES로 Kafka 메시지 발행 확인
- [ ] `dcspecValue`에 수집 결과가 채워지는지 확인
- [ ] 매핑 없는 항목은 `""` 확인
- [ ] DatacollState 없을 때 warn 로그 후 정상 종료 확인
- [ ] 실행 완료 후 Redis key 삭제 확인

---

## T6. 문서 갱신

### 목적

구현 결과를 반영하여 기능 가이드와 README를 최신화합니다.

### 작업 내용

#### T6-1. 09번 가이드 검토 및 갱신

- [ ] `docs/Architecture/business/09-dcspecreq-datacoll-dcop-item-guide.md` 내용이 구현과 일치하는지 확인
- [ ] TCAction 구현 완료 시 "구현 예정(TODO)" 문구 제거
- [ ] 실제 Redis key 패턴, 포트/어댑터 경로가 문서와 일치하는지 확인

#### T6-2. tc-business-core-app README 갱신

- [ ] `apps/tc-business-core-app/docs/README.md`에 설계 문서(02-design)와 작업 계획(02-tasks) 링크 추가

### T6 검증

- [ ] 문서의 흐름 다이어그램과 실제 구현 흐름이 일치하는지 확인
- [ ] 관련 문서 링크가 모두 유효한지 확인

---

## T7. 테스트 및 acceptance 검증

### 목적

전체 기능이 설계 문서의 요구사항에 맞게 구현되었는지 확인합니다.

### 작업 내용

#### T7-1. 단위 테스트

- [ ] `BusinessTransformSupportTest`: 10개 함수 각각 정상/경계/null 케이스
- [ ] `DatacollRedisAdapterTest`: save/find/update/delete, key 없을 때 Optional.empty()
- [ ] `DcopCollectionEngineTest`:
  - Collection Rule별 (FIRST/LAST/AVERAGE/MIN/MAX) 단위 테스트
  - Calculation Rule 적용 테스트
  - Order Rule 정렬 순서 테스트
  - collectionState 없는 항목 처리 테스트
- [ ] `DatacollTcActionTest`:
  - 정상 흐름 (Redis 있음 → 발행 → 삭제)
  - DatacollState 없을 때 warn 로그 후 정상 종료
  - Redis 삭제 실패 시 발행은 완료된 상태 유지

#### T7-2. 기존 테스트 회귀 확인

- [ ] `BusinessActionDataIndexHybridResolverTest` 전체 통과
- [ ] `BusinessWorkflowFilterEvaluatorTest` 전체 통과

#### T7-3. acceptance 기준

- [ ] DCSPECREQ_REP 수신 → Redis에 DatacollState 저장
- [ ] workflowName 매칭 → collectionState 올바르게 누적
- [ ] AVERAGE: `sum/count` 결과가 정확한지 확인
- [ ] Calculation Rule이 transform 함수와 동일한 동작인지 확인
- [ ] Order Rule 순서 (`orderRule ASC, dcopItemName ASC`) 준수 확인
- [ ] DATACOLL 보고 후 Redis key 삭제 확인
- [ ] BusinessTransformSupport 추출 후 기존 테스트 전체 통과

---

## 추가 확인 필요 사항

- `DCSPECREQ_REP` 이벤트를 기존 TCAction 방식으로 처리할지, 별도 이벤트 핸들러로 처리할지 기존 코드 패턴 확인 후 결정 필요
- DATACOLL 메시지 조립 시 MDF를 사용할지 직접 JSON을 구성할지 기존 MES 발행 패턴 확인 후 결정 필요
- `collectOnWorkflowEvent()`를 어느 시점에서 호출할지 (workflow 실행 파이프라인 어느 위치에 hook으로 추가할지) 기존 action 실행 흐름 확인 후 결정 필요
- `variableId`로 값을 조회할 때 `availableValues`의 소스가 무엇인지 (Kafka payload data 블록인지, 런타임 context 변수인지) 확인 필요
- `modelVersionKey`를 `DatacollTcAction`에서 어떻게 조회하는지 `TcModelRuntime` 구조 확인 필요
