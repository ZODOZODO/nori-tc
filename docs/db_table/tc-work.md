# 작업(Work) 테이블 명세

> **파일 경로** : `nori-tc/docs/db/table/tc-work.md`
> **스키마**    : `public`
> **작성 기준** : PostgreSQL DDL 역설계
> **테이블 수** : 8개

---

## 1. 도메인 개요

### 1-1. 테이블 목록

| No | 테이블명 | 설명 |
|:--:|---|---|
| 1 | `tc_work` | 작업 원장. 모든 작업 관련 테이블의 최상위 부모 |
| 2 | `tc_work_param` | 작업별 Key-Value 파라미터 |
| 3 | `tc_work_carrier` | 작업에 투입된 캐리어 정보 |
| 4 | `tc_work_carrier_slot` | 캐리어 내 슬롯별 상태 |
| 5 | `tc_work_controljob` | SECS/GEM Control Job 정보 |
| 6 | `tc_work_processjob` | SECS/GEM Process Job 정보 |
| 7 | `tc_work_processjob_lot_map` | Process Job ↔ Lot N:M 매핑 |
| 8 | `tc_work_lot` | 작업에 포함된 Lot 정보 |

### 1-2. 설계 원칙

`tc_work` 가 모든 작업 관련 테이블의 루트이며, `work_key` 를 기준으로 모든 자식 테이블이 연결된다. SECS/GEM 계층 구조인 `Work → ControlJob → ProcessJob → Lot` 을 그대로 테이블 계층으로 반영한다. 작업 삭제 시 모든 하위 테이블은 `ON DELETE CASCADE` 처리된다. `tc_work` 는 `tc_eqp` 를 삭제 정책 없이 단순 FK 참조하므로, 작업 이력이 있는 장비는 삭제할 수 없다.

---

## 2. 테이블 관계 다이어그램

```
tc_eqp ──[RESTRICT]──► tc_work
                          │
                          ├──[CASCADE]──► tc_work_param
                          ├──[CASCADE]──► tc_work_carrier
                          │                   │
                          │                   └──[CASCADE]──► tc_work_carrier_slot
                          ├──[CASCADE]──► tc_work_controljob
                          │                   │
                          │                   └──[CASCADE]──► tc_work_processjob
                          │                                       │
                          │                                       └──[CASCADE]──► tc_work_processjob_lot_map
                          │                                                              │
                          └──[CASCADE]──► tc_work_lot ◄──[CASCADE]─────────────────────┘
```

---

## 3. 테이블 상세 명세

### 3-1. `tc_work`

#### 개요

장비에서 실행되는 작업(Work)의 기본 정보와 상태를 관리하는 최상위 테이블이다. 장비+작업ID 조합이 UNIQUE 다.

#### 컬럼 명세

| 컬럼명 | 타입 | NOT NULL | 기본값 | 설명 |
|---|---|:---:|---|---|
| `work_key` | `bigint` | ✅ | identity (자동 증가) | PK |
| `eqp_key` | `bigint` | ✅ | - | FK → `tc_eqp.eqp_key` |
| `work_id` | `varchar(64)` | ✅ | - | 작업 식별 ID |
| `operator_id` | `varchar(64)` | - | - | 작업 담당 오퍼레이터 ID |
| `step_seq` | `integer` | - | - | 공정 스텝 순서 (>= 0) |
| `work_state` | `varchar(20)` | ✅ | - | 작업 상태. `QUEUED` / `RUNNING` / `COMPLETED` / `ABORTED` |
| `start_time` | `timestamptz(3)` | - | - | 작업 시작 일시 |
| `end_time` | `timestamptz(3)` | - | - | 작업 종료 일시 |
| `mes_message` | `text` | - | - | MES 시스템 전달 메시지 |
| `created_at` | `timestamptz(3)` | ✅ | `CURRENT_TIMESTAMP` | 생성 일시 |
| `updated_at` | `timestamptz(3)` | ✅ | `CURRENT_TIMESTAMP` | 수정 일시 |

