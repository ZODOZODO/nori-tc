# 장비(EQP) 테이블 명세

> **파일 경로** : `nori-tc/docs/db/table/tc-eqp.md`
> **스키마**    : `public`
> **작성 기준** : PostgreSQL DDL 역설계
> **테이블 수** : 10개

---

## 1. 도메인 개요

### 1-1. 테이블 목록

| No | 테이블명 | 설명 |
|:--:|---|---|
| 1 | `tc_eqp` | 장비 원장. 모든 장비 관련 테이블의 최상위 부모 |
| 2 | `tc_eqp_global` | 장비별 전역 Key-Value 파라미터 |
| 3 | `tc_eqp_hsms` | HSMS 통신 방식 장비의 세부 설정 (1:1) |
| 4 | `tc_eqp_socket` | Socket 통신 방식 장비의 세부 설정 (1:1) |
| 5 | `tc_eqp_socket_protocol_type` | Socket 프로토콜 타입 코드 마스터 |
| 6 | `tc_eqp_log` | 장비별 로그 설정 (1:1) |
| 7 | `tc_eqp_param` | 장비별 버전 관리 런타임 파라미터 |
| 8 | `tc_eqp_port_status` | 장비 포트별 현재 상태 |
| 9 | `tc_eqp_state` | 장비 현재 상태 스냅샷 (1:1, 항상 최신 1건 유지) |
| 10 | `tc_eqp_state_hist` | 장비 상태 변경 이력 (1:N, 누적 기록) |

### 1-2. 설계 원칙

`tc_eqp` 가 모든 장비 관련 테이블의 루트이며, `eqp_key` 를 기준으로 모든 자식 테이블이 연결된다. 통신 방식(`comm_interface`)이 `HSMS` 이면 `tc_eqp_hsms`, `SOCKET` 이면 `tc_eqp_socket` 에 통신 상세 설정이 1:1로 존재한다. 장비 삭제 시 모든 하위 테이블은 `ON DELETE CASCADE` 처리된다. 단, `tc_work` 는 삭제 정책 없이 단순 FK 참조이므로 작업 이력이 존재하는 장비는 삭제할 수 없다.

---

## 2. 테이블 관계 다이어그램

```
tc_eqp_socket_protocol_type ──[RESTRICT]──► tc_eqp_socket
                                                   │
tc_model ──[RESTRICT]──► tc_eqp ◄──────────────────┘
                            │
                            ├──[CASCADE]──► tc_eqp_global
                            ├──[CASCADE]──► tc_eqp_hsms
                            ├──[CASCADE]──► tc_eqp_log
                            ├──[CASCADE]──► tc_eqp_param
                            ├──[CASCADE]──► tc_eqp_port_status
                            ├──[CASCADE]──► tc_eqp_state
                            ├──[CASCADE]──► tc_eqp_state_hist
                            ├──[CASCADE]──► tc_jar_business    (→ tc-jar.md)
                            ├──[CASCADE]──► tc_jar_gateway     (→ tc-jar.md)
                            └──[RESTRICT]── tc_work            (→ tc-work.md)
```

---

## 3. 테이블 상세 명세

### 3-1. `tc_eqp`

#### 개요

시스템에 등록된 장비의 기본 식별 정보와 통신 방식을 관리하는 최상위 테이블이다. `comm_interface` 값에 따라 `tc_eqp_hsms` 또는 `tc_eqp_socket` 중 하나에 통신 상세 설정이 1:1로 존재하며, 공통 연결 모드(`comm_mode`)와 Gateway 라우팅 파티션(`route_partition`)도 이 테이블에서 함께 관리한다.

#### 컬럼 명세

