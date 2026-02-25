# 모델(Model) 테이블 명세

> **파일 경로** : `nori-tc/docs/db/table/tc-model.md`
> **스키마**    : `public`
> **작성 기준** : PostgreSQL DDL 역설계
> **테이블 수** : 10개

---

## 1. 도메인 개요

### 1-1. 테이블 목록

| No | 테이블명 | 설명 |
|:--:|---|---|
| 1 | `tc_model` | 모델 원장. 모든 모델 관련 테이블의 최상위 부모 |
| 2 | `tc_model_param` | 모델별 Key-Value 파라미터 |
| 3 | `tc_model_eventid` | SECS Event ID (CEID) 정의 |
| 4 | `tc_model_reportid` | SECS Report ID (RPTID) 정의 |
| 5 | `tc_model_variableid` | SECS Variable ID (SVID/DVID/ECID/CEID) 정의 |
| 6 | `tc_model_mdf` | 모델 파일(MDF) 바이너리 저장 |
| 7 | `tc_model_secs_message` | HSMS 통신용 SECS 메시지 정의 |
| 8 | `tc_model_socket_message` | Socket 통신용 커스텀 메시지 정의 |
| 9 | `tc_model_workflow` | 메시지 수신 시 실행할 워크플로우 매핑 정의 |
| 10 | `tc_model_dcop_item` | DCOP 데이터 수집 항목 정의 |

### 1-2. 설계 원칙

`tc_model` 이 모든 모델 관련 테이블의 루트이며, `model_key` 를 기준으로 모든 자식 테이블이 연결된다. 모델은 `DRAFT` → `ACTIVE` → `DEPRECATED` 생명주기를 가지며, 장비(`tc_eqp`)는 모델을 `RESTRICT` FK로 참조하므로 장비에 적용 중인 모델은 삭제할 수 없다. 모델 삭제 시 모든 하위 테이블은 `ON DELETE CASCADE` 처리된다.

---

## 2. 테이블 관계 다이어그램

```
tc_model
    │
    ├──[CASCADE]──► tc_model_param
    ├──[CASCADE]──► tc_model_eventid
    ├──[CASCADE]──► tc_model_reportid
    ├──[CASCADE]──► tc_model_variableid
    ├──[CASCADE]──► tc_model_mdf
    ├──[CASCADE]──► tc_model_secs_message
    ├──[CASCADE]──► tc_model_socket_message
    ├──[CASCADE]──► tc_model_workflow
    ├──[CASCADE]──► tc_model_dcop_item
    └──[RESTRICT]── tc_eqp.model_key         (→ tc-eqp.md)
```

---

## 3. 테이블 상세 명세

### 3-1. `tc_model`

#### 개요

장비 종류별 통신 모델의 기본 식별 정보, 버전, 상태를 관리하는 최상위 테이블이다. 동일 모델명이라도 버전이 다르면 별도 행으로 관리된다.

#### 컬럼 명세

| 컬럼명 | 타입 | NOT NULL | 기본값 | 설명 |
|---|---|:---:|---|---|
| `model_key` | `bigint` | ✅ | identity (자동 증가) | PK |
| `model_name` | `varchar(128)` | ✅ | - | 모델 이름 |
| `model_version` | `varchar(32)` | ✅ | - | 모델 버전. 예: `1.0.0` |
| `comm_interface` | `varchar(16)` | ✅ | - | 통신 방식. `HSMS` / `SOCKET` |
| `status` | `varchar(16)` | ✅ | - | 모델 상태. `DRAFT` / `ACTIVE` / `DEPRECATED` |
| `maker` | `varchar(32)` | - | - | 장비 제조사. 예: `AMAT`, `TEL` |
| `created_at` | `timestamptz(3)` | ✅ | `CURRENT_TIMESTAMP` | 생성 일시 |
| `updated_at` | `timestamptz(3)` | ✅ | `CURRENT_TIMESTAMP` | 수정 일시 |
| `created_by` | `varchar(50)` | ✅ | `'SYSTEM'` | 생성 주체 |
| `updated_by` | `varchar(50)` | ✅ | `'SYSTEM'` | 수정 주체 |

#### 인덱스 명세