#### 인덱스 명세

| 인덱스명 | 컬럼 | 종류 | 목적 |
|---|---|---|---|
| `pk_tc_work` | `work_key` | PRIMARY KEY | PK 단건 조회 |
| `uk_tc_work_eqp_key_work_id` | `eqp_key`, `work_id` | UNIQUE | 장비+작업ID 중복 방지 |
| `ix_tc_work_eqp_key` | `eqp_key` | INDEX | 장비별 작업 목록 조회 |
| `ix_tc_work_work_state` | `work_state` | INDEX | 상태별 작업 목록 조회 |
| `ix_tc_work_created_at` | `created_at` | INDEX | 생성 일시 기반 시계열 조회 |

#### 제약 조건

| 제약명 | 조건 |
|---|---|
| `ck_tc_work_work_state` | `work_state IN ('QUEUED', 'RUNNING', 'COMPLETED', 'ABORTED')` |
| `ck_tc_work_step_seq` | `step_seq IS NULL OR step_seq >= 0` |

#### 외래 키

| FK명 | 컬럼 | 참조 테이블.컬럼 | 삭제 정책 |
|---|---|---|---|
| `fk_tc_work_eqp_key__tc_eqp` | `eqp_key` | `tc_eqp.eqp_key` | RESTRICT (기본값) |

---

### 3-2. `tc_work_param`

#### 개요

작업별 Key-Value 형태의 파라미터를 저장한다. 동일 작업에서 같은 `param_name` 은 UNIQUE 제약으로 중복을 방지한다.

#### 컬럼 명세

| 컬럼명 | 타입 | NOT NULL | 기본값 | 설명 |
|---|---|:---:|---|---|
| `work_param_key` | `bigint` | ✅ | identity (자동 증가) | PK |
| `work_key` | `bigint` | ✅ | - | FK → `tc_work.work_key` |
| `param_name` | `varchar(100)` | ✅ | - | 파라미터 이름 |
| `param_value` | `varchar(2000)` | - | - | 파라미터 값 |
| `updated_at` | `timestamptz(3)` | ✅ | `CURRENT_TIMESTAMP` | 수정 일시 |

#### 인덱스 명세

| 인덱스명 | 컬럼 | 종류 | 목적 |
|---|---|---|---|
| `pk_tc_work_param` | `work_param_key` | PRIMARY KEY | PK 단건 조회 |
| `uk_tc_work_param_work_key_param_name` | `work_key`, `param_name` | UNIQUE | 작업+파라미터명 중복 방지 |
| `ix_tc_work_param_work_key` | `work_key` | INDEX | 작업별 파라미터 전체 조회 |

#### 제약 조건

```
없음 (NOT NULL 및 UNIQUE 제약만 존재)
```

#### 외래 키

| FK명 | 컬럼 | 참조 테이블.컬럼 | 삭제 정책 |
|---|---|---|---|
| `fk_tc_work_param_work_key__tc_work` | `work_key` | `tc_work.work_key` | CASCADE |

---

### 3-3. `tc_work_carrier`

#### 개요

작업에 투입된 캐리어의 식별 정보, 포트 위치, 수량 정보를 저장한다. 작업+캐리어ID 조합이 UNIQUE 다.

#### 컬럼 명세

| 컬럼명 | 타입 | NOT NULL | 기본값 | 설명 |
|---|---|:---:|---|---|
| `work_carrier_key` | `bigint` | ✅ | identity (자동 증가) | PK |
| `work_key` | `bigint` | ✅ | - | FK → `tc_work.work_key` |
| `carrier_id` | `varchar(64)` | ✅ | - | 캐리어 식별 ID |
| `port_id` | `varchar(20)` | - | - | 캐리어가 올려진 포트 ID |
| `slot_map` | `varchar(255)` | - | - | 슬롯 사용 현황 맵 (비트 문자열 등) |
| `total_qty` | `integer` | - | - | 전체 슬롯 수 (>= 0) |
| `good_qty` | `integer` | - | - | 양품 수 (>= 0) |
| `scrap_qty` | `integer` | - | - | 불량품 수 (>= 0) |
| `updated_at` | `timestamptz(3)` | ✅ | `CURRENT_TIMESTAMP` | 수정 일시 |