| 컬럼명 | 타입 | NOT NULL | 기본값 | 설명 |
|---|---|:---:|---|---|
| `eqp_key` | `bigint` | ✅ | identity (자동 증가) | PK |
| `eqp_id` | `varchar(64)` | ✅ | - | 장비 식별 ID. 예: `EQP-001` |
| `comm_interface` | `varchar(16)` | ✅ | - | 통신 방식. `HSMS` / `SOCKET` |
| `comm_mode` | `varchar(10)` | ✅ | - | 공통 연결 모드. `ACTIVE` / `PASSIVE` |
| `route_partition` | `integer` | - | - | Gateway 대상 토픽 고정 라우팅 파티션 번호 (NULL 허용) |
| `eqp_ip` | `varchar(45)` | ✅ | - | 장비 IP 주소 (IPv4/IPv6) |
| `eqp_port` | `integer` | ✅ | - | 장비 포트 번호. 1 ~ 65535 |
| `model_key` | `bigint` | ✅ | - | FK → `tc_model.model_key` |
| `enabled` | `boolean` | ✅ | `true` | 장비 활성 여부 |
| `created_at` | `timestamptz(3)` | ✅ | `CURRENT_TIMESTAMP` | 생성 일시 |
| `updated_at` | `timestamptz(3)` | ✅ | `CURRENT_TIMESTAMP` | 수정 일시 |
| `created_by` | `varchar(50)` | ✅ | `'SYSTEM'` | 생성 주체 |
| `updated_by` | `varchar(50)` | ✅ | `'SYSTEM'` | 수정 주체 |

#### 인덱스 명세

| 인덱스명 | 컬럼 | 종류 | 목적 |
|---|---|---|---|
| `pk_tc_eqp` | `eqp_key` | PRIMARY KEY | PK 단건 조회 |
| `uk_tc_eqp_eqp_id` | `eqp_id` | UNIQUE | ID 중복 방지 |
| `ix_tc_eqp_comm_interface` | `comm_interface` | INDEX | 통신 방식별 장비 목록 조회 |
| `ix_tc_eqp_enabled` | `enabled` | INDEX | 활성 장비 필터링 |
| `ix_tc_eqp_route_partition_enabled` | `route_partition`, `enabled` | INDEX | 라우팅 파티션 + 활성 장비 조회 |
| `ix_tc_eqp_eqp_ip_port` | `eqp_ip`, `eqp_port` | INDEX | IP+포트 기반 장비 조회 |
| `ix_tc_eqp_model_key` | `model_key` | INDEX | 모델별 장비 목록 조회 |

#### 제약 조건

| 제약명 | 조건 |
|---|---|
| `ck_tc_eqp_comm_interface` | `comm_interface IN ('HSMS', 'SOCKET')` |
| `ck_tc_eqp_comm_mode` | `comm_mode IN ('ACTIVE', 'PASSIVE')` |
| `ck_tc_eqp_route_partition` | `route_partition IS NULL OR route_partition >= 0` |
| `ck_tc_eqp_eqp_port` | `eqp_port >= 1 AND eqp_port <= 65535` |
| `ck_tc_eqp_enabled` | `enabled IN (true, false)` |

#### 외래 키

| FK명 | 컬럼 | 참조 테이블.컬럼 | 삭제 정책 |
|---|---|---|---|
| `fk_tc_eqp_model_key__tc_model` | `model_key` | `tc_model.model_key` | RESTRICT (기본값) |

---

### 3-2. `tc_eqp_global`

#### 개요

장비별 Key-Value 형태의 전역 설정 파라미터를 저장한다. 동일 장비에서 같은 `param_name` 은 UNIQUE 제약으로 중복을 방지한다.

#### 컬럼 명세

| 컬럼명 | 타입 | NOT NULL | 기본값 | 설명 |
|---|---|:---:|---|---|
| `eqp_global_key` | `bigint` | ✅ | identity (자동 증가) | PK |
| `eqp_key` | `bigint` | ✅ | - | FK → `tc_eqp.eqp_key` |
| `param_name` | `varchar(100)` | ✅ | - | 파라미터 이름 |
| `param_value` | `text` | - | - | 파라미터 값 (NULL 허용) |
| `updated_at` | `timestamptz(3)` | ✅ | `CURRENT_TIMESTAMP` | 수정 일시 |

#### 인덱스 명세