| 인덱스명 | 컬럼 | 종류 | 목적 |
|---|---|---|---|
| `pk_tc_model` | `model_key` | PRIMARY KEY | PK 단건 조회 |
| `uk_tc_model_model_name_model_version` | `model_name`, `model_version` | UNIQUE | 모델명+버전 중복 방지 |
| `ix_tc_model_model_name` | `model_name` | INDEX | 모델명 검색 |
| `ix_tc_model_status` | `status` | INDEX | 상태별 모델 목록 조회 |
| `ix_tc_model_comm_interface` | `comm_interface` | INDEX | 통신 방식별 모델 목록 조회 |
| `ix_tc_model_maker` | `maker` | INDEX | 제조사별 모델 목록 조회 |

#### 제약 조건

| 제약명 | 조건 |
|---|---|
| `ck_tc_model_comm_interface` | `comm_interface IN ('HSMS', 'SOCKET')` |
| `ck_tc_model_status` | `status IN ('DRAFT', 'ACTIVE', 'DEPRECATED')` |

#### 외래 키

```
없음 (이 테이블은 최상위 부모이며 다른 테이블에서 참조함)
```

---

### 3-2. `tc_model_param`

#### 개요

모델별 Key-Value 형태의 설정 파라미터를 저장한다. 동일 모델에서 같은 `param_name` 은 UNIQUE 제약으로 중복을 방지한다.

#### 컬럼 명세

| 컬럼명 | 타입 | NOT NULL | 기본값 | 설명 |
|---|---|:---:|---|---|
| `model_param_key` | `bigint` | ✅ | identity (자동 증가) | PK |
| `model_key` | `bigint` | ✅ | - | FK → `tc_model.model_key` |
| `param_name` | `varchar(128)` | ✅ | - | 파라미터 이름 |
| `param_value` | `varchar(2000)` | - | - | 파라미터 값 |
| `updated_at` | `timestamptz(3)` | ✅ | `CURRENT_TIMESTAMP` | 수정 일시 |

#### 인덱스 명세

| 인덱스명 | 컬럼 | 종류 | 목적 |
|---|---|---|---|
| `pk_tc_model_param` | `model_param_key` | PRIMARY KEY | PK 단건 조회 |
| `uk_tc_model_param_model_key_param_name` | `model_key`, `param_name` | UNIQUE | 모델+파라미터명 중복 방지 |
| `ix_tc_model_param_model_key` | `model_key` | INDEX | 모델별 파라미터 전체 조회 |

#### 제약 조건

```
없음 (NOT NULL 및 UNIQUE 제약만 존재)
```

#### 외래 키

| FK명 | 컬럼 | 참조 테이블.컬럼 | 삭제 정책 |
|---|---|---|---|
| `fk_tc_model_param_model_key__tc_model` | `model_key` | `tc_model.model_key` | CASCADE |

---

### 3-3. `tc_model_eventid`

#### 개요

HSMS(SECS-II) 통신에서 장비가 발생시키는 Event ID(CEID) 목록을 정의한다. 어떤 이벤트에 어떤 Report ID 가 연결되는지 설정하며, `enabled` 플래그로 수신 활성화 여부를 제어한다.

#### 컬럼 명세

| 컬럼명 | 타입 | NOT NULL | 기본값 | 설명 |
|---|---|:---:|---|---|
| `event_key` | `bigint` | ✅ | identity (자동 증가) | PK |
| `model_key` | `bigint` | ✅ | - | FK → `tc_model.model_key` |
| `event_id` | `varchar(100)` | ✅ | - | SECS Event ID (CEID) |
| `report_id` | `varchar(1000)` | - | - | 연결된 Report ID 목록 (구분자로 연결) |
| `enabled` | `boolean` | ✅ | `false` | 이벤트 수신 활성화 여부 |
| `updated_at` | `timestamptz(3)` | ✅ | `CURRENT_TIMESTAMP` | 수정 일시 |

#### 인덱스 명세

| 인덱스명 | 컬럼 | 종류 | 목적 |
|---|---|---|---|
| `pk_tc_model_eventid` | `event_key` | PRIMARY KEY | PK 단건 조회 |
| `uk_tc_model_eventid_model_key_event_id` | `model_key`, `event_id` | UNIQUE | 모델+이벤트ID 중복 방지 |
| `ix_tc_model_eventid_model_key` | `model_key` | INDEX | 모델별 이벤트 전체 조회 |
| `ix_tc_model_eventid_enabled` | `enabled` | INDEX | 활성화된 이벤트만 필터링 |

#### 제약 조건

| 제약명 | 조건 |
|---|---|
| `ck_tc_model_eventid_enabled` | `enabled IN (true, false)` |

#### 외래 키

