# 12. 고정 Partition 라우팅 (Fixed Partition Routing)

## 개요

Gateway는 여러 인스턴스를 동시에 운영할 수 있습니다.
각 설비는 TCP 연결과 상태가 특정 Gateway 인스턴스에 묶여 있기 때문에,
**명령과 UI 이벤트가 항상 올바른 Gateway 인스턴스에 도달**해야 합니다.

이를 위해 **DB의 `tc_eqp.route_partition`을 라우팅 기준(SSOT)으로 사용**하고,
Kafka 메시지를 발행할 때 해당 값으로 partition을 명시 지정합니다.
수신 측 Gateway는 `ASSIGN` 방식으로 자신이 소유한 partition만 구독합니다.

이 구조를 **고정 Partition 라우팅**이라고 부릅니다.

---

## 왜 이 구조가 필요한가?

### 문제: Hash 기반 자동 분배는 상태 일관성을 보장하지 못함

```
EQP-001이 Gateway-1에 TCP 연결되어 있는 상태에서:

Kafka key hash 자동 분배 방식:
  "EQP-001" 키 해시 → Partition 2 → Gateway-2 수신
  Gateway-2에는 EQP-001의 TCP 연결이 없음
  → 명령을 설비에 전달할 수 없음!

Gateway 증설 시 partition 수가 변경되면:
  hash(EQP-001) % 6 = Partition 0  (6개일 때)
  hash(EQP-001) % 8 = Partition 2  (8개로 늘리면)
  → 기존 설비의 라우팅이 자동으로 바뀌어 버림!
```

### 해결: DB 고정값 기반 명시 라우팅

```
DB에 EQP-001의 route_partition=0 저장
  → 명령 발행 시 항상 Partition 0으로 명시 지정
  → Gateway-1(owned: 0,1)이 수신
  → EQP-001의 TCP 연결이 있는 Gateway에만 도달

Gateway 증설 시:
  기존 설비의 route_partition은 변경하지 않음
  신규 설비만 신규 partition 번호 배정
  → 기존 라우팅 완전히 무영향
```

---

## 전체 라우팅 흐름

```
┌─────────────────────────────────────────────────────────────────────┐
│                        전체 라우팅 흐름                              │
│                                                                     │
│  UI Backend                                                         │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │ 1. eqpId로 tc_eqp.route_partition 조회                       │   │
│  │    UiEqpRoutePartitionDbAdapter.findRoutePartition(eqpId)    │   │
│  │    → routePartition = 0                                      │   │
│  │                                                              │   │
│  │ 2. 명시 partition 지정 발행                                   │   │
│  │    ProducerRecord(topic, routePartition=0, eqpId, message)   │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                          │                                          │
│                          ↓                                          │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │ Kafka: tc.ui.events.gateway (또는 tc.eqp.commands)           │   │
│  │                                                              │   │
│  │  Partition 0 │ Partition 1 │ Partition 2 │ ...              │   │
│  │  [EQP-001]   │ [EQP-002]   │ [EQP-004]   │                  │   │
│  │  [EQP-003]   │             │ [EQP-005]   │                  │   │
│  └──────┬───────────────────────────────────────────────────────┘   │
│         │                                                           │
│    owned=0,1                                                        │
│         ↓                                                           │
│  ┌──────────────────────────────────────────────────┐              │
│  │ Gateway-1 (ASSIGN 모드, owned-partitions=0,1)    │              │
│  │                                                  │              │
│  │ 3. route_partition 수신측 정합성 검증             │              │
│  │    record.partition() == equipmentInfo.routePartition?         │
│  │    → 일치하면 처리 / 불일치하면 REJECTED          │              │
│  │                                                  │              │
│  │ 4. 처리 위임                                      │              │
│  │    EQP-001 TCP 연결 → 설비에 전달                 │              │
│  └──────────────────────────────────────────────────┘              │
└─────────────────────────────────────────────────────────────────────┘
```

---

## DB 구조: tc_eqp.route_partition

`route_partition`은 Gateway 대상 토픽 라우팅의 **단일 기준(SSOT)**입니다.