| 인덱스명 | 컬럼 | 종류 | 목적 |
|---|---|---|---|
| `pk_tc_eqp_global` | `eqp_global_key` | PRIMARY KEY | PK 단건 조회 |
| `uk_tc_eqp_global_eqp_key_param_name` | `eqp_key`, `param_name` | UNIQUE | 장비+파라미터명 중복 방지 |
| `ix_tc_eqp_global_eqp_key` | `eqp_key` | INDEX | 장비별 파라미터 전체 조회 |

#### 제약 조건

```
없음 (NOT NULL 및 UNIQUE 제약만 존재)
```

#### 외래 키

| FK명 | 컬럼 | 참조 테이블.컬럼 | 삭제 정책 |
|---|---|---|---|
| `fk_tc_eqp_global_eqp_key__tc_eqp` | `eqp_key` | `tc_eqp.eqp_key` | CASCADE |

---

### 3-3. `tc_eqp_hsms`

#### 개요

`comm_interface = 'HSMS'` 인 장비의 SEMI E37 HSMS 프로토콜 세부 설정을 저장한다. `tc_eqp` 와 1:1 관계이며 `eqp_key` 가 PK이자 FK다. 연결 모드(`ACTIVE`/`PASSIVE`)는 `tc_eqp.comm_mode`에서 공통 관리한다.

| 타이머 컬럼 | 기본값 | SEMI E37 정의 |
|---|---|---|
| `t3_timeout` | 45초 | Reply 메시지 수신 대기 타임아웃 |
| `t5_timeout` | 10초 | Connection Separation 대기 시간 |
| `t6_timeout` | 5초 | Control Transaction 응답 타임아웃 |
| `t7_timeout` | 10초 | Not Selected 상태 유지 타임아웃 |
| `t8_timeout` | 5초 | Network Intercharacter 타임아웃 |

#### 컬럼 명세

| 컬럼명 | 타입 | NOT NULL | 기본값 | 설명 |
|---|---|:---:|---|---|
| `eqp_key` | `bigint` | ✅ | - | PK + FK → `tc_eqp.eqp_key` |
| `device_id` | `integer` | ✅ | - | HSMS Device ID. 0 ~ 32767 |
| `t3_timeout` | `integer` | ✅ | `45` | T3 타임아웃 (초, > 0) |
| `t5_timeout` | `integer` | ✅ | `10` | T5 타임아웃 (초, > 0) |
| `t6_timeout` | `integer` | ✅ | `5` | T6 타임아웃 (초, > 0) |
| `t7_timeout` | `integer` | ✅ | `10` | T7 타임아웃 (초, > 0) |
| `t8_timeout` | `integer` | ✅ | `5` | T8 타임아웃 (초, > 0) |
| `link_test_enabled` | `boolean` | ✅ | `true` | LinkTest 활성화 여부 |
| `link_test_interval` | `integer` | ✅ | `60` | LinkTest 전송 간격 (초, > 0) |
| `max_msg_bytes` | `bigint` | ✅ | `10485760` | 최대 메시지 크기 (기본 10MB, > 0) |
| `created_at` | `timestamptz(3)` | ✅ | `CURRENT_TIMESTAMP` | 생성 일시 |
| `updated_at` | `timestamptz(3)` | ✅ | `CURRENT_TIMESTAMP` | 수정 일시 |

#### 인덱스 명세

| 인덱스명 | 컬럼 | 종류 | 목적 |
|---|---|---|---|
| `pk_tc_eqp_hsms` | `eqp_key` | PRIMARY KEY | PK 단건 조회 |

#### 제약 조건

| 제약명 | 조건 |
|---|---|
| `ck_tc_eqp_hsms_device_id` | `device_id >= 0 AND device_id <= 32767` |
| `ck_tc_eqp_hsms_t3_timeout` | `t3_timeout > 0` |
| `ck_tc_eqp_hsms_t5_timeout` | `t5_timeout > 0` |
| `ck_tc_eqp_hsms_t6_timeout` | `t6_timeout > 0` |
| `ck_tc_eqp_hsms_t7_timeout` | `t7_timeout > 0` |
| `ck_tc_eqp_hsms_t8_timeout` | `t8_timeout > 0` |
| `ck_tc_eqp_hsms_link_test_interval` | `link_test_interval > 0` |
| `ck_tc_eqp_hsms_max_msg_bytes` | `max_msg_bytes > 0` |
| `ck_tc_eqp_hsms_link_test_enabled` | `link_test_enabled IN (true, false)` |