| FK명 | 컬럼 | 참조 테이블.컬럼 | 삭제 정책 |
|---|---|---|---|
| `fk_tc_model_eventid_model_key__tc_model` | `model_key` | `tc_model.model_key` | CASCADE |

---

### 3-4. `tc_model_reportid`

#### 개요

HSMS(SECS-II) 통신에서 장비가 보고하는 Report ID(RPTID) 목록을 정의한다. 각 Report ID 에 포함된 Variable ID 목록을 설정하며, `enabled` 플래그로 수신 활성화 여부를 제어한다.

#### 컬럼 명세

| 컬럼명 | 타입 | NOT NULL | 기본값 | 설명 |
|---|---|:---:|---|---|
| `report_key` | `bigint` | ✅ | identity (자동 증가) | PK |
| `model_key` | `bigint` | ✅ | - | FK → `tc_model.model_key` |
| `report_id` | `varchar(100)` | ✅ | - | SECS Report ID (RPTID) |
| `variable_id` | `varchar(1000)` | - | - | 포함된 Variable ID 목록 (구분자로 연결) |
| `enabled` | `boolean` | ✅ | `false` | Report 수신 활성화 여부 |
| `updated_at` | `timestamptz(3)` | ✅ | `CURRENT_TIMESTAMP` | 수정 일시 |

#### 인덱스 명세

| 인덱스명 | 컬럼 | 종류 | 목적 |
|---|---|---|---|
| `pk_tc_model_reportid` | `report_key` | PRIMARY KEY | PK 단건 조회 |
| `uk_tc_model_reportid_model_key_report_id` | `model_key`, `report_id` | UNIQUE | 모델+Report ID 중복 방지 |
| `ix_tc_model_reportid_model_key` | `model_key` | INDEX | 모델별 Report 전체 조회 |
| `ix_tc_model_reportid_enabled` | `enabled` | INDEX | 활성화된 Report만 필터링 |

#### 제약 조건

| 제약명 | 조건 |
|---|---|
| `ck_tc_model_reportid_enabled` | `enabled IN (true, false)` |

#### 외래 키

| FK명 | 컬럼 | 참조 테이블.컬럼 | 삭제 정책 |
|---|---|---|---|
| `fk_tc_model_reportid_model_key__tc_model` | `model_key` | `tc_model.model_key` | CASCADE |

---

### 3-5. `tc_model_variableid`

#### 개요

HSMS(SECS-II) 통신에서 사용되는 Variable ID 목록을 정의한다. `variable_id_type` 으로 SVID, DVID, ECID, CEID 를 구분하며, 동일 모델 내에서 타입+ID 조합이 UNIQUE 다.

| 타입 | 설명 |
|---|---|
| `SVID` | Status Variable ID — 장비 상태값 |
| `DVID` | Data Variable ID — 데이터 수집 변수 |
| `ECID` | Equipment Constant ID — 장비 상수 |
| `CEID` | Collection Event ID — 수집 이벤트 |

#### 컬럼 명세

| 컬럼명 | 타입 | NOT NULL | 기본값 | 설명 |
|---|---|:---:|---|---|
| `variable_key` | `bigint` | ✅ | identity (자동 증가) | PK |
| `model_key` | `bigint` | ✅ | - | FK → `tc_model.model_key` |
| `variable_id` | `varchar(100)` | ✅ | - | Variable ID 값 |
| `variable_id_type` | `varchar(10)` | ✅ | `'SVID'` | 변수 타입. `SVID` / `DVID` / `ECID` / `CEID` |
| `description` | `varchar(2000)` | - | - | 변수 설명 |
| `updated_at` | `timestamptz(3)` | ✅ | `CURRENT_TIMESTAMP` | 수정 일시 |

#### 인덱스 명세

| 인덱스명 | 컬럼 | 종류 | 목적 |
|---|---|---|---|
| `pk_tc_model_variableid` | `variable_key` | PRIMARY KEY | PK 단건 조회 |
| `uk_tc_model_variableid_model_key_type_variable_id` | `model_key`, `variable_id_type`, `variable_id` | UNIQUE | 모델+타입+ID 중복 방지 |
| `ix_tc_model_variableid_model_key` | `model_key` | INDEX | 모델별 변수 전체 조회 |
| `ix_tc_model_variableid_variable_id` | `variable_id` | INDEX | Variable ID로 검색 |

#### 제약 조건

| 제약명 | 조건 |
|---|---|
| `ck_tc_model_variableid_type` | `variable_id_type IN ('SVID', 'DVID', 'ECID', 'CEID')` |