#### 인덱스 명세

| 인덱스명 | 컬럼 | 종류 | 목적 |
|---|---|---|---|
| `pk_tc_work_carrier` | `work_carrier_key` | PRIMARY KEY | PK 단건 조회 |
| `uk_tc_work_carrier_work_key_carrier_id` | `work_key`, `carrier_id` | UNIQUE | 작업+캐리어ID 중복 방지 |
| `ix_tc_work_carrier_work_key` | `work_key` | INDEX | 작업별 캐리어 목록 조회 |
| `ix_tc_work_carrier_carrier_id` | `carrier_id` | INDEX | 캐리어 ID로 조회 |
| `ix_tc_work_carrier_port_id` | `port_id` | INDEX | 포트 ID로 조회 |

#### 제약 조건

| 제약명 | 조건 |
|---|---|
| `ck_tc_work_carrier_total_qty` | `total_qty IS NULL OR total_qty >= 0` |
| `ck_tc_work_carrier_good_qty` | `good_qty IS NULL OR good_qty >= 0` |
| `ck_tc_work_carrier_scrap_qty` | `scrap_qty IS NULL OR scrap_qty >= 0` |

#### 외래 키

| FK명 | 컬럼 | 참조 테이블.컬럼 | 삭제 정책 |
|---|---|---|---|
| `fk_tc_work_carrier_work_key__tc_work` | `work_key` | `tc_work.work_key` | CASCADE |

---

### 3-4. `tc_work_carrier_slot`

#### 개요

캐리어 내 슬롯별 상태(빈 슬롯, 웨이퍼 존재 여부 등)와 Lot ID를 저장한다. 캐리어+슬롯번호 조합이 UNIQUE 다. 슬롯 번호는 1부터 시작한다.

#### 컬럼 명세

| 컬럼명 | 타입 | NOT NULL | 기본값 | 설명 |
|---|---|:---:|---|---|
| `carrier_slot_key` | `bigint` | ✅ | identity (자동 증가) | PK |
| `work_carrier_key` | `bigint` | ✅ | - | FK → `tc_work_carrier.work_carrier_key` |
| `slot_no` | `integer` | ✅ | - | 슬롯 번호 (>= 1) |
| `slot_state` | `varchar(50)` | ✅ | - | 슬롯 상태 |
| `lot_id` | `varchar(64)` | - | - | 해당 슬롯에 올려진 Lot ID |
| `updated_at` | `timestamptz(3)` | ✅ | `CURRENT_TIMESTAMP` | 수정 일시 |

#### 인덱스 명세

| 인덱스명 | 컬럼 | 종류 | 목적 |
|---|---|---|---|
| `pk_tc_work_carrier_slot` | `carrier_slot_key` | PRIMARY KEY | PK 단건 조회 |
| `uk_tc_work_carrier_slot_work_carrier_key_slot_no` | `work_carrier_key`, `slot_no` | UNIQUE | 캐리어+슬롯번호 중복 방지 |
| `ix_tc_work_carrier_slot_work_carrier_key` | `work_carrier_key` | INDEX | 캐리어별 슬롯 전체 조회 |
| `ix_tc_work_carrier_slot_lot_id` | `lot_id` | INDEX | Lot ID로 슬롯 위치 역추적 |

#### 제약 조건

| 제약명 | 조건 |
|---|---|
| `ck_tc_work_carrier_slot_slot_no` | `slot_no >= 1` |

#### 외래 키

| FK명 | 컬럼 | 참조 테이블.컬럼 | 삭제 정책 |
|---|---|---|---|
| `fk_tc_work_carrier_slot_work_carrier_key__tc_work_carrier` | `work_carrier_key` | `tc_work_carrier.work_carrier_key` | CASCADE |

---