#### 외래 키

| FK명 | 컬럼 | 참조 테이블.컬럼 | 삭제 정책 |
|---|---|---|---|
| `fk_tc_eqp_hsms_eqp_key__tc_eqp` | `eqp_key` | `tc_eqp.eqp_key` | CASCADE |

---

### 3-4. `tc_eqp_socket`

#### 개요

`comm_interface = 'SOCKET'` 인 장비의 TCP Socket 프로토콜 세부 설정을 저장한다. `tc_eqp` 와 1:1 관계이며 `eqp_key` 가 PK이자 FK다. 프로토콜 타입은 `tc_eqp_socket_protocol_type` 코드 테이블을 참조한다. 연결 모드(`ACTIVE`/`PASSIVE`)는 `tc_eqp.comm_mode`에서 공통 관리한다.

#### 컬럼 명세

| 컬럼명 | 타입 | NOT NULL | 기본값 | 설명 |
|---|---|:---:|---|---|
| `eqp_key` | `bigint` | ✅ | - | PK + FK → `tc_eqp.eqp_key` |
| `socket_protocol_type` | `varchar(32)` | ✅ | - | FK → `tc_eqp_socket_protocol_type.socket_protocol_type` |
| `charset` | `varchar(20)` | ✅ | `'UTF-8'` | 문자 인코딩 |
| `heartbeat_enabled` | `boolean` | ✅ | `true` | Heartbeat 활성화 여부 |
| `heartbeat_interval` | `integer` | ✅ | `30` | Heartbeat 전송 간격 (초, >= 0) |
| `read_timeout` | `integer` | ✅ | `0` | 읽기 타임아웃 (초, 0 = 무제한, >= 0) |
| `write_timeout` | `integer` | ✅ | `0` | 쓰기 타임아웃 (초, 0 = 무제한, >= 0) |
| `max_frame_size_bytes` | `integer` | ✅ | `8192` | 최대 프레임 크기 (바이트, > 0) |
| `keep_alive_enabled` | `boolean` | ✅ | `true` | TCP Keep-Alive 활성화 여부 |
| `created_at` | `timestamptz(3)` | ✅ | `CURRENT_TIMESTAMP` | 생성 일시 |
| `updated_at` | `timestamptz(3)` | ✅ | `CURRENT_TIMESTAMP` | 수정 일시 |

#### 인덱스 명세

| 인덱스명 | 컬럼 | 종류 | 목적 |
|---|---|---|---|
| `pk_tc_eqp_socket` | `eqp_key` | PRIMARY KEY | PK 단건 조회 |
| `ix_tc_eqp_socket_socket_protocol_type` | `socket_protocol_type` | INDEX | 프로토콜 타입별 장비 조회 |

#### 제약 조건

| 제약명 | 조건 |
|---|---|
| `ck_tc_eqp_socket_heartbeat_interval` | `heartbeat_interval >= 0` |
| `ck_tc_eqp_socket_read_timeout` | `read_timeout >= 0` |
| `ck_tc_eqp_socket_write_timeout` | `write_timeout >= 0` |
| `ck_tc_eqp_socket_max_frame_size_bytes` | `max_frame_size_bytes > 0` |
| `ck_tc_eqp_socket_heartbeat_enabled` | `heartbeat_enabled IN (true, false)` |
| `ck_tc_eqp_socket_keep_alive_enabled` | `keep_alive_enabled IN (true, false)` |

#### 외래 키

| FK명 | 컬럼 | 참조 테이블.컬럼 | 삭제 정책 |
|---|---|---|---|
| `fk_tc_eqp_socket_eqp_key__tc_eqp` | `eqp_key` | `tc_eqp.eqp_key` | CASCADE |
| `fk_tc_eqp_socket_socket_protocol_type__tc_eqp_socket_protocol_t` | `socket_protocol_type` | `tc_eqp_socket_protocol_type.socket_protocol_type` | RESTRICT (기본값) |

---