#### 외래 키

| FK명 | 컬럼 | 참조 테이블.컬럼 | 삭제 정책 |
|---|---|---|---|
| `fk_tc_model_variableid_model_key__tc_model` | `model_key` | `tc_model.model_key` | CASCADE |

---

### 3-6. `tc_model_mdf`

#### 개요

모델에 연결된 MDF(Model Definition File) 바이너리 파일을 저장한다. 동일 모델에 여러 MDF 파일을 이름으로 구분하여 저장할 수 있다.

#### 컬럼 명세

| 컬럼명 | 타입 | NOT NULL | 기본값 | 설명 |
|---|---|:---:|---|---|
| `mdf_key` | `bigint` | ✅ | identity (자동 증가) | PK |
| `model_key` | `bigint` | ✅ | - | FK → `tc_model.model_key` |
| `mdf_name` | `varchar(100)` | ✅ | - | MDF 파일 이름 |
| `mdf_file` | `bytea` | ✅ | - | MDF 파일 바이너리 데이터 |
| `updated_at` | `timestamptz(3)` | ✅ | `CURRENT_TIMESTAMP` | 수정 일시 |

#### 인덱스 명세

| 인덱스명 | 컬럼 | 종류 | 목적 |
|---|---|---|---|
| `pk_tc_model_mdf` | `mdf_key` | PRIMARY KEY | PK 단건 조회 |
| `uk_tc_model_mdf_model_key_mdf_name` | `model_key`, `mdf_name` | UNIQUE | 모델+파일명 중복 방지 |
| `ix_tc_model_mdf_model_key` | `model_key` | INDEX | 모델별 MDF 파일 전체 조회 |

#### 제약 조건

```
없음 (NOT NULL 및 UNIQUE 제약만 존재)
```

#### 외래 키

| FK명 | 컬럼 | 참조 테이블.컬럼 | 삭제 정책 |
|---|---|---|---|
| `fk_tc_model_mdf_model_key__tc_model` | `model_key` | `tc_model.model_key` | CASCADE |

---

### 3-7. `tc_model_secs_message`

#### 개요

HSMS 통신에서 처리하는 SECS-II 메시지 정의를 저장한다. 메시지 이름, 데이터 파싱 인덱스 경로 등 메시지 처리에 필요한 정보를 보관한다.

#### 컬럼 명세

| 컬럼명 | 타입 | NOT NULL | 기본값 | 설명 |
|---|---|:---:|---|---|
| `secs_msg_key` | `bigint` | ✅ | identity (자동 증가) | PK |
| `model_key` | `bigint` | ✅ | - | FK → `tc_model.model_key` |
| `secs_msg_name` | `varchar(100)` | ✅ | - | SECS 메시지 이름. 예: `S6F11`, `S1F1` |
| `description` | `varchar(2000)` | - | - | 메시지 설명 |
| `data_index` | `varchar(200)` | - | - | 메시지 데이터 파싱 인덱스 경로 |
| `updated_at` | `timestamptz(3)` | ✅ | `CURRENT_TIMESTAMP` | 수정 일시 |

#### 인덱스 명세

| 인덱스명 | 컬럼 | 종류 | 목적 |
|---|---|---|---|
| `pk_tc_model_secs_message` | `secs_msg_key` | PRIMARY KEY | PK 단건 조회 |
| `uk_tc_model_secs_message_model_key_secs_msg_name` | `model_key`, `secs_msg_name` | UNIQUE | 모델+메시지명 중복 방지 |
| `ix_tc_model_secs_message_model_key` | `model_key` | INDEX | 모델별 메시지 전체 조회 |
| `ix_tc_model_secs_message_secs_msg_name` | `secs_msg_name` | INDEX | 메시지 이름으로 검색 |

#### 제약 조건

```
없음 (NOT NULL 및 UNIQUE 제약만 존재)
```

#### 외래 키

| FK명 | 컬럼 | 참조 테이블.컬럼 | 삭제 정책 |
|---|---|---|---|
| `fk_tc_model_secs_message_model_key__tc_model` | `model_key` | `tc_model.model_key` | CASCADE |

---

### 3-8. `tc_model_socket_message`

#### 개요

Socket 통신에서 처리하는 커스텀 소켓 메시지 정의를 저장한다. 메시지 이름, 데이터 파싱 인덱스 경로 등 메시지 처리에 필요한 정보를 보관한다.

#### 컬럼 명세

