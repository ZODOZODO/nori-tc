# U13 TODO: `tc.ui.events.gateway` 고정 `route_partition` 명시 발행 반영

## 목적
- 현재 `tc-ui-backend-app`은 실구현이 비어 있거나 초기 상태이므로, `U13`(Gateway 대상 UI 이벤트 고정 partition 발행) 구현은 보류합니다.
- 이 문서는 이후 `tc-ui-backend-app` 구현 시점에 바로 착수할 수 있도록 설계 결정사항과 구현 체크리스트를 남기는 용도입니다.

## 현재 상태 (보류 사유)
- `apps/tc-ui-backend-app` 디렉터리는 존재하지만, 실제 UI Backend 발행 로직이 아직 구현되지 않았습니다.
- 따라서 지금 단계에서 `tc.ui.events.gateway` 발행 코드를 억지로 추가하기보다, TODO 문서로 범위를 고정하고 추후 요청 시 구현하는 것이 안전합니다.

## U13에서 구현해야 할 핵심 목표
1. Gateway 대상 UI 이벤트는 `tc.ui.events.gateway` 토픽으로 발행한다.
2. 발행 시 Kafka key는 `eqpId`를 유지한다.
3. 라우팅은 Kafka key hash가 아니라 `tc_eqp.route_partition`를 조회하여 `ProducerRecord(topic, partition, key, payload)`로 명시 partition 발행한다.
4. `route_partition` 미배정/미존재/음수인 경우 발행을 차단하고 명확한 오류 로그를 남긴다.
5. Business 쪽 U12와 동일한 운영 규칙(고정 partition 라우팅)을 따른다.

## 설계 결정사항 (고정)
### 1) 토픽 분리
- Gateway 대상 UI 이벤트 토픽: `tc.ui.events.gateway`
- Business 대상 UI 이벤트 토픽: `tc.ui.events.business`

### 2) 라우팅 기준
- Gateway 대상 UI 이벤트 라우팅 SSOT: `tc_eqp.route_partition`
- `targetGatewayShardId`는 1차 구현에서 사용하지 않음
- fan-out 사용 안 함

### 3) 발행 규칙
- Kafka key: `eqpId` (추적/계약 검증/순서성 용도)
- Kafka partition: `tc_eqp.route_partition` 명시 지정

## 권장 구현 구조 (tc-ui-backend-app 구현 시)
### A. Core 포트 (권장)
- 예시 이름: `UiGatewayEqpRoutePartitionLookupPort`
- 책임:
  - `eqpId -> route_partition` 조회
  - 조회 실패/미배정은 `Optional.empty()`로 반환

### B. DB 어댑터 (권장)
- `TcEqpStore.findByEqpId(...)`를 사용해 `tc_eqp.route_partition` 조회
- U12에서 구현한 `BusinessEqpRoutePartitionDbAdapter`와 동일 패턴 권장

### C. Kafka 발행 어댑터 (핵심)
- 예시 역할:
  - UI 요청 payload -> `KafkaUiTaskMessage` 또는 Gateway UI 전용 wire contract 변환
  - `route_partition` 조회
  - `ProducerRecord(topic, partition, key, payload)` 생성
  - tracing header 추가
  - 발행/실패 로그 기록

## 구현 시 상세 체크리스트
### 1. 설정/토픽
- [ ] `tc-ui-backend-app` 설정에서 `tc.ui.events.gateway`, `tc.ui.events.business` 토픽명 사용
- [ ] Gateway 대상 이벤트는 `tc.ui.events.gateway`로만 발행하도록 분기

### 2. route_partition 조회
- [ ] `eqpId` 입력값 검증 (null/blank 차단)
- [ ] `tc_eqp` 조회
- [ ] `route_partition` 존재 여부 검증
- [ ] `route_partition >= 0` 검증

### 3. Kafka 발행
- [ ] `ProducerRecord<>(topic, routePartition, key, payload)` 사용
- [ ] tracing/eventType/source 헤더 추가
- [ ] 발행 전/후 `debug` 로그
- [ ] 발행 실패 `error` 로그

### 4. 로그 규칙 (사용자 요구 반영)
- [ ] 한글 위주 로그 메시지
- [ ] `info`: 초기화/정책 로딩/중요 상태 변경
- [ ] `debug`: route_partition 조회/라우팅 판단/발행 상세
- [ ] `error`: route_partition 조회 실패/발행 실패
- [ ] 로그 필수 식별자 포함: `eqpId`, `topic`, `partition`, `traceId`, `eventType`

### 5. 주석/인코딩 규칙 (사용자 요구 반영)
- [ ] 코드/주석 UTF-8 작성
- [ ] 클래스/메서드/주요 블록 한글 주석 상세 작성
- [ ] 한글 깨짐 발생 시 파일 재작성 전에 IDE/터미널 인코딩 점검

### 6. 테스트 (최소)
- [ ] `route_partition` 조회 성공 시 명시 partition 발행 검증
- [ ] `route_partition` 미배정(null) 시 발행 차단 검증
- [ ] `route_partition` 음수 시 발행 차단 검증
- [ ] Kafka key는 `eqpId` 유지되는지 검증
- [ ] `tc.ui.events.gateway` 토픽으로 발행되는지 검증

## 구현 참고 (이미 반영된 유사 패턴)
- Business EQP command 발행(U12)
  - `libs/business/adapter/tc-business-kafka-adapter/src/main/java/com/nori/tc/business/adapters/kafka/publish/BusinessEqpCommandKafkaPublisher.java`
  - `libs/business/tc-business-core/src/main/java/com/nori/tc/business/core/messaging/BusinessEqpRoutePartitionLookupPort.java`
  - `libs/business/adapter/tc-business-db-adapter/src/main/java/com/nori/tc/business/adapters/db/eqp/BusinessEqpRoutePartitionDbAdapter.java`

## 비고
- 이 TODO는 `U13` 보류 기록입니다.
- 실제 구현은 사용자 요청 시점에 진행합니다.