### 3-5. `tc_eqp_socket_protocol_type`

#### 개요

Socket 통신에서 사용하는 프로토콜 타입의 코드 마스터 테이블이다. 메시지 파싱 규칙(시작/종료 규칙, 정규식)을 정의하며, `tc_eqp_socket` 에서 FK로 참조한다. 문자열 코드 값이 PK다.

#### 컬럼 명세

| 컬럼명 | 타입 | NOT NULL | 기본값 | 설명 |
|---|---|:---:|---|---|
| `socket_protocol_type` | `varchar(32)` | ✅ | - | PK. 프로토콜 타입 코드 |
| `socket_protocol_type_name` | `varchar(100)` | ✅ | - | 프로토콜 타입 표시 이름 |
| `parse_start_rule` | `varchar(1000)` | - | - | 메시지 시작 파싱 규칙 |
| `parse_end_rule` | `varchar(1000)` | - | - | 메시지 종료 파싱 규칙 |
| `parse_regex` | `varchar(1000)` | - | - | 메시지 파싱 정규표현식 |
| `description` | `varchar(1000)` | - | - | 프로토콜 설명 |

#### 인덱스 명세

| 인덱스명 | 컬럼 | 종류 | 목적 |
|---|---|---|---|
| `pk_tc_eqp_socket_protocol_type` | `socket_protocol_type` | PRIMARY KEY | PK 단건 조회 |

#### 제약 조건

```
없음 (NOT NULL 제약만 존재)
```

#### 외래 키

```
없음 (이 테이블은 참조 대상이며 다른 테이블에서 참조함)
```

---

### 3-6. `tc_eqp_log`

#### 개요

장비별 로그 레벨, 보존 기간, 저장 경로를 관리하는 테이블이다. `tc_eqp` 와 1:1 관계이며 `eqp_key` 가 PK이자 FK다.

#### 컬럼 명세

| 컬럼명 | 타입 | NOT NULL | 기본값 | 설명 |
|---|---|:---:|---|---|
| `eqp_key` | `bigint` | ✅ | - | PK + FK → `tc_eqp.eqp_key` |
| `log_level` | `varchar(10)` | ✅ | `'INFO'` | 로그 레벨. `TRACE` / `DEBUG` / `INFO` / `WARN` / `ERROR` |
| `log_retention_days` | `integer` | ✅ | `30` | 로그 보존 기간 (일, >= 1) |
| `log_path` | `varchar(1000)` | - | - | 로그 파일 저장 경로. NULL 이면 기본 경로 사용 |
| `updated_at` | `timestamptz(3)` | ✅ | `CURRENT_TIMESTAMP` | 수정 일시 |

#### 인덱스 명세

| 인덱스명 | 컬럼 | 종류 | 목적 |
|---|---|---|---|
| `pk_tc_eqp_log` | `eqp_key` | PRIMARY KEY | PK 단건 조회 |

#### 제약 조건

| 제약명 | 조건 |
|---|---|
| `ck_tc_eqp_log_log_level` | `log_level IN ('TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR')` |
| `ck_tc_eqp_log_log_retention_days` | `log_retention_days >= 1` |

#### 외래 키

| FK명 | 컬럼 | 참조 테이블.컬럼 | 삭제 정책 |
|---|---|---|---|
| `fk_tc_eqp_log_eqp_key__tc_eqp` | `eqp_key` | `tc_eqp.eqp_key` | CASCADE |

---

### 3-7. `tc_eqp_param`

#### 개요

장비별 런타임 파라미터를 버전 단위로 관리한다. 동일한 `param_name` 이라도 `param_version` 이 다르면 별도 행으로 저장되어 버전 이력을 유지할 수 있다.

#### 컬럼 명세

| 컬럼명 | 타입 | NOT NULL | 기본값 | 설명 |
|---|---|:---:|---|---|
| `eqp_param_key` | `bigint` | ✅ | identity (자동 증가) | PK |
| `eqp_key` | `bigint` | ✅ | - | FK → `tc_eqp.eqp_key` |
| `param_name` | `varchar(100)` | ✅ | - | 파라미터 이름 |
| `param_version` | `varchar(100)` | ✅ | - | 파라미터 버전 |
| `param_value` | `text` | - | - | 파라미터 값 |
| `updated_at` | `timestamptz(3)` | ✅ | `CURRENT_TIMESTAMP` | 수정 일시 |