### 3-5. `tc_work_controljob`

#### 개요

SECS/GEM Control Job 의 식별 정보와 상태를 저장한다. 작업+Control Job ID 조합이 UNIQUE 다.

#### 컬럼 명세

| 컬럼명 | 타입 | NOT NULL | 기본값 | 설명 |
|---|---|:---:|---|---|
| `control_job_key` | `bigint` | ✅ | identity (자동 증가) | PK |
| `work_key` | `bigint` | ✅ | - | FK → `tc_work.work_key` |
| `controljob_id` | `varchar(64)` | ✅ | - | Control Job 식별 ID |
| `controljob_state` | `varchar(20)` | ✅ | - | Control Job 상태. `CREATED` / `QUEUED` / `RUNNING` / `PAUSED` / `COMPLETED` / `ABORTED` / `FAILED` |
| `created_at` | `timestamptz(3)` | ✅ | `CURRENT_TIMESTAMP` | 생성 일시 |
| `updated_at` | `timestamptz(3)` | ✅ | `CURRENT_TIMESTAMP` | 수정 일시 |

#### 인덱스 명세

| 인덱스명 | 컬럼 | 종류 | 목적 |
|---|---|---|---|
| `pk_tc_work_controljob` | `control_job_key` | PRIMARY KEY | PK 단건 조회 |
| `uk_tc_work_controljob_work_key_controljob_id` | `work_key`, `controljob_id` | UNIQUE | 작업+Control Job ID 중복 방지 |
| `ix_tc_work_controljob_work_key` | `work_key` | INDEX | 작업별 Control Job 목록 조회 |
| `ix_tc_work_controljob_controljob_id` | `controljob_id` | INDEX | Control Job ID로 조회 |

#### 제약 조건

| 제약명 | 조건 |
|---|---|
| `ck_tc_work_controljob_state` | `controljob_state IN ('CREATED', 'QUEUED', 'RUNNING', 'PAUSED', 'COMPLETED', 'ABORTED', 'FAILED')` |

#### 외래 키

| FK명 | 컬럼 | 참조 테이블.컬럼 | 삭제 정책 |
|---|---|---|---|
| `fk_tc_work_controljob_work_key__tc_work` | `work_key` | `tc_work.work_key` | CASCADE |

---

### 3-6. `tc_work_processjob`

#### 개요

SECS/GEM Process Job 의 식별 정보, 상태, 레시피 ID를 저장한다. `tc_work_controljob` 의 하위 개념이며, Control Job+Process Job ID 조합이 UNIQUE 다.

#### 컬럼 명세

| 컬럼명 | 타입 | NOT NULL | 기본값 | 설명 |
|---|---|:---:|---|---|
| `process_job_key` | `bigint` | ✅ | identity (자동 증가) | PK |
| `control_job_key` | `bigint` | ✅ | - | FK → `tc_work_controljob.control_job_key` |
| `processjob_id` | `varchar(64)` | ✅ | - | Process Job 식별 ID |
| `processjob_state` | `varchar(20)` | ✅ | - | Process Job 상태. `CREATED` / `QUEUED` / `RUNNING` / `PAUSED` / `COMPLETED` / `ABORTED` / `FAILED` |
| `recipe_id` | `varchar(128)` | ✅ | - | 적용할 레시피 ID |
| `created_at` | `timestamptz(3)` | ✅ | `CURRENT_TIMESTAMP` | 생성 일시 |
| `updated_at` | `timestamptz(3)` | ✅ | `CURRENT_TIMESTAMP` | 수정 일시 |

#### 인덱스 명세

| 인덱스명 | 컬럼 | 종류 | 목적 |
|---|---|---|---|
| `pk_tc_work_processjob` | `process_job_key` | PRIMARY KEY | PK 단건 조회 |
| `uk_tc_work_processjob_control_job_key_processjob_id` | `control_job_key`, `processjob_id` | UNIQUE | Control Job+Process Job ID 중복 방지 |
| `ix_tc_work_processjob_control_job_key` | `control_job_key` | INDEX | Control Job별 Process Job 목록 조회 |
| `ix_tc_work_processjob_processjob_id` | `processjob_id` | INDEX | Process Job ID로 조회 |

