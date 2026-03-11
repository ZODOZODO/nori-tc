# 03. Kafka Partition 기반 고정 라우팅 (Kafka Partition Shard Routing)

## 개요

`tc-comm-gateway-app`은 여러 인스턴스를 동시에 운영할 수 있습니다.
각 인스턴스는 **특정 설비들만 전담**해서 처리합니다.
이를 구현하기 위해 **Kafka Partition을 인스턴스에 고정 할당**하는 방식을 사용합니다.

이 구조를 **Shard(샤드) 기반 라우팅**이라고 부릅니다.

---

## 왜 이 구조가 필요한가?

### 문제: Gateway가 여러 대일 때 상태 일관성 문제

```
설비 EQP-001은 Gateway-1에 TCP 연결되어 있음
설비 EQP-001의 명령이 Gateway-2에 도착하면?
  → Gateway-2에는 EQP-001의 TCP 연결이 없음
  → 명령을 전달할 수 없음!

또한 Gateway-1과 Gateway-2가 각각
EQP-001의 상태를 따로 관리하면:
  → 상태 불일치 발생
  → "연결됨"과 "연결 안 됨"을 동시에 판단할 수 있음
```

### 해결: 설비별 Gateway 인스턴스 고정

```
EQP-001, EQP-002, EQP-003 → 항상 Gateway-1 에서만 처리
EQP-004, EQP-005, EQP-006 → 항상 Gateway-2 에서만 처리

→ 각 설비의 TCP 연결과 상태 관리가 특정 인스턴스에 집중됨
→ 명령이 항상 올바른 인스턴스에 도달
→ 상태 일관성 보장
```

---

## 구조 다이어그램

```
┌──────────────────────────────────────────────────────────────────┐
│                  tc.eqp.commands (Kafka Topic)                   │
│                                                                  │
│  Partition 0 │ Partition 1 │ Partition 2 │ Partition 3 │ ...   │
│  [EQP-001]   │ [EQP-002]   │ [EQP-004]   │ [EQP-005]   │       │
│  [EQP-003]   │             │             │ [EQP-006]   │       │
└──────┬──────────────┬─────────────────┬──────────────────────────┘
       │              │                 │
  owned=0,1       owned=2,3         owned=4,5
       ↓              ↓                 ↓
┌─────────────┐ ┌─────────────┐ ┌─────────────┐
│  Gateway-1  │ │  Gateway-2  │ │  Gateway-3  │
│             │ │             │ │             │
│ EQP-001 TCP │ │ EQP-004 TCP │ │ EQP-007 TCP │
│ EQP-002 TCP │ │ EQP-005 TCP │ │ EQP-008 TCP │
│ EQP-003 TCP │ │ EQP-006 TCP │ │ EQP-009 TCP │
└─────────────┘ └─────────────┘ └─────────────┘
```

---

## route_partition 헤더

UI Backend는 명령을 발행할 때 **어떤 Gateway 인스턴스로 보낼지**를 결정해야 합니다.
이를 위해 Kafka 메시지에 `route_partition` 헤더를 포함합니다.

```
UI Backend → tc.eqp.commands 발행 시:

Kafka 메시지:
  Key: EQP-001
  Headers:
    route_partition: 0    ← 이 메시지를 Partition 0에 발행
  Value: {"commandId": "...", "eqpId": "EQP-001", ...}
```

**route_partition 결정 방법:**

```java
// UiEqpRoutePartitionDbAdapter.java
// DB의 equipment 테이블에서 EQP-001의 route_partition 조회
// (설비가 어느 partition에 할당되었는지 사전에 등록되어 있음)

public int getRoutePartition(String eqpId) {
    return equipmentRepository.findByEqpId(eqpId)
        .map(EquipmentEntity::getRoutePartition)
        .orElseThrow(() -> new EqpNotFoundException(eqpId));
}
```

---

## Gateway 인스턴스 설정

각 Gateway 인스턴스는 자신이 담당할 partition을 `tc-comm.properties`에 선언합니다.

```properties
# Gateway-1 (tc-comm.properties)
tc.comm.gateway.commands-partition-count=6   # 전체 partition 수 (모든 인스턴스 동일)
tc.comm.gateway.owned-partitions=0,1         # 이 인스턴스가 담당할 partition 번호

# Gateway-2 (tc-comm.properties)
tc.comm.gateway.commands-partition-count=6
tc.comm.gateway.owned-partitions=2,3

# Gateway-3 (tc-comm.properties)
tc.comm.gateway.commands-partition-count=6
tc.comm.gateway.owned-partitions=4,5
```