#### 인덱스 명세

| 인덱스명 | 컬럼 | 종류 | 목적 |
|---|---|---|---|
| `pk_tc_eqp_param` | `eqp_param_key` | PRIMARY KEY | PK 단건 조회 |
| `uk_tc_eqp_param_eqp_key_param_name_param_version` | `eqp_key`, `param_name`, `param_version` | UNIQUE | 장비+파라미터+버전 중복 방지 |
| `ix_tc_eqp_param_eqp_key` | `eqp_key` | INDEX | 장비별 파라미터 전체 조회 |
| `ix_tc_eqp_param_param_name` | `param_name` | INDEX | 파라미터명으로 검색 |

#### 제약 조건

```
없음 (NOT NULL 및 UNIQUE 제약만 존재)
```

#### 외래 키

| FK명 | 컬럼 | 참조 테이블.컬럼 | 삭제 정책 |
|---|---|---|---|
| `fk_tc_eqp_param_eqp_key__tc_eqp` | `eqp_key` | `tc_eqp.eqp_key` | CASCADE |

---

### 3-8. `tc_eqp_port_status`

#### 개요

장비 각 포트의 현재 상태를 저장한다. 포트 타입, 포트 상태, 캐리어 정보를 실시간으로 반영하며 장비+포트 조합이 UNIQUE다. Nullable 컬럼은 NULL 허용 CHECK 제약으로 값 범위를 제어한다.

#### 컬럼 명세

| 컬럼명 | 타입 | NOT NULL | 기본값 | 설명 |
|---|---|:---:|---|---|
| `eqp_port_status_key` | `bigint` | ✅ | identity (자동 증가) | PK |
| `eqp_key` | `bigint` | ✅ | - | FK → `tc_eqp.eqp_key` |
| `port_id` | `varchar(20)` | ✅ | - | 포트 식별 ID |
| `port_type` | `varchar(20)` | - | - | 포트 타입. `LOAD_PORT` / `UNLOAD_PORT` / `INTERNAL_BUFFER` / `OTHER` |
| `port_state` | `varchar(20)` | - | - | 포트 상태. `EMPTY` / `LOADED` / `READY_TO_LOAD` / `DOWN` / `IN_SERVICE` / `UNKNOWN` |
| `carrier_id` | `varchar(64)` | - | - | 포트에 올려진 캐리어 ID |
| `carrier_type` | `varchar(20)` | - | - | 캐리어 타입. `FOUP` / `CASSETTE` / `WAFER_BOX` / `TRAY` / `OTHER` |
| `carrier_state` | `varchar(20)` | - | - | 캐리어 상태. `CLAMPED` / `UNCLAMPED` / `OPENED` / `CLOSED` / `UNKNOWN` |
| `updated_at` | `timestamptz(3)` | ✅ | `CURRENT_TIMESTAMP` | 상태 갱신 일시 |

#### 인덱스 명세

| 인덱스명 | 컬럼 | 종류 | 목적 |
|---|---|---|---|
| `pk_tc_eqp_port_status` | `eqp_port_status_key` | PRIMARY KEY | PK 단건 조회 |
| `uk_tc_eqp_port_status_eqp_key_port_id` | `eqp_key`, `port_id` | UNIQUE | 장비+포트 중복 방지 |
| `ix_tc_eqp_port_status_eqp_key` | `eqp_key` | INDEX | 장비별 포트 목록 조회 |
| `ix_tc_eqp_port_status_port_id` | `port_id` | INDEX | 포트 ID로 조회 |
| `ix_tc_eqp_port_status_carrier_id` | `carrier_id` | INDEX | 캐리어 ID로 포트 위치 역추적 |

#### 제약 조건

