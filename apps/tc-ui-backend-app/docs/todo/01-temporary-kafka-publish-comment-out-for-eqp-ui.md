# TODO 01. EQP UI 개발용 Kafka 발행 임시 비활성화

## 배경

- EQP page에서 설비 생성/수정/삭제를 수행할 때 UI Backend가 gateway/business로 Kafka 메시지를 발행합니다.
- 현재는 UI만 개발/테스트하려는 단계이므로, Kafka 발행과 응답 대기 때문에 timeout이 발생하면 화면 개발 진행이 막힙니다.
- 따라서 **당분간은 Kafka 발행을 임시로 주석 처리하고, 나중에 다시 필요할 때 원복**하는 방향으로 관리합니다.

## 임시 정책

- 목적: UI 단독 개발/테스트 동안 Kafka 발행과 응답 대기를 막아 timeout을 제거합니다.
- 범위: EQP create/update/delete 및 start/end 관련 runtime sync 호출
- 원칙: 실제 `publish()` 호출만 주석 처리하는 것으로 끝내지 말고, **응답 대기 로직도 함께 우회**해야 합니다.
- 이유: `publish()`만 주석 처리하면 `future.join()` 또는 polling 루프가 그대로 남아 다시 timeout이 발생합니다.

## 주석 처리 대상

### 1. EQP create/update/delete dual publish 우회

- 파일: `libs/ui/tc-ui-core/src/main/java/com/nori/tc/ui/core/service/EqpManagementService.java`
- 메서드: `awaitDualSuccess(...)`
- 현재 기준 위치: `416` 라인 부근

이 메서드는 아래 이벤트의 공통 발행 지점입니다.

- `EQP_CREATE`
- `EQP_UPDATE`
- `EQP_DELETE`
- `EQP_UPDATE_JARFILE`
- create/update/delete 실패 시 보상 처리에서 다시 호출되는 runtime sync

현재 실제 발행 코드는 아래 구간입니다.

```java
gatewayEventPublishPort.publish(message);
gatewayPublished = true;
businessEventPublishPort.publish(message);
```

임시 작업 시에는 위 발행 호출만 주석 처리하지 말고, 메서드 초반에 아래와 같이 **즉시 return** 하도록 우회해야 합니다.

```java
// TODO(donggeon): UI 단독 개발 단계에서는 Kafka 발행 및 응답 대기를 비활성화합니다.
log.warn("임시 우회: awaitDualSuccess Kafka 발행 생략. eventType={}, eqpId={}", eventType, snapshot.eqp().eqpId());
return;
```

정리하면, 이 메서드는 다음 이유로 임시 우회 대상입니다.

- create/update/delete 본 처리에서 gateway/business 발행을 수행합니다.
- jar 변경 시 추가 발행도 여기서 처리합니다.
- 보상 처리까지 같은 메서드를 타므로, 이 지점 하나를 우회하면 관련 timeout을 한 번에 차단할 수 있습니다.

### 2. EQP delete 선행 END 및 lifecycle 대기 우회

- 파일: `libs/ui/tc-ui-core/src/main/java/com/nori/tc/ui/core/service/EqpManagementService.java`
- 메서드: `awaitLifecycleSuccess(...)`
- 현재 기준 위치: `504` 라인 부근

이 메서드는 gateway 단일 발행 + Redis polling 대기 공통 지점입니다.

- `EQP_END`
- 삭제 시 선행 종료 처리

현재 실제 발행 코드는 아래 구간입니다.

```java
gatewayEventPublishPort.publish(message);
```

임시 작업 시에는 이 메서드도 메서드 초반에서 바로 빠지도록 우회해야 합니다.

```java
// TODO(donggeon): UI 단독 개발 단계에서는 lifecycle Kafka 발행 및 polling 대기를 비활성화합니다.
log.warn("임시 우회: awaitLifecycleSuccess Kafka 발행 생략. eventType={}, eqpId={}", eventType, snapshot.eqp().eqpId());
return;
```