```
tc_eqp 테이블:

eqp_id      | comm_mode | route_partition | enabled
------------|-----------|-----------------|--------
EQP-001     | ACTIVE    | 0               | true
EQP-002     | ACTIVE    | 1               | true
EQP-003     | PASSIVE   | 0               | true    ← PASSIVE는 listener-group과 동일 partition
EQP-004     | ACTIVE    | 2               | true
EQP-005     | PASSIVE   | 2               | true    ← 같은 bindIp+port 공유 → 동일 route_partition 강제
EQP-006     | ACTIVE    | null            | false   ← 미배정 상태 (발행 차단)
```

**설계 결정:**
- `route_partition`은 `Integer` 타입, null 허용 (미배정 상태)
- null 상태의 설비에 Gateway 대상 메시지를 발행하면 즉시 예외 발생 (`ROUTE_PARTITION_NOT_ASSIGNED`)
- `comm_mode`는 `ACTIVE` / `PASSIVE` 구분에 사용 (기존 `tc_eqp_hsms.connection_mode`, `tc_eqp_socket.connection_mode`에서 `tc_eqp`로 통합)

**관련 클래스:**
- `TcEqpEntity` (`libs/db/jpa/tc-db-jpa-common-schema/.../entity/eqp/TcEqpEntity.java`)
- `TcEqp` (도메인 DTO, `routePartition()` 메서드 제공)

---

## 토픽 라우팅 정책

| 토픽 | 방향 | 라우팅 방식 | 발행측 | 소비측 |
|------|------|-----------|------|------|
| `tc.eqp.commands` | UI Backend → Gateway | `route_partition` 명시 partition 발행 | UI Backend | Gateway (ASSIGN) |
| `tc.ui.events.gateway` | UI Backend → Gateway | `route_partition` 명시 partition 발행 | UI Backend | Gateway (ASSIGN) |
| `tc.ui.events.business` | UI Backend → Business | 고정 partition 없음 (일반 발행) | UI Backend | Business (일반 consumer-group) |
| `tc.eqp.events` | Gateway → 공통 | 고정 partition 없음 | Gateway | 공통 소비자 |
| `tc.ui.commands` | Gateway → UI Backend | 고정 partition 없음 | Gateway | UI Backend |

**핵심 원칙:**
- Gateway가 소비해야 하는 토픽(`tc.eqp.commands`, `tc.ui.events.gateway`)만 `route_partition` 명시 발행
- Kafka key는 `eqpId`를 그대로 사용 (추적/정합성 용도, 실제 라우팅은 partition 번호가 결정)

---

## UI Backend — route_partition 조회 및 명시 발행

### 1단계: DB에서 route_partition 조회

```java
// UiEqpRoutePartitionDbAdapter.java
// (libs/ui/adapter/tc-ui-db-adapter/.../UiEqpRoutePartitionDbAdapter.java)

@Override
public Optional<Integer> findRoutePartition(final String eqpId) {
    if (eqpId == null || eqpId.isBlank()) {
        // eqpId 없으면 조회 건너뜀
        return Optional.empty();
    }

    final Optional<TcEqp> found = eqpStore.findByEqpId(eqpId);
    if (found.isEmpty()) {
        // tc_eqp에 설비가 없으면 빈 Optional
        return Optional.empty();
    }

    final Integer routePartition = found.get().routePartition();
    // route_partition이 null이면 빈 Optional (미배정 상태)
    return Optional.ofNullable(routePartition);
}
```

- `route_partition`의 SSOT는 `tc_eqp` 테이블
- 조회 결과만 반환하며, 허용/차단 판단은 발행 어댑터(`UiGatewayEventKafkaPublisher`)에서 수행

### 2단계: 명시 partition 지정 발행