#### 제약 조건

| 제약명 | 조건 |
|---|---|
| `ck_tc_work_processjob_state` | `processjob_state IN ('CREATED', 'QUEUED', 'RUNNING', 'PAUSED', 'COMPLETED', 'ABORTED', 'FAILED')` |

#### 외래 키

| FK명 | 컬럼 | 참조 테이블.컬럼 | 삭제 정책 |
|---|---|---|---|
| `fk_tc_work_processjob_control_job_key__tc_work_controljob` | `control_job_key` | `tc_work_controljob.control_job_key` | CASCADE |

---

### 3-7. `tc_work_processjob_lot_map`

#### 개요

`tc_work_processjob` 과 `tc_work_lot` 의 N:M 관계를 연결하는 중간 테이블이다. Process Job+Lot 조합이 UNIQUE 다. `map_role` 로 Lot의 역할을, `map_order` 로 처리 방향을 구분한다.

#### 컬럼 명세

| 컬럼명 | 타입 | NOT NULL | 기본값 | 설명 |
|---|---|:---:|---|---|
| `pj_lot_map_key` | `bigint` | ✅ | identity (자동 증가) | PK |
| `process_job_key` | `bigint` | ✅ | - | FK → `tc_work_processjob.process_job_key` |
| `work_lot_key` | `bigint` | ✅ | - | FK → `tc_work_lot.work_lot_key` |
| `map_role` | `varchar(20)` | - | - | Lot 역할 구분 |
| `map_order` | `varchar(20)` | - | - | 처리 방향. `FORWARD` / `REVERSE` |
| `created_at` | `timestamptz(3)` | ✅ | `CURRENT_TIMESTAMP` | 생성 일시 |
| `updated_at` | `timestamptz(3)` | ✅ | `CURRENT_TIMESTAMP` | 수정 일시 |

#### 인덱스 명세

| 인덱스명 | 컬럼 | 종류 | 목적 |
|---|---|---|---|
| `pk_tc_work_processjob_lot_map` | `pj_lot_map_key` | PRIMARY KEY | PK 단건 조회 |
| `uk_tc_work_pj_lot_map_process_job_key_work_lot_key` | `process_job_key`, `work_lot_key` | UNIQUE | Process Job+Lot 중복 방지 |
| `ix_tc_work_pj_lot_map_process_job_key` | `process_job_key` | INDEX | Process Job별 Lot 목록 조회 |
| `ix_tc_work_pj_lot_map_work_lot_key` | `work_lot_key` | INDEX | Lot별 Process Job 목록 조회 |

#### 제약 조건

| 제약명 | 조건 |
|---|---|
| `ck_tc_work_pj_lot_map_map_order` | `map_order IS NULL OR map_order IN ('FORWARD', 'REVERSE')` |

#### 외래 키

| FK명 | 컬럼 | 참조 테이블.컬럼 | 삭제 정책 |
|---|---|---|---|
| `fk_tc_work_pj_lot_map_process_job_key__tc_work_processjob` | `process_job_key` | `tc_work_processjob.process_job_key` | CASCADE |
| `fk_tc_work_pj_lot_map_work_lot_key__tc_work_lot` | `work_lot_key` | `tc_work_lot.work_lot_key` | CASCADE |

---

### 3-8. `tc_work_lot`

#### 개요

작업에 포함된 Lot 의 식별 정보, 캐리어, 챔버 정보를 저장한다. 작업+Lot ID 조합이 UNIQUE 다.

#### 컬럼 명세