이 메서드를 함께 우회해야 하는 이유는 다음과 같습니다.

- 설비 삭제 시 먼저 `EQP_END`를 시도합니다.
- 여기서 gateway 응답 대기 timeout이 나면 delete 테스트도 정상 진행되지 않습니다.

### 3. start/end API 직접 호출 시 controller 발행 우회

- 파일: `libs/ui/adapter/tc-ui-web-adapter/src/main/java/com/nori/tc/ui/adapters/web/controller/EqpController.java`
- 메서드: `publishLifecycleAndAccept(...)`
- 현재 기준 위치: `379` 라인 부근

이 메서드는 `/api/eqp/{eqpId}/start`, `/api/eqp/{eqpId}/end` 요청에서 직접 gateway 발행을 수행합니다.

현재 실제 발행 코드는 아래 구간입니다.

```java
asyncResultStorePort.registerPending(traceId, lifecycleTimeoutMs);
gatewayEventPublishPort.publish(message);
```

UI에서 start/end 버튼까지 함께 테스트할 예정이면, 이 메서드도 임시 우회하는 것이 안전합니다.

권장 임시 우회 방식:

```java
// TODO(donggeon): UI 단독 개발 단계에서는 start/end Kafka 발행을 비활성화합니다.
log.warn("임시 우회: lifecycle API Kafka 발행 생략. eventType={}, eqpId={}, traceId={}", eventType, eqpId, traceId);
return ResponseEntity.accepted()
        .body(ApiResponse.success(new AsyncAcceptResponse(traceId)));
```

주의할 점:

- `registerPending()`까지 유지하면 불필요한 Redis pending 데이터가 남을 수 있습니다.
- 따라서 controller 임시 우회 시에는 `registerPending()`도 함께 건너뛰는 편이 낫습니다.

## 현재 Kafka 주석 대상이 아닌 영역

아래 코드는 **현재 직접 Kafka를 발행하지 않으므로** 이번 임시 작업의 주석 대상이 아닙니다.

### 1. EQP 파라미터 편집

- 파일: `libs/ui/adapter/tc-ui-web-adapter/src/main/java/com/nori/tc/ui/adapters/web/controller/EqpParamController.java`
- 확인 메서드:
  - `checkout(...)`
  - `saveEditParams(...)`
  - `undoCheckout(...)`
  - `checkin(...)`

현재는 DB command port 호출만 수행하며 gateway/business 발행은 없습니다.

### 2. 모델 정보 수정/생성/삭제

- 파일: `libs/ui/adapter/tc-ui-web-adapter/src/main/java/com/nori/tc/ui/adapters/web/controller/ModelController.java`
- 확인 메서드:
  - `updateModelInfo(...)`
  - `create(...)`
  - `update(...)`
  - `delete(...)`

현재는 model command/db port 호출만 수행하며 gateway/business 발행은 없습니다.

## 원복 기준

- UI 개발/테스트 단계가 끝나고 실제 gateway/business 연동 검증이 필요해질 때
- 사용자가 다시 Kafka 발행 복구를 요청할 때

원복 시 해야 할 일:

1. `awaitDualSuccess(...)`의 임시 `return` 제거
2. `awaitLifecycleSuccess(...)`의 임시 `return` 제거
3. 필요 시 `publishLifecycleAndAccept(...)`의 임시 `return ResponseEntity.accepted(...)` 제거
4. 원래의 Kafka 발행 + 응답 대기 로직 복구
5. create/update/delete/start/end 시나리오 재검증

## 작업 메모

- 이번 TODO의 핵심은 `publish()` 한 줄 주석이 아니라, **발행 + 대기 흐름 전체를 임시 비활성화**하는 것입니다.
- 실제 코드 반영 시에는 임시 우회임이 드러나도록 `TODO(donggeon)` 주석을 남깁니다.
- 향후 복구 시에는 이 문서를 기준으로 세 지점만 되돌리면 됩니다.