| 컬럼명 | 타입 | NOT NULL | 기본값 | 설명 |
|---|---|:---:|---|---|
| `socket_msg_key` | `bigint` | ✅ | identity (자동 증가) | PK |
| `model_key` | `bigint` | ✅ | - | FK → `tc_model.model_key` |
| `socket_msg_name` | `varchar(100)` | ✅ | - | 소켓 메시지 이름 |
| `description` | `varchar(2000)` | - | - | 메시지 설명 |
| `data_index` | `varchar(200)` | - | - | 메시지 데이터 파싱 인덱스 경로 |
| `updated_at` | `timestamptz(3)` | ✅ | `CURRENT_TIMESTAMP` | 수정 일시 |

#### 인덱스 명세

| 인덱스명 | 컬럼 | 종류 | 목적 |
|---|---|---|---|
| `pk_tc_model_socket_message` | `socket_msg_key` | PRIMARY KEY | PK 단건 조회 |
| `uk_tc_model_socket_message_model_key_socket_msg_name` | `model_key`, `socket_msg_name` | UNIQUE | 모델+메시지명 중복 방지 |
| `ix_tc_model_socket_message_model_key` | `model_key` | INDEX | 모델별 메시지 전체 조회 |
| `ix_tc_model_socket_message_socket_msg_name` | `socket_msg_name` | INDEX | 메시지 이름으로 검색 |

#### 제약 조건

```
없음 (NOT NULL 및 UNIQUE 제약만 존재)
```

#### 외래 키

| FK명 | 컬럼 | 참조 테이블.컬럼 | 삭제 정책 |
|---|---|---|---|
| `fk_tc_model_socket_message_model_key__tc_model` | `model_key` | `tc_model.model_key` | CASCADE |

---

### 3-9. `tc_model_workflow`

#### 개요

특정 메시지 수신 시 실행할 워크플로우 매핑 정의를 저장한다. 어떤 메시지가 왔을 때 어떤 액션을 수행할지 라우팅 규칙을 정의하며, 모델+워크플로우명+메시지명 조합이 UNIQUE 다.

#### 컬럼 명세

| 컬럼명 | 타입 | NOT NULL | 기본값 | 설명 |
|---|---|:---:|---|---|
| `workflow_key` | `bigint` | ✅ | identity (자동 증가) | PK |
| `model_key` | `bigint` | ✅ | - | FK → `tc_model.model_key` |
| `workflow_name` | `varchar(200)` | ✅ | - | 워크플로우 이름 |
| `message_name` | `varchar(200)` | ✅ | - | 트리거 메시지 이름 |
| `event_id` | `varchar(200)` | - | - | 연결된 이벤트 ID |
| `transaction_id` | `varchar(200)` | - | - | 트랜잭션 식별자 |
| `workflow_filter` | `varchar(200)` | - | - | 워크플로우 실행 조건 필터 |
| `action_name` | `varchar(200)` | ✅ | - | 실행할 액션 이름 |
| `action_data_index` | `varchar(1000)` | - | - | 액션에 전달할 데이터 인덱스 경로 |
| `updated_at` | `timestamptz(3)` | ✅ | `CURRENT_TIMESTAMP` | 수정 일시 |

#### 인덱스 명세

| 인덱스명 | 컬럼 | 종류 | 목적 |
|---|---|---|---|
| `pk_tc_model_workflow` | `workflow_key` | PRIMARY KEY | PK 단건 조회 |
| `uk_tc_model_workflow_model_key_workflow_name_message_name` | `model_key`, `workflow_name`, `message_name` | UNIQUE | 모델+워크플로우+메시지명 중복 방지 |
| `ix_tc_model_workflow_model_key` | `model_key` | INDEX | 모델별 워크플로우 전체 조회 |
| `ix_tc_model_workflow_workflow_name` | `workflow_name` | INDEX | 워크플로우 이름으로 검색 |

#### 제약 조건

```
없음 (NOT NULL 및 UNIQUE 제약만 존재)
```

#### 외래 키

| FK명 | 컬럼 | 참조 테이블.컬럼 | 삭제 정책 |
|---|---|---|---|
| `fk_tc_model_workflow_model_key__tc_model` | `model_key` | `tc_model.model_key` | CASCADE |

---

### 3-10. `tc_model_dcop_item`

#### 개요

DCOP(Data Collection Operation Profile)에서 수집할 데이터 항목과 집계 규칙을 정의한다. 어떤 이벤트/변수에서 어떤 워크플로우를 통해 데이터를 수집하고 집계할지를 정의한다.