```java
// UiGatewayEventKafkaPublisher.java
// (libs/ui/adapter/tc-ui-kafka-adapter/.../UiGatewayEventKafkaPublisher.java)

@Override
public void publish(final UiCommandMessage message) {
    final String eqpId = message.eqpId();
    final String topic = topicProperties.getGatewayEventsTopic();  // tc.ui.events.gateway

    // 1. DB에서 route_partition 조회
    final Optional<Integer> routePartitionOpt = routePartitionLookupPort.findRoutePartition(eqpId);

    // 2. 미배정 → 즉시 차단
    if (routePartitionOpt.isEmpty()) {
        log.error("tc.ui.events.gateway 발행 차단: route_partition 미배정. topic={}, eqpId={}, reason=ROUTE_PARTITION_NOT_ASSIGNED", ...);
        throw new IllegalStateException("Gateway 발행 실패: eqpId=" + eqpId + "에 route_partition이 배정되지 않았습니다.");
    }

    final int routePartition = routePartitionOpt.get();

    // 3. 음수 partition → 즉시 차단
    if (routePartition < 0) {
        log.error("tc.ui.events.gateway 발행 차단: route_partition 음수. routePartition={}, reason=ROUTE_PARTITION_NEGATIVE", ...);
        throw new IllegalStateException("Gateway 발행 실패: route_partition=" + routePartition + "은 유효하지 않습니다.");
    }

    // 4. 메시지 크기 사전 검증 (max.request.size 가드레일, overhead 1KB 포함)
    final int requestBytes = estimatePayloadBytes(kafkaMessage) + KAFKA_RECORD_OVERHEAD_BYTES;
    if (requestBytes > publishProperties.getMaxRequestBytes()) {
        throw new UiKafkaPublishException("Gateway Kafka 발행 메시지 크기 초과", ...);
    }

    // 5. 명시 partition 발행 (Kafka key는 eqpId 유지)
    final ProducerRecord<String, Object> record = new ProducerRecord<>(topic, routePartition, eqpId, kafkaMessage);
    KafkaHeaderSupport.addTracingHeaders(record, traceId, eventType, source);

    // 6. 동기 발행 (타임아웃: publishProperties.getPublishTimeoutSeconds())
    kafkaTemplate.send(record).get(publishTimeoutSeconds, TimeUnit.SECONDS);
}
```

**발행 차단 사유:**

| 사유 코드 | 조건 | 처리 |
|----------|------|------|
| `ROUTE_PARTITION_NOT_ASSIGNED` | `route_partition`이 null이거나 설비 미존재 | `IllegalStateException` 발생 |
| `ROUTE_PARTITION_NEGATIVE` | `route_partition < 0` | `IllegalStateException` 발생 |
| 메시지 크기 초과 | payload + 1KB overhead > maxRequestBytes | `UiKafkaPublishException` 발생 |

---

## Gateway — ASSIGN 방식 소비

Gateway는 Kafka 동적 Rebalance를 사용하지 않고, 설정에 선언된 `owned-partitions`를 직접 ASSIGN합니다.

### GatewayEqpCommandKafkaSubscriber — tc.eqp.commands 소비

```java
// GatewayEqpCommandKafkaSubscriber.java
// (libs/comm/adapter/tc-comm-gateway-kafka-adapter/.../subscribe/GatewayEqpCommandKafkaSubscriber.java)

@Override
protected KafkaConsumerBindingMode bindingMode() {
    return KafkaConsumerBindingMode.ASSIGN;  // 동적 Rebalance 없음
}

@Override
protected List<TopicPartition> assignedPartitions() {
    // owned-partitions 설정값으로 TopicPartition 목록 구성
    final List<TopicPartition> owned = new ArrayList<>();
    for (Integer partition : shardProperties.getOwnedPartitions()) {
        owned.add(new TopicPartition(topicProperties.getEqpCommands(), partition));
        //                            tc.eqp.commands        partition 번호 (예: 2, 3)
    }
    return owned;
}

@Override
protected void handleRecord(ConsumerRecord<String, GatewayBusinessCommandMessage> record) {
    // envelope/metadata/Kafka key 계약 검증
    final TcCommonKafkaMetadata metadata = contractSupport.validateGatewayBusinessCommandRecord(...);

    // 검증 통과 → GatewayCommandDispatcher로 전달
    // record.partition() 정보도 함께 전달 (수신측 route_partition 검증에 사용)
    dispatcher.dispatchBusinessCommand(message, record.topic(), record.partition(), record.offset());
}
```

### GatewayUiEventKafkaSubscriber — tc.ui.events.gateway 소비

