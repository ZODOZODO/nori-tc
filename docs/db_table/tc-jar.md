# JAR 플러그인(JAR) 테이블 명세

> **파일 경로** : `nori-tc/docs/db/table/tc-jar.md`
> **스키마**    : `public`
> **작성 기준** : PostgreSQL DDL 역설계
> **테이블 수** : 2개

---

## 1. 도메인 개요

### 1-1. 테이블 목록

| No | 테이블명 | 설명 |
|:--:|---|---|
| 1 | `tc_jar_business` | 장비별 Business 레이어 플러그인 JAR 파일 저장 |
| 2 | `tc_jar_gateway` | 장비별 Gateway 레이어 플러그인 JAR 파일 저장 |

### 1-2. 설계 원칙

장비(`tc_eqp`)별로 커스텀 비즈니스 로직과 통신 처리 로직을 JAR 플러그인으로 분리하여 DB에 저장한다. `tc_jar_business` 는 `tc-business-core-app` 이, `tc_jar_gateway` 는 `tc-comm-gateway-app` 이 런타임에 동적으로 로드하여 실행한다. 두 테이블 모두 `tc_eqp` 와 1:1 관계이며 `eqp_key` 가 PK이자 FK다. 장비 삭제 시 `ON DELETE CASCADE` 로 JAR 파일도 함께 삭제된다.

---

## 2. 테이블 관계 다이어그램

```
tc_eqp.eqp_key
    ├──[CASCADE]──► tc_jar_business.eqp_key
    └──[CASCADE]──► tc_jar_gateway.eqp_key
```

---

## 3. 테이블 상세 명세

### 3-1. `tc_jar_business`

#### 개요

장비별 Business 레이어 플러그인 JAR 파일을 저장한다. `tc-business-core-app` 이 이 테이블에서 장비에 해당하는 JAR 를 로드하여 비즈니스 로직을 처리한다. `tc_eqp` 와 1:1 관계이며 `eqp_key` 가 PK이자 FK다.

#### 컬럼 명세

| 컬럼명 | 타입 | NOT NULL | 기본값 | 설명 |
|---|---|:---:|---|---|
| `eqp_key` | `bigint` | ✅ | - | PK + FK → `tc_eqp.eqp_key` |
| `jar_file_name` | `varchar(255)` | ✅ | - | JAR 파일 이름 |
| `jar_file` | `bytea` | ✅ | - | JAR 파일 바이너리 데이터 |
| `created_at` | `timestamptz(3)` | ✅ | `CURRENT_TIMESTAMP` | 생성 일시 |
| `updated_at` | `timestamptz(3)` | ✅ | `CURRENT_TIMESTAMP` | 수정 일시 |
| `created_by` | `varchar(50)` | ✅ | `'SYSTEM'` | 생성 주체 |
| `updated_by` | `varchar(50)` | ✅ | `'SYSTEM'` | 수정 주체 |

#### 인덱스 명세

| 인덱스명 | 컬럼 | 종류 | 목적 |
|---|---|---|---|
| `pk_tc_jar_business` | `eqp_key` | PRIMARY KEY | PK 단건 조회 |

#### 제약 조건

```
없음 (NOT NULL 제약만 존재)
```

#### 외래 키

| FK명 | 컬럼 | 참조 테이블.컬럼 | 삭제 정책 |
|---|---|---|---|
| `fk_tc_jar_business_eqp_key__tc_eqp` | `eqp_key` | `tc_eqp.eqp_key` | CASCADE |

---

### 3-2. `tc_jar_gateway`

#### 개요

장비별 Gateway 레이어 플러그인 JAR 파일을 저장한다. `tc-comm-gateway-app` 이 이 테이블에서 장비에 해당하는 JAR 를 로드하여 통신 메시지 파싱 및 처리 로직을 실행한다. `tc_eqp` 와 1:1 관계이며 `eqp_key` 가 PK이자 FK다.

#### 컬럼 명세

| 컬럼명 | 타입 | NOT NULL | 기본값 | 설명 |
|---|---|:---:|---|---|
| `eqp_key` | `bigint` | ✅ | - | PK + FK → `tc_eqp.eqp_key` |
| `jar_file_name` | `varchar(255)` | ✅ | - | JAR 파일 이름 |
| `jar_file` | `bytea` | ✅ | - | JAR 파일 바이너리 데이터 |
| `created_at` | `timestamptz(3)` | ✅ | `CURRENT_TIMESTAMP` | 생성 일시 |
| `updated_at` | `timestamptz(3)` | ✅ | `CURRENT_TIMESTAMP` | 수정 일시 |
| `created_by` | `varchar(50)` | ✅ | `'SYSTEM'` | 생성 주체 |
| `updated_by` | `varchar(50)` | ✅ | `'SYSTEM'` | 수정 주체 |

#### 인덱스 명세

| 인덱스명 | 컬럼 | 종류 | 목적 |
|---|---|---|---|
| `pk_tc_jar_gateway` | `eqp_key` | PRIMARY KEY | PK 단건 조회 |

#### 제약 조건

```
없음 (NOT NULL 제약만 존재)
```

#### 외래 키

| FK명 | 컬럼 | 참조 테이블.컬럼 | 삭제 정책 |
|---|---|---|---|
| `fk_tc_jar_gateway_eqp_key__tc_eqp` | `eqp_key` | `tc_eqp.eqp_key` | CASCADE |

---

## 4. FK 관계 요약

```
tc_eqp.eqp_key
    ├──[CASCADE]──► tc_jar_business.eqp_key
    └──[CASCADE]──► tc_jar_gateway.eqp_key
```

---

## 5. 주요 쿼리 패턴

### 5-1. 장비의 Business JAR 파일 조회

```sql
SELECT jar_file_name, jar_file, updated_at
  FROM tc_jar_business
 WHERE eqp_key = :eqp_key;
```

### 5-2. 장비의 Gateway JAR 파일 조회

```sql
SELECT jar_file_name, jar_file, updated_at
  FROM tc_jar_gateway
 WHERE eqp_key = :eqp_key;
```

### 5-3. JAR 파일이 등록된 장비 목록 조회 (Business + Gateway 모두 존재하는 장비)

```sql
SELECT e.eqp_key, e.eqp_id, e.comm_interface,
       b.jar_file_name AS business_jar,
       g.jar_file_name AS gateway_jar
  FROM tc_eqp          e
  JOIN tc_jar_business b ON b.eqp_key = e.eqp_key
  JOIN tc_jar_gateway  g ON g.eqp_key = e.eqp_key
 WHERE e.enabled = true
 ORDER BY e.eqp_id;
```

### 5-4. Business JAR 파일 등록 또는 갱신 (UPSERT)

```sql
INSERT INTO tc_jar_business (eqp_key, jar_file_name, jar_file, updated_by)
VALUES (:eqp_key, :jar_file_name, :jar_file, :updated_by)
ON CONFLICT (eqp_key)
DO UPDATE SET jar_file_name = EXCLUDED.jar_file_name,
              jar_file      = EXCLUDED.jar_file,
              updated_at    = NOW(),
              updated_by    = EXCLUDED.updated_by;
```
