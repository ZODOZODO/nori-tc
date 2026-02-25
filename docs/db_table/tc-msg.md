# 메시지 발송 큐(MSG) 테이블 명세

> **파일 경로** : `nori-tc/docs/db/table/tc-msg.md`
> **스키마**    : `public`
> **작성 기준** : PostgreSQL DDL 역설계
> **테이블 수** : 2개

---

## 1. 도메인 개요

### 1-1. 테이블 목록

| No | 테이블명 | 설명 |
|:--:|---|---|
| 1 | `tc_msg_send_queue` | 발송 대기 메시지 큐. Transactional Outbox 패턴의 저장소 |
| 2 | `tc_msg_send_log` | 발송 시도 이력. 메시지별 전송 결과를 누적 기록 |

### 1-2. 설계 원칙

**Transactional Outbox 패턴**을 구현한다. 비즈니스 로직과 메시지 발송을 같은 DB 트랜잭션 내에서 처리하여 원자성을 보장한다. 비즈니스 트랜잭션 내에서 `tc_msg_send_queue` 에 INSERT 하고, 별도 배치 워커가 폴링하여 Kafka 로 발송한다. 발송 시마다 `tc_msg_send_log` 에 이력을 기록하며, 최대 재시도 횟수 초과 시 `status = 'DEAD'` 로 처리한다. 분산 환경에서 중복 처리를 방지하기 위해 `locked_by`, `locked_until` 을 사용한 비관적 락을 적용한다.

---

## 2. 테이블 관계 다이어그램

```
tc_msg_send_queue
    │
    └──[CASCADE]──► tc_msg_send_log
```

---

## 3. 테이블 상세 명세

### 3-1. `tc_msg_send_queue`

#### 개요

Kafka 로 발송할 메시지를 임시 저장하는 Outbox 큐 테이블이다. 비즈니스 트랜잭션과 동일한 트랜잭션 내에서 INSERT 되어 원자성을 보장한다. 배치 워커는 `status + next_retry_at` 인덱스로 발송 대상을 폴링하고, `locked_by + locked_until` 로 분산 중복 처리를 방지한다.

#### 메시지 상태 전이

```
[PENDING] ──► [SENDING] ──► [SENT]
                   │
                   └──► [FAILED] ──► (재시도) ──► [SENDING]
                                          │
                                          └──► [DEAD]  (최대 재시도 초과)
```

#### 컬럼 명세

| 컬럼명 | 타입 | NOT NULL | 기본값 | 설명 |
|---|---|:---:|---|---|
| `msg_key` | `bigint` | ✅ | identity (자동 증가) | PK |
| `idempotency_key` | `varchar(128)` | ✅ | - | 멱등성 키. topic+idempotency_key 가 UNIQUE |
| `topic` | `varchar(200)` | ✅ | - | Kafka 토픽 이름 |
| `message_key` | `varchar(200)` | - | - | Kafka 메시지 파티셔닝 키 |
| `headers_json` | `text` | - | - | Kafka 헤더 (JSON 형식) |
| `payload_json` | `text` | ✅ | - | 발송할 메시지 본문 (JSON 형식) |
| `status` | `varchar(16)` | ✅ | - | 발송 상태. `PENDING` / `SENDING` / `SENT` / `FAILED` / `DEAD` |
| `retry_count` | `integer` | ✅ | `0` | 현재까지 재시도 횟수 (>= 0) |
| `next_retry_at` | `timestamptz(3)` | - | - | 다음 재시도 예약 일시 (지수 백오프 적용) |
| `locked_by` | `varchar(64)` | - | - | 현재 처리 중인 워커 인스턴스 ID |
| `locked_until` | `timestamptz(3)` | - | - | 락 만료 일시. 이 시각 이후 다른 워커가 처리 가능 |
| `created_at` | `timestamptz(3)` | ✅ | `CURRENT_TIMESTAMP` | 생성 일시 |
| `updated_at` | `timestamptz(3)` | ✅ | `CURRENT_TIMESTAMP` | 수정 일시 |

#### 인덱스 명세

| 인덱스명 | 컬럼 | 종류 | 목적 |
|---|---|---|---|
| `pk_tc_msg_send_queue` | `msg_key` | PRIMARY KEY | PK 단건 조회 |
| `uk_tc_msg_send_queue_topic_idempotency_key` | `topic`, `idempotency_key` | UNIQUE | 토픽+멱등성 키 중복 방지 |
| `ix_tc_msg_send_queue_status_next_retry_at` | `status`, `next_retry_at` | INDEX | 배치 워커 발송 대상 폴링 (핵심 쿼리) |
| `ix_tc_msg_send_queue_topic_status` | `topic`, `status` | INDEX | 토픽별 상태 현황 조회 |
| `ix_tc_msg_send_queue_locked_until` | `locked_until` | INDEX | 만료된 락 탐지 및 강제 해제 |

#### 제약 조건