```java
// GatewayUiEventKafkaSubscriber.java
// (libs/comm/adapter/tc-comm-gateway-kafka-adapter/.../subscribe/GatewayUiEventKafkaSubscriber.java)

@Override
protected KafkaConsumerBindingMode bindingMode() {
    return KafkaConsumerBindingMode.ASSIGN;  // tc.eqp.commands와 동일한 ASSIGN 방식
}

@Override
protected List<TopicPartition> assignedPartitions() {
    // tc.eqp.commands와 동일한 ownedPartitions 집합 사용
    final List<TopicPartition> owned = new ArrayList<>();
    for (Integer partition : shardProperties.getOwnedPartitions()) {
        owned.add(new TopicPartition(topicProperties.getUiEvents(), partition));
        //                            tc.ui.events.gateway    partition 번호
    }
    return owned;
}

// UI task 처리 실패 시 commit하지 않고 동일 offset에서 재시도
@Override
protected boolean commitOnRecordFailure() { return false; }

@Override
protected boolean retryFailedRecordFromCurrentOffset() { return true; }

@Override
protected long failedRecordRetryBackoffMs() {
    return uiTaskPolicyProperties.getFailedRecordRetryBackoffMs();  // tc-comm.properties 설정
}
```

**ASSIGN 방식을 선택한 이유:**

| 항목 | Consumer Group (동적 Rebalance) | ASSIGN 방식 (고정 할당) |
|------|-------------------------------|----------------------|
| partition 배분 주체 | Kafka Coordinator 자동 배분 | Gateway 인스턴스가 직접 지정 |
| 인스턴스 추가/제거 시 | 자동 재배분 (Rebalance 발생) | 수동 설정 변경 필요 |
| 상태 일관성 | Rebalance 중 처리 중단 가능 | 항상 동일 인스턴스 처리 보장 |
| Gateway 적합성 | 부적합 (TCP 연결 상태와 분리될 수 있음) | 적합 (TCP 연결과 partition 소유가 동기화됨) |

---

## Gateway — 수신 측 route_partition 정합성 검증

Gateway는 수신한 Kafka 레코드의 실제 partition이 설비 메타데이터의 `route_partition`과 일치하는지 검증합니다.
발행 측(`UiGatewayEventKafkaPublisher`)에서 올바르게 발행했다면 일치해야 하므로,
불일치는 라우팅 설정 오류 또는 운영 이상을 의미합니다.

```java
// GatewayCommandDispatcher.java (validateRoutePartitionOrReject 메서드)
// (libs/comm/adapter/tc-comm-gateway-kafka-adapter/.../subscribe/GatewayCommandDispatcher.java)

// record.partition(): Kafka에서 실제로 수신한 partition 번호
// equipmentInfo.routePartition(): tc_eqp에 저장된 기대 partition 번호

if (equipmentInfo.routePartition() == null) {
    // route_partition 미배정 설비가 수신됨 → 라우팅 데이터 정합성 이상
    log.warn("route_partition 미배정 설비 수신. eqpId={}, partition={}", eqpId, record.partition());
    // disposition: ROUTE_PARTITION_NOT_ASSIGNED (DLQ 발행 안 함 — 발행 측 오류이므로)
    return false;
}

if (equipmentInfo.routePartition() != record.partition()) {
    // 기대 partition과 실제 수신 partition 불일치
    log.warn("route_partition 불일치. eqpId={}, expected={}, actual={}",
             eqpId, equipmentInfo.routePartition(), record.partition());
    // disposition: ROUTE_PARTITION_MISMATCH (DLQ 발행 안 함)
    return false;
}

// 일치 → 처리 진행
```

**검증 목적:**
- 발행 측과 수신 측의 라우팅 기준 일관성 확인
- partition 수 변경 또는 설정 오류를 조기 탐지
- DLQ를 발행하지 않는 이유: 메시지 자체의 문제가 아닌 라우팅 설정 문제이므로 재처리해도 동일하게 실패

---

## 기동 시 불변조건 검증

Gateway 기동 시 `GatewayKafkaOperationalInvariantChecker`가 `@PostConstruct`에서 Kafka 토픽 상태를 검증합니다.
설정 오류로 잘못된 상태로 구동되는 것을 방지하는 **Fail-Fast** 설계입니다.