---

## ASSIGN 모드 Consumer

일반 Kafka Consumer는 Consumer Group 내에서 **동적 Rebalance**로 partition이 자동 배분됩니다.
하지만 Gateway는 **ASSIGN 모드**를 사용해 partition을 직접 지정합니다.

```java
// GatewayEqpCommandKafkaSubscriber.java

// ASSIGN 모드: owned-partitions를 직접 지정
List<TopicPartition> partitions = ownedPartitions.stream()
    .map(p -> new TopicPartition(commandsTopic, p))
    .collect(Collectors.toList());

consumer.assign(partitions);  // 동적 Rebalance 없음
```

**일반 Consumer Group vs ASSIGN 모드 비교:**

| 항목 | Consumer Group (동적 Rebalance) | ASSIGN 모드 (고정 할당) |
|------|--------------------------------|----------------------|
| 할당 방식 | Kafka Coordinator가 자동 배분 | 인스턴스가 직접 지정 |
| 인스턴스 추가/제거 시 | 자동으로 재배분 (Rebalance) | 수동으로 설정 변경 필요 |
| 상태 일관성 | Rebalance 중 처리 중단 가능 | 항상 동일 인스턴스 처리 |
| 설정 복잡도 | 낮음 (자동) | 높음 (수동 관리) |

**Gateway에서 ASSIGN 모드를 선택한 이유:**
- 설비의 TCP 연결과 상태는 특정 인스턴스에 묶여 있습니다
- Rebalance로 갑자기 다른 인스턴스가 명령을 받으면 TCP 연결이 없어서 실패합니다
- 고정 할당으로 상태 일관성을 보장합니다

---

## 운영 — Gateway 인스턴스 추가/제거

### 인스턴스 추가 시

```
1. 새 Gateway-4 서버 준비
2. tc-comm.properties 설정:
   commands-partition-count=8   ← 총 partition을 8개로 늘렸다고 가정
   owned-partitions=6,7

3. 기존 Gateway-1,2,3의 설정은 변경 없음 (담당 partition 범위 유지)

4. Kafka 토픽 tc.eqp.commands의 partition 수를 8개로 증가
   (commands-partition-count와 일치시켜야 함)

5. 새 설비들의 route_partition을 6 또는 7로 DB에 등록

6. Gateway-4 기동
```

### 인스턴스 제거 시

```
1. Gateway-2를 제거한다고 가정 (담당: partition 2,3)

2. Gateway-2가 담당하던 설비의 route_partition을 다른 인스턴스로 변경
   → EQP-004, EQP-005, EQP-006의 route_partition을 0 또는 1로 변경

3. Gateway-1의 owned-partitions=0,1,2,3 으로 확장
   (partition 2,3도 담당하도록)

4. Gateway-2 종료

주의: 이 과정에서 EQP-004,005,006은 Gateway-1로 TCP 재연결이 필요합니다
```

---

## Kafka Partition 수 검증

기동 시 `GatewayKafkaOperationalInvariantChecker`가 partition 수 일관성을 검증합니다.

```
검증 내용:
1. tc.eqp.commands 토픽의 실제 partition 수
   = tc-comm.properties의 commands-partition-count

2. owned-partitions의 모든 값이 [0, commands-partition-count) 범위 이내

3. tc.ui.events.gateway 토픽도 동일한 partition 수 (같은 라우팅 키 사용)
```

```
예시 — 검증 실패:
  실제 토픽 partition 수: 6
  설정의 commands-partition-count: 4 ← 불일치!

  → GatewayStartupException 발생
  → 앱 기동 실패
  → 로그: "tc.eqp.commands partition 수 불일치: 실제=6, 설정=4. tc-comm.properties 수정 필요"
```

---

## 주의사항

| 항목 | 내용 |
|------|------|
| **partition 수 일치** | Kafka 토픽의 실제 partition 수와 `commands-partition-count` 설정이 반드시 같아야 합니다 |
| **owned-partitions 중복 없음** | 두 Gateway 인스턴스가 같은 partition을 담당하면 같은 메시지가 두 번 처리됩니다 |
| **owned-partitions 공백 없음** | 담당하는 인스턴스가 없는 partition이 있으면 그 partition의 메시지는 영원히 처리되지 않습니다 |
| **route_partition DB 등록** | 새 설비를 추가할 때 반드시 DB에 `route_partition`을 등록해야 합니다 |
| **토픽 partition 감소 불가** | 이미 발행된 메시지가 있는 토픽은 partition 수를 줄일 수 없습니다. 늘리는 것만 가능합니다 |