| 제약명 | 조건 |
|---|---|
| `ck_tc_msg_send_queue_status` | `status IN ('PENDING', 'SENDING', 'SENT', 'FAILED', 'DEAD')` |
| `ck_tc_msg_send_queue_retry_count` | `retry_count >= 0` |

#### 외래 키

```
없음 (이 테이블은 최상위 부모이며 다른 테이블에서 참조함)
```

---

### 3-2. `tc_msg_send_log`

#### 개요

메시지 발송 시도 결과를 누적 기록하는 이력 테이블이다. 발송 성공 시 Kafka 파티션과 오프셋을 기록하고, 실패 시 에러 코드와 메시지를 기록한다. `attempt_no` 는 1부터 시작하며 재시도마다 1씩 증가한다.

#### 컬럼 명세

| 컬럼명 | 타입 | NOT NULL | 기본값 | 설명 |
|---|---|:---:|---|---|
| `send_log_key` | `bigint` | ✅ | identity (자동 증가) | PK |
| `msg_key` | `bigint` | ✅ | - | FK → `tc_msg_send_queue.msg_key` |
| `attempt_no` | `integer` | ✅ | - | 발송 시도 횟수 (>= 1, 1부터 시작) |
| `result` | `varchar(16)` | ✅ | - | 발송 결과. `SUCCESS` / `FAIL` |
| `kafka_partition` | `integer` | - | - | Kafka 파티션 번호 (성공 시 기록) |
| `kafka_offset` | `bigint` | - | - | Kafka 오프셋 (성공 시 기록) |
| `error_code` | `varchar(64)` | - | - | 에러 코드 (실패 시 기록) |
| `error_message` | `text` | - | - | 에러 상세 메시지 (실패 시 기록) |
| `sent_at` | `timestamptz(3)` | ✅ | `CURRENT_TIMESTAMP` | 발송 시도 일시 |

#### 인덱스 명세

| 인덱스명 | 컬럼 | 종류 | 목적 |
|---|---|---|---|
| `pk_tc_msg_send_log` | `send_log_key` | PRIMARY KEY | PK 단건 조회 |
| `ix_tc_msg_send_log_msg_key_attempt_no` | `msg_key`, `attempt_no` | INDEX | 메시지별 시도 이력 순서 조회 |
| `ix_tc_msg_send_log_sent_at` | `sent_at` | INDEX | 발송 일시 기반 시계열 조회 |

#### 제약 조건

| 제약명 | 조건 |
|---|---|
| `ck_tc_msg_send_log_result` | `result IN ('SUCCESS', 'FAIL')` |
| `ck_tc_msg_send_log_attempt_no` | `attempt_no >= 1` |

#### 외래 키

| FK명 | 컬럼 | 참조 테이블.컬럼 | 삭제 정책 |
|---|---|---|---|
| `fk_tc_msg_send_log_msg_key__tc_msg_send_queue` | `msg_key` | `tc_msg_send_queue.msg_key` | CASCADE |

---

## 4. FK 관계 요약

```
tc_msg_send_queue.msg_key
    └──[CASCADE]──► tc_msg_send_log.msg_key
```

---

## 5. 주요 쿼리 패턴

### 5-1. 배치 워커 발송 대상 폴링 (FOR UPDATE SKIP LOCKED)

```sql
SELECT msg_key, topic, message_key, headers_json, payload_json
  FROM tc_msg_send_queue
 WHERE status        = 'PENDING'
   AND (next_retry_at IS NULL OR next_retry_at <= NOW())
 ORDER BY created_at
 LIMIT 100
   FOR UPDATE SKIP LOCKED;
```

### 5-2. 발송 성공 처리

```sql
UPDATE tc_msg_send_queue
   SET status     = 'SENT',
       locked_by  = NULL,
       locked_until = NULL,
       updated_at = NOW()
 WHERE msg_key = :msg_key;

INSERT INTO tc_msg_send_log (msg_key, attempt_no, result, kafka_partition, kafka_offset)
VALUES (:msg_key, :attempt_no, 'SUCCESS', :partition, :offset);
```

### 5-3. 발송 실패 처리 (재시도 예약)

```sql
UPDATE tc_msg_send_queue
   SET status        = 'FAILED',
       retry_count   = retry_count + 1,
       next_retry_at = NOW() + (INTERVAL '1 second' * POWER(2, retry_count)),
       locked_by     = NULL,
       locked_until  = NULL,
       updated_at    = NOW()
 WHERE msg_key = :msg_key;

INSERT INTO tc_msg_send_log (msg_key, attempt_no, result, error_code, error_message)
VALUES (:msg_key, :attempt_no, 'FAIL', :error_code, :error_message);
```

### 5-4. 특정 메시지의 발송 시도 이력 조회

```sql
SELECT attempt_no, result, kafka_partition, kafka_offset, error_code, sent_at
  FROM tc_msg_send_log
 WHERE msg_key = :msg_key
 ORDER BY attempt_no ASC;
```