```java
// GatewayKafkaOperationalInvariantChecker.java
// (libs/comm/adapter/tc-comm-gateway-kafka-adapter/.../config/GatewayKafkaOperationalInvariantChecker.java)

@PostConstruct
public void verify() {
    // AdminClient로 Kafka에서 실제 토픽 메타데이터 조회
    Map<String, TopicDescription> topicDescriptions = admin.describeTopics(requiredTopics)
            .allTopicNames()
            .get(adminTimeoutSeconds, TimeUnit.SECONDS);  // adminTimeoutSeconds=5
}
```

**검증 항목:**

| 순서 | 검증 내용 | 실패 시 |
|------|----------|------|
| 1 | 필수 토픽 4개가 Kafka에 존재하는지 (`tc.eqp.events`, `tc.eqp.commands`, `tc.ui.events.gateway`, `tc.ui.commands`) | `IllegalStateException` 발생 |
| 2 | 각 토픽의 partition 수 ≥ 1 | `IllegalStateException` 발생 |
| 3 | `tc.eqp.commands` 실제 partition 수 == `commands-partition-count` 설정값 | `IllegalStateException` 발생 |
| 4 | `tc.ui.events.gateway` partition 수 == `tc.eqp.commands` partition 수 (두 토픽은 동일해야 함) | `IllegalStateException` 발생 |
| 5 | `owned-partitions`의 모든 값이 `[0, partitionCount)` 범위 이내 | `IllegalStateException` 발생 |

**검증 4번(두 토픽 partition 수 일치)이 필요한 이유:**
- 두 토픽 모두 동일한 `ownedPartitions` 집합을 ASSIGN 기준으로 사용
- partition 수가 다르면 어느 한 토픽의 partition이 소비되지 않거나, 범위를 벗어난 partition을 ASSIGN 시도하게 됨

```
검증 실패 예시:

tc.eqp.commands:      실제 partition 수 = 6
commands-partition-count:              설정값 = 4  ← 불일치!

→ IllegalStateException: "Partition count mismatch for tc.eqp.commands (expected=4, actual=6)"
→ Spring 컨텍스트 초기화 실패 → 앱 즉시 종료
```

---

## 설정 (tc-comm.properties)

```properties
# apps/tc-comm-gateway-app/config/tc-comm.properties
# 섹션 6. Kafka Shard Ownership

# [중요 운영 규칙]
# tc.eqp.commands와 tc.ui.events.gateway 두 토픽의 partition 수는 동일해야 합니다.
# owned-partitions는 두 토픽에 공통으로 적용됩니다.

# tc.eqp.commands 전체 partition 수 (Kafka 토픽의 실제 partition 수와 반드시 일치)
tc.comm.gateway.kafka.commands-partition-count=6

# 현재 Gateway 인스턴스가 소유할 partition 목록
# 인스턴스별로 다르게 설정 (아래는 3대 운영 예시)
# Gateway-1 → owned-partitions=0,1
# Gateway-2 → owned-partitions=2,3
# Gateway-3 → owned-partitions=4,5
tc.comm.gateway.kafka.owned-partitions=2,3

# Consumer poll/commit 설정
tc.comm.gateway.kafka.poll-timeout-ms=1000
tc.comm.gateway.kafka.ui-poll-timeout-ms=1000
tc.comm.gateway.kafka.commit-retry-max=1
tc.comm.gateway.kafka.commit-retry-backoff-ms=200
tc.comm.gateway.kafka.lag-sample-interval-ms=5000
tc.comm.gateway.kafka.consumer-shutdown-wait-ms=3000
tc.comm.gateway.kafka.admin-timeout-seconds=5

# 비동기 처리 설정 (poll 스레드와 레코드 처리 스레드 분리)
tc.comm.gateway.kafka.async-record-processing-enabled=true
tc.comm.gateway.kafka.record-worker-threads=8
tc.comm.gateway.kafka.ack-drain-max-batch=512
tc.comm.gateway.kafka.max-in-flight-records=10000
```

**설정 유효성 검증 (`GatewayKafkaShardProperties.validate()`):**
- `commands-partition-count > 0` 필수
- `owned-partitions` 비어있지 않아야 함
- `owned-partitions` 각 값 ≥ 0 이고 < `commands-partition-count`

---