| 제약명 | 조건 |
|---|---|
| `ck_tc_eqp_port_status_port_type` | `port_type IS NULL OR port_type IN ('LOAD_PORT', 'UNLOAD_PORT', 'INTERNAL_BUFFER', 'OTHER')` |
| `ck_tc_eqp_port_status_port_state` | `port_state IS NULL OR port_state IN ('EMPTY', 'LOADED', 'READY_TO_LOAD', 'DOWN', 'IN_SERVICE', 'UNKNOWN')` |
| `ck_tc_eqp_port_status_carrier_type` | `carrier_type IS NULL OR carrier_type IN ('FOUP', 'CASSETTE', 'WAFER_BOX', 'TRAY', 'OTHER')` |
| `ck_tc_eqp_port_status_carrier_state` | `carrier_state IS NULL OR carrier_state IN ('CLAMPED', 'UNCLAMPED', 'OPENED', 'CLOSED', 'UNKNOWN')` |

#### 외래 키

| FK명 | 컬럼 | 참조 테이블.컬럼 | 삭제 정책 |
|---|---|---|---|
| `fk_tc_eqp_port_status_eqp_key__tc_eqp` | `eqp_key` | `tc_eqp.eqp_key` | CASCADE |

---

### 3-9. `tc_eqp_state`

#### 개요

장비의 현재 상태를 항상 최신 1건으로 유지하는 스냅샷 테이블이다. `tc_eqp` 와 1:1 관계이며 `eqp_key` 가 PK이자 FK다. 상태 변경 시 이 테이블을 UPDATE 하고, `tc_eqp_state_hist` 에 변경 이력을 INSERT 한다.

#### 컬럼 명세

| 컬럼명 | 타입 | NOT NULL | 기본값 | 설명 |
|---|---|:---:|---|---|
| `eqp_key` | `bigint` | ✅ | - | PK + FK → `tc_eqp.eqp_key` |
| `control_state` | `varchar(20)` | - | - | 제어 상태. `OFFLINE` / `LOCAL` / `REMOTE` |
| `eqp_state` | `varchar(20)` | - | - | 운영 상태. `IDLE` / `RUN` / `DOWN` / `MAINTENANCE` / `PAUSE` |
| `since_at` | `timestamptz(3)` | - | - | 현재 상태 진입 일시 |
| `reason_code` | `varchar(50)` | - | - | 상태 전이 사유 코드 |
| `reason_detail` | `text` | - | - | 상태 전이 상세 사유 |
| `updated_at` | `timestamptz(3)` | ✅ | `CURRENT_TIMESTAMP` | 상태 갱신 일시 |

#### 인덱스 명세

| 인덱스명 | 컬럼 | 종류 | 목적 |
|---|---|---|---|
| `pk_tc_eqp_state` | `eqp_key` | PRIMARY KEY | PK 단건 조회 |

#### 제약 조건

| 제약명 | 조건 |
|---|---|
| `ck_tc_eqp_state_control_state` | `control_state IS NULL OR control_state IN ('OFFLINE', 'LOCAL', 'REMOTE')` |
| `ck_tc_eqp_state_eqp_state` | `eqp_state IS NULL OR eqp_state IN ('IDLE', 'RUN', 'DOWN', 'MAINTENANCE', 'PAUSE')` |

#### 외래 키

| FK명 | 컬럼 | 참조 테이블.컬럼 | 삭제 정책 |
|---|---|---|---|
| `fk_tc_eqp_state_eqp_key__tc_eqp` | `eqp_key` | `tc_eqp.eqp_key` | CASCADE |

---

### 3-10. `tc_eqp_state_hist`

#### 개요

장비의 모든 상태 변경을 시계열로 누적 기록하는 이력 테이블이다. `state_type` 으로 운영 상태(`OPER`)와 연결 상태(`CONN`) 이력을 구분한다.

#### 컬럼 명세