| 컬럼명 | 타입 | NOT NULL | 기본값 | 설명 |
|---|---|:---:|---|---|
| `work_lot_key` | `bigint` | ✅ | identity (자동 증가) | PK |
| `work_key` | `bigint` | ✅ | - | FK → `tc_work.work_key` |
| `carrier_id` | `varchar(64)` | - | - | Lot 이 올려진 캐리어 ID |
| `lot_id` | `varchar(64)` | ✅ | - | Lot 식별 ID |
| `parent_lot_id` | `varchar(64)` | - | - | 부모 Lot ID (분할 공정용) |
| `chamber_id` | `varchar(64)` | - | - | 처리 챔버 ID |
| `updated_at` | `timestamptz(3)` | ✅ | `CURRENT_TIMESTAMP` | 수정 일시 |

#### 인덱스 명세

| 인덱스명 | 컬럼 | 종류 | 목적 |
|---|---|---|---|
| `pk_tc_work_lot` | `work_lot_key` | PRIMARY KEY | PK 단건 조회 |
| `uk_tc_work_lot_work_key_lot_id` | `work_key`, `lot_id` | UNIQUE | 작업+Lot ID 중복 방지 |
| `ix_tc_work_lot_work_key` | `work_key` | INDEX | 작업별 Lot 목록 조회 |
| `ix_tc_work_lot_lot_id` | `lot_id` | INDEX | Lot ID로 조회 |
| `ix_tc_work_lot_carrier_id` | `carrier_id` | INDEX | 캐리어 ID로 Lot 역추적 |

#### 제약 조건

```
없음 (NOT NULL 및 UNIQUE 제약만 존재)
```

#### 외래 키

| FK명 | 컬럼 | 참조 테이블.컬럼 | 삭제 정책 |
|---|---|---|---|
| `fk_tc_work_lot_work_key__tc_work` | `work_key` | `tc_work.work_key` | CASCADE |

---

## 4. FK 관계 요약

```
tc_eqp.eqp_key
    └──[RESTRICT]── tc_work.eqp_key

tc_work.work_key
    ├──[CASCADE]──► tc_work_param.work_key
    ├──[CASCADE]──► tc_work_carrier.work_key
    │                   └──[CASCADE]──► tc_work_carrier_slot.work_carrier_key
    ├──[CASCADE]──► tc_work_controljob.work_key
    │                   └──[CASCADE]──► tc_work_processjob.control_job_key
    │                                       └──[CASCADE]──► tc_work_processjob_lot_map.process_job_key
    └──[CASCADE]──► tc_work_lot.work_key
                        └──[CASCADE]──► tc_work_processjob_lot_map.work_lot_key
```

---

## 5. 주요 쿼리 패턴

### 5-1. 장비의 진행 중인 작업 조회

```sql
SELECT work_key, work_id, operator_id, step_seq, work_state, start_time
  FROM tc_work
 WHERE eqp_key    = :eqp_key
   AND work_state = 'RUNNING';
```

### 5-2. 작업의 캐리어 및 슬롯 상태 조회

```sql
SELECT c.carrier_id, c.port_id, c.total_qty, c.good_qty, c.scrap_qty,
       s.slot_no, s.slot_state, s.lot_id
  FROM tc_work_carrier      c
  JOIN tc_work_carrier_slot s ON s.work_carrier_key = c.work_carrier_key
 WHERE c.work_key = :work_key
 ORDER BY c.carrier_id, s.slot_no;
```

### 5-3. 특정 Process Job 에 연결된 Lot 목록 조회

```sql
SELECT l.lot_id, l.carrier_id, l.chamber_id, m.map_role, m.map_order
  FROM tc_work_processjob_lot_map m
  JOIN tc_work_lot                l ON l.work_lot_key = m.work_lot_key
 WHERE m.process_job_key = :process_job_key
 ORDER BY m.map_order;
```

### 5-4. 작업의 Control Job → Process Job 계층 조회

```sql
SELECT cj.controljob_id, cj.controljob_state,
       pj.processjob_id, pj.processjob_state, pj.recipe_id
  FROM tc_work_controljob  cj
  JOIN tc_work_processjob  pj ON pj.control_job_key = cj.control_job_key
 WHERE cj.work_key = :work_key
 ORDER BY cj.controljob_id, pj.processjob_id;
```