## PASSIVE / ACTIVE 설비 라우팅 규칙

### ACTIVE 설비

- 설비마다 고유한 TCP 연결을 맺음
- `route_partition` 기준으로 각 설비가 개별 Gateway 인스턴스에 귀속
- 해당 `route_partition`을 소유한 Gateway만 connect/start 및 명령 처리 수행

```
EQP-001 (ACTIVE, route_partition=0)
  → Gateway-1 (owned: 0,1)만 EQP-001 TCP 연결 및 명령 처리
```

### PASSIVE 설비

- 여러 설비가 동일한 `bindIp + port + socketType`의 listener 리소스를 공유함
- **같은 listener-group에 속하는 설비는 반드시 동일한 `route_partition`을 가져야 함**
- 이 규칙을 위반하면 설비 등록/수정 시 요청 차단

```
listener-group: interfaceType=SOCKET, bindIp=192.168.1.100, port=5000, socketType=LINE_DELIMITED

EQP-003 (PASSIVE, route_partition=0)  ← 동일
EQP-007 (PASSIVE, route_partition=0)  ← 동일
EQP-008 (PASSIVE, route_partition=1)  ← 다른 listener-group이면 다른 partition 허용
```

**PASSIVE listener-group 동일 `route_partition` 규칙이 필요한 이유:**
- PASSIVE 설비들은 같은 포트로 접속하므로, 어느 설비가 연결할지 사전에 알 수 없음
- 연결된 이후에야 설비 식별이 가능 → 이미 특정 Gateway 인스턴스에 연결된 상태
- listener-group 내 설비들이 서로 다른 `route_partition`을 가지면, 명령이 연결되지 않은 Gateway로 라우팅될 수 있음

---

## 무중단 증설 운영 정책

Gateway 인스턴스를 추가할 때 **기존 설비의 `route_partition`은 변경하지 않는** 원칙을 사용합니다.

### 증설 절차 예시

```
초기 상태:
  tc.eqp.commands: 6 partitions (p0~p5)
  tc.ui.events.gateway: 6 partitions (p0~p5)
  Gateway-1: owned-partitions=0,1  (EQP-001, EQP-002, EQP-003)
  Gateway-2: owned-partitions=2,3  (EQP-004, EQP-005)
  Gateway-3: owned-partitions=4,5  (EQP-006, EQP-007)

증설 절차 (Gateway-4 추가):
  1. Kafka 토픽 partition 증설: 6 → 12
     (tc.eqp.commands, tc.ui.events.gateway 동시 증설 — 두 토픽 partition 수는 동일해야 함)

  2. Gateway-4 서버 준비 및 설정:
     tc.comm.gateway.kafka.commands-partition-count=12   ← 증설 후 전체 partition 수
     tc.comm.gateway.kafka.owned-partitions=6,7           ← 신규 Gateway 소유 partition

  3. 기존 Gateway-1,2,3 설정 업데이트:
     commands-partition-count=12 (토픽 실제 수와 일치해야 함)
     owned-partitions는 변경 없음

  4. 신규 설비 등록 시:
     tc_eqp.route_partition = 6 또는 7 배정 (기존 설비는 0~5 유지)

  5. Gateway-4 기동
```

### 증설 비범위 (1차)

- 기존 생산 설비의 live `route_partition` 재배치 (무중단 설비 이동)
- 기존 설비를 새 Gateway로 온라인 전환
- 필요 시 설비 정지 후 `route_partition` 변경 및 TCP 재연결로 수행

---

## 관련 클래스 및 파일 목록