| 컬럼명 | 타입 | NOT NULL | 기본값 | 설명 |
|---|---|:---:|---|---|
| `state_hist_key` | `bigint` | ✅ | identity (자동 증가) | PK |
| `eqp_key` | `bigint` | ✅ | - | FK → `tc_eqp.eqp_key` |
| `state_type` | `varchar(10)` | ✅ | - | 상태 유형. `OPER`(운영 상태) / `CONN`(연결 상태) |
| `from_state` | `varchar(50)` | - | - | 변경 전 상태 |
| `to_state` | `varchar(50)` | - | - | 변경 후 상태 |
| `changed_at` | `timestamptz(3)` | ✅ | `CURRENT_TIMESTAMP` | 상태 변경 일시 |
| `reason_code` | `varchar(50)` | - | - | 변경 사유 코드 |
| `reason_detail` | `text` | - | - | 변경 상세 사유 |

#### 인덱스 명세

| 인덱스명 | 컬럼 | 종류 | 목적 |
|---|---|---|---|
| `pk_tc_eqp_state_hist` | `state_hist_key` | PRIMARY KEY | PK 단건 조회 |
| `ix_tc_eqp_state_hist_eqp_key_changed_at` | `eqp_key`, `changed_at` | INDEX | 장비별 상태 이력 시계열 조회 |
| `ix_tc_eqp_state_hist_state_type_changed_at` | `state_type`, `changed_at` | INDEX | 상태 타입별 이력 조회 |

#### 제약 조건

| 제약명 | 조건 |
|---|---|
| `ck_tc_eqp_state_hist_state_type` | `state_type IN ('OPER', 'CONN')` |

#### 외래 키

| FK명 | 컬럼 | 참조 테이블.컬럼 | 삭제 정책 |
|---|---|---|---|
| `fk_tc_eqp_state_hist_eqp_key__tc_eqp` | `eqp_key` | `tc_eqp.eqp_key` | CASCADE |

---

## 4. FK 관계 요약

```
tc_model.model_key
    └──[RESTRICT]──► tc_eqp.model_key

tc_eqp.eqp_key
    ├──[CASCADE]──► tc_eqp_global.eqp_key
    ├──[CASCADE]──► tc_eqp_hsms.eqp_key
    ├──[CASCADE]──► tc_eqp_socket.eqp_key
    ├──[CASCADE]──► tc_eqp_log.eqp_key
    ├──[CASCADE]──► tc_eqp_param.eqp_key
    ├──[CASCADE]──► tc_eqp_port_status.eqp_key
    ├──[CASCADE]──► tc_eqp_state.eqp_key
    ├──[CASCADE]──► tc_eqp_state_hist.eqp_key
    ├──[CASCADE]──► tc_jar_business.eqp_key      (→ tc-jar.md)
    ├──[CASCADE]──► tc_jar_gateway.eqp_key       (→ tc-jar.md)
    └──[RESTRICT]── tc_work.eqp_key              (→ tc-work.md)

tc_eqp_socket_protocol_type.socket_protocol_type
    └──[RESTRICT]──► tc_eqp_socket.socket_protocol_type
```

---

## 5. 주요 쿼리 패턴

### 5-1. 활성화된 장비 전체 조회

```sql
SELECT e.eqp_key, e.eqp_id, e.comm_interface, e.eqp_ip, e.eqp_port,
       m.model_name, m.model_version
  FROM tc_eqp e
  JOIN tc_model m ON m.model_key = e.model_key
 WHERE e.enabled = true
 ORDER BY e.eqp_id;
```

### 5-2. 장비의 현재 상태 조회

```sql
SELECT e.eqp_id, s.control_state, s.eqp_state, s.since_at, s.reason_code
  FROM tc_eqp_state s
  JOIN tc_eqp e ON e.eqp_key = s.eqp_key
 WHERE s.eqp_key = :eqp_key;
```

### 5-3. 장비 상태 변경 이력 조회 (최근 100건)

```sql
SELECT state_type, from_state, to_state, changed_at, reason_code
  FROM tc_eqp_state_hist
 WHERE eqp_key = :eqp_key
 ORDER BY changed_at DESC
 LIMIT 100;
```

### 5-4. 캐리어 ID로 포트 위치 역추적

```sql
SELECT e.eqp_id, p.port_id, p.port_type, p.port_state, p.carrier_type, p.carrier_state
  FROM tc_eqp_port_status p
  JOIN tc_eqp e ON e.eqp_key = p.eqp_key
 WHERE p.carrier_id = :carrier_id;
```