#### 컬럼 명세

| 컬럼명 | 타입 | NOT NULL | 기본값 | 설명 |
|---|---|:---:|---|---|
| `dcop_item_key` | `bigint` | ✅ | identity (자동 증가) | PK |
| `model_key` | `bigint` | ✅ | - | FK → `tc_model.model_key` |
| `dcop_item_name` | `varchar(200)` | ✅ | - | 수집 항목 이름 |
| `workflow_name` | `varchar(200)` | - | - | 연결된 워크플로우 이름 |
| `event_id` | `varchar(100)` | - | - | 수집 트리거 Event ID |
| `variable_id` | `varchar(100)` | - | - | 수집 대상 Variable ID |
| `collection_rule` | `varchar(10)` | - | - | 수집 규칙. `FIRST`(첫 번째 값) / `LAST`(마지막 값) |
| `calculation_rule` | `varchar(20)` | - | - | 집계 규칙. `ADD` / `MULTIPLY` / `SUBTRACT` / `NONE` |
| `order_rule` | `integer` | - | - | 항목 정렬 순서 (>= 0) |
| `updated_at` | `timestamptz(3)` | ✅ | `CURRENT_TIMESTAMP` | 수정 일시 |

#### 인덱스 명세

| 인덱스명 | 컬럼 | 종류 | 목적 |
|---|---|---|---|
| `pk_tc_model_dcop_item` | `dcop_item_key` | PRIMARY KEY | PK 단건 조회 |
| `uk_tc_model_dcop_item_model_key_dcop_item_name` | `model_key`, `dcop_item_name` | UNIQUE | 모델+항목명 중복 방지 |
| `ix_tc_model_dcop_item_model_key` | `model_key` | INDEX | 모델별 DCOP 항목 전체 조회 |
| `ix_tc_model_dcop_item_event_id` | `event_id` | INDEX | Event ID로 항목 조회 |

#### 제약 조건

| 제약명 | 조건 |
|---|---|
| `ck_tc_model_dcop_item_collection_rule` | `collection_rule IS NULL OR collection_rule IN ('FIRST', 'LAST')` |
| `ck_tc_model_dcop_item_calculation_rule` | `calculation_rule IS NULL OR calculation_rule IN ('ADD', 'MULTIPLY', 'SUBTRACT', 'NONE')` |
| `ck_tc_model_dcop_item_order_rule` | `order_rule IS NULL OR order_rule >= 0` |

#### 외래 키

| FK명 | 컬럼 | 참조 테이블.컬럼 | 삭제 정책 |
|---|---|---|---|
| `fk_tc_model_dcop_item_model_key__tc_model` | `model_key` | `tc_model.model_key` | CASCADE |

---

## 4. FK 관계 요약

```
tc_model.model_key
    ├──[CASCADE]──► tc_model_param.model_key
    ├──[CASCADE]──► tc_model_eventid.model_key
    ├──[CASCADE]──► tc_model_reportid.model_key
    ├──[CASCADE]──► tc_model_variableid.model_key
    ├──[CASCADE]──► tc_model_mdf.model_key
    ├──[CASCADE]──► tc_model_secs_message.model_key
    ├──[CASCADE]──► tc_model_socket_message.model_key
    ├──[CASCADE]──► tc_model_workflow.model_key
    ├──[CASCADE]──► tc_model_dcop_item.model_key
    └──[RESTRICT]── tc_eqp.model_key                  (→ tc-eqp.md)
```

---

## 5. 주요 쿼리 패턴

### 5-1. 활성화된 모델 전체 조회

```sql
SELECT model_key, model_name, model_version, comm_interface, maker
  FROM tc_model
 WHERE status = 'ACTIVE'
 ORDER BY model_name, model_version;
```

### 5-2. 특정 모델의 활성화된 Event ID 전체 조회

```sql
SELECT event_id, report_id
  FROM tc_model_eventid
 WHERE model_key = :model_key
   AND enabled   = true;
```

### 5-3. 특정 메시지에 매핑된 워크플로우 조회

```sql
SELECT workflow_name, action_name, action_data_index
  FROM tc_model_workflow
 WHERE model_key    = :model_key
   AND message_name = :message_name;
```

### 5-4. DCOP 수집 항목을 순서대로 조회

```sql
SELECT dcop_item_name, event_id, variable_id, collection_rule, calculation_rule
  FROM tc_model_dcop_item
 WHERE model_key = :model_key
 ORDER BY order_rule ASC NULLS LAST;
```