| 역할 | 클래스 / 파일 |
|------|-------------|
| DB 엔티티 (`route_partition` 필드) | `TcEqpEntity` (`libs/db/jpa/tc-db-jpa-common-schema/.../entity/eqp/TcEqpEntity.java`) |
| route_partition 조회 포트 | `UiGatewayEqpRoutePartitionLookupPort` (`libs/ui/tc-ui-core/.../port/messaging/`) |
| route_partition DB 조회 어댑터 | `UiEqpRoutePartitionDbAdapter` (`libs/ui/adapter/tc-ui-db-adapter/.../db/`) |
| tc.ui.events.gateway 발행 | `UiGatewayEventKafkaPublisher` (`libs/ui/adapter/tc-ui-kafka-adapter/.../publish/`) |
| tc.eqp.commands ASSIGN 소비 | `GatewayEqpCommandKafkaSubscriber` (`libs/comm/adapter/tc-comm-gateway-kafka-adapter/.../subscribe/`) |
| tc.ui.events.gateway ASSIGN 소비 | `GatewayUiEventKafkaSubscriber` (`libs/comm/adapter/tc-comm-gateway-kafka-adapter/.../subscribe/`) |
| 수신 측 route_partition 검증 | `GatewayCommandDispatcher` (`libs/comm/adapter/tc-comm-gateway-kafka-adapter/.../subscribe/`) |
| 기동 시 불변조건 검증 | `GatewayKafkaOperationalInvariantChecker` (`libs/comm/adapter/tc-comm-gateway-kafka-adapter/.../config/`) |
| Shard 설정 Properties | `GatewayKafkaShardProperties` (`libs/comm/tc-comm-gateway-core/.../config/props/`) |
| 토픽 설정 Properties | `GatewayKafkaTopicProperties` (`libs/comm/adapter/tc-comm-gateway-kafka-adapter/.../config/`) |
| Gateway 설정 파일 | `tc-comm.properties` (`apps/tc-comm-gateway-app/config/`) |

---

## 비정상 케이스 처리

| 상황 | 발생 지점 | 처리 결과 |
|------|----------|---------|
| `route_partition`이 null (미배정) | 발행 측: `UiGatewayEventKafkaPublisher` | `IllegalStateException` → API 500 오류 응답 |
| `route_partition`이 음수 | 발행 측: `UiGatewayEventKafkaPublisher` | `IllegalStateException` → API 500 오류 응답 |
| 발행 타임아웃 | 발행 측: `UiGatewayEventKafkaPublisher` | `UiKafkaPublishException` (GATEWAY, TimeoutException) |
| 수신 partition ≠ `route_partition` | 수신 측: `GatewayCommandDispatcher` | REJECTED disposition, warn 로그, DLQ 미발행 |
| `route_partition` null인 설비가 수신됨 | 수신 측: `GatewayCommandDispatcher` | REJECTED disposition, warn 로그 |
| Kafka 토픽 partition 수 불일치 | 기동 시: `GatewayKafkaOperationalInvariantChecker` | `IllegalStateException` → 앱 기동 실패 |
| `commands-partition-count` 설정 불일치 | 기동 시: `GatewayKafkaOperationalInvariantChecker` | `IllegalStateException` → 앱 기동 실패 |
| `owned-partitions` 범위 초과 | `GatewayKafkaShardProperties.validate()` 또는 기동 시 검증 | `IllegalStateException` → 앱 기동 실패 |

---

## 운영 포인트

| 항목 | 내용 |
|------|------|
| **두 토픽 partition 수 동기화** | `tc.eqp.commands`와 `tc.ui.events.gateway`는 항상 동일한 partition 수를 유지해야 합니다. 하나만 증설하면 기동 시 검증 실패 |
| **owned-partitions 중복 없음** | 두 Gateway 인스턴스가 같은 partition을 소유하면 명령이 중복 처리됩니다 |
| **owned-partitions 공백 없음** | 담당 인스턴스가 없는 partition의 메시지는 영원히 처리되지 않습니다 |
| **route_partition DB 등록 필수** | 설비 등록 시 반드시 `route_partition`을 배정해야 합니다. null이면 명령 발행 자체가 차단됩니다 |
| **PASSIVE 동일 partition 강제** | 같은 `interfaceType + bindIp + port + socketType` listener-group 내 설비는 반드시 동일한 `route_partition`을 가져야 합니다 |
| **commands-partition-count 동기화** | 토픽 partition 증설 후 기존 모든 Gateway의 `commands-partition-count` 설정도 업데이트해야 합니다 |
| **partition 감소 불가** | 이미 발행된 메시지가 있는 토픽은 partition 수를 줄일 수 없습니다. 늘리는 것만 가능합니다 |
| **route_partition 불일치 경보** | `warn: route_partition 불일치` 로그가 발생하면 DB 데이터 또는 발행 설정 오류를 즉시 점검하세요 |
