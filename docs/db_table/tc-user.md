# 사용자·인증·권한(User) 테이블 명세

> **파일 경로** : `nori-tc/docs/db/table/tc-user.md`
> **스키마**    : `public`
> **작성 기준** : PostgreSQL DDL 역설계
> **테이블 수** : 6개

---

## 1. 도메인 개요

### 1-1. 테이블 목록

| No | 테이블명 | 설명 |
|:--:|---|---|
| 1 | `tc_user_info` | 사용자 계정 원장. 모든 사용자 관련 테이블의 최상위 부모 |
| 2 | `tc_ui_auth_session` | 로그인 세션 토큰 관리 |
| 3 | `tc_user_group` | 그룹 정의 |
| 4 | `tc_user_group_member` | 사용자 ↔ 그룹 N:M 매핑 |
| 5 | `tc_ui_permission` | 권한 정의 (PAGE / API 리소스) |
| 6 | `tc_user_group_permission` | 그룹 ↔ 권한 N:M 매핑 |

### 1-2. 설계 원칙

권한은 사용자 개인이 아닌 **그룹 단위**로 부여하는 GBAC(Group-Based Access Control) 구조다. 사용자는 여러 그룹에 속할 수 있고, 그룹은 여러 권한을 가질 수 있다. 세션은 토큰 기반이며 서버 측 DB에서 유효성을 관리한다. 권한 리소스는 `PAGE`(화면 라우팅)와 `API`(백엔드 엔드포인트) 두 타입을 지원하며, 경로 매칭은 `EXACT` / `PREFIX` / `REGEX` 방식을 사용한다.

---

## 2. 테이블 관계 다이어그램

```
tc_user_info
    │
    ├──[RESTRICT]── tc_ui_auth_session.user_pk
    │
    └──[CASCADE]──► tc_user_group_member.user_pk
                          │
                          └──► tc_user_group ◄──── tc_user_group_permission.group_id [CASCADE]
                                                             │
                                                             └──► tc_ui_permission [CASCADE]
```

---

## 3. 테이블 상세 명세

### 3-1. `tc_user_info`

#### 개요

시스템에 등록된 모든 사용자의 식별 정보, 로그인 자격증명, 계정 상태를 관리하는 최상위 테이블이다. `user_id_norm` 은 로그인 ID를 소문자 변환 등으로 정규화한 값으로, 실제 로그인 조회 시 이 컬럼을 기준으로 조회한다.

#### 컬럼 명세

| 컬럼명 | 타입 | NOT NULL | 기본값 | 설명 |
|---|---|:---:|---|---|
| `user_pk` | `bigint` | ✅ | identity (자동 증가) | PK |
| `company` | `varchar(100)` | ✅ | - | 소속 회사 |
| `department` | `varchar(100)` | ✅ | - | 소속 부서 |
| `user_name` | `varchar(100)` | ✅ | - | 사용자 실명 |
| `user_id` | `varchar(50)` | ✅ | - | 로그인 ID 원본 (대소문자 유지, 화면 표시용) |
| `user_id_norm` | `varchar(50)` | ✅ | - | 로그인 ID 정규화 값 (소문자 변환). 실제 로그인 조회에 사용 |
| `password_hash` | `varchar(255)` | ✅ | - | 해시 처리된 비밀번호 (BCrypt) |
| `email` | `varchar(255)` | ✅ | - | 이메일 주소 (전역 UNIQUE) |
| `status` | `varchar(20)` | ✅ | `'ACTIVE'` | 계정 상태. `ACTIVE` / `LOCKED` / `DISABLED` / `DELETED` |
| `created_at` | `timestamptz(3)` | ✅ | `CURRENT_TIMESTAMP` | 생성 일시 |
| `updated_at` | `timestamptz(3)` | ✅ | `CURRENT_TIMESTAMP` | 수정 일시 |
| `created_by` | `varchar(50)` | ✅ | `'SYSTEM'` | 생성 주체 |
| `updated_by` | `varchar(50)` | ✅ | `'SYSTEM'` | 수정 주체 |

#### 인덱스 명세

| 인덱스명 | 컬럼 | 종류 | 목적 |
|---|---|---|---|
| `pk_tc_user_info` | `user_pk` | PRIMARY KEY | PK 단건 조회 |
| `uk_tc_user_info_user_id_norm` | `user_id_norm` | UNIQUE | 정규화 ID 중복 방지. 로그인 시 WHERE 조건으로 사용 |
| `uk_tc_user_info_email` | `email` | UNIQUE | 이메일 중복 방지 |
| `ix_tc_user_info_status` | `status` | INDEX | 상태별 사용자 목록 조회 |
| `ix_tc_user_info_company_department` | `company`, `department` | INDEX | 부서별 사용자 조회 |
| `ix_tc_user_info_email` | `email` | INDEX | 이메일 검색 |

#### 제약 조건

| 제약명 | 조건 |
|---|---|
| `ck_tc_user_info_status` | `status IN ('ACTIVE', 'LOCKED', 'DISABLED', 'DELETED')` |

#### 외래 키

```
없음 (이 테이블은 최상위 부모이며 다른 테이블에서 참조함)
```

---

### 3-2. `tc_ui_auth_session`

#### 개요

로그인 성공 시 발급되는 토큰 기반 서버 측 세션을 관리한다. 매 API 요청마다 `token`, `revoked`, `expires_at` 조건으로 유효성을 검증한다. `tc_user_info` 를 삭제 정책 없이 단순 FK 참조하므로 활성 세션이 있는 사용자는 삭제할 수 없다.

#### 컬럼 명세

| 컬럼명 | 타입 | NOT NULL | 기본값 | 설명 |
|---|---|:---:|---|---|
| `token` | `varchar(64)` | ✅ | - | PK. 랜덤 생성 세션 토큰 문자열 |
| `user_pk` | `bigint` | ✅ | - | FK → `tc_user_info.user_pk` |
| `issued_at` | `timestamptz(3)` | ✅ | `CURRENT_TIMESTAMP` | 토큰 최초 발급 일시 |
| `expires_at` | `timestamptz(3)` | ✅ | - | 토큰 만료 일시. 이 시각 이후 자동 무효 |
| `last_seen_at` | `timestamptz(3)` | - | - | 마지막 요청 수신 일시 (슬라이딩 세션 갱신용) |
| `revoked` | `boolean` | ✅ | `false` | 강제 폐기 여부. `true` 이면 즉시 무효 |

#### 인덱스 명세

| 인덱스명 | 컬럼 | 종류 | 목적 |
|---|---|---|---|
| `pk_tc_ui_auth_session` | `token` | PRIMARY KEY | 토큰 단건 검증 |
| `ix_tc_ui_auth_session_user_pk` | `user_pk` | INDEX | 사용자별 세션 전체 조회 (강제 로그아웃) |
| `ix_tc_ui_auth_session_expires_at` | `expires_at` | INDEX | 만료 세션 정기 정리 배치 |
| `ix_tc_ui_auth_session_revoked_expires_at` | `revoked`, `expires_at` | INDEX | 유효 세션 복합 조건 고속 필터링 |

#### 제약 조건

| 제약명 | 조건 |
|---|---|
| `ck_tc_ui_auth_session_revoked` | `revoked IN (true, false)` |

#### 외래 키

| FK명 | 컬럼 | 참조 테이블.컬럼 | 삭제 정책 |
|---|---|---|---|
| `fk_tc_ui_auth_session_user_pk__tc_user_info` | `user_pk` | `tc_user_info.user_pk` | RESTRICT (기본값) |

---

### 3-3. `tc_user_group`

#### 개요

사용자를 묶는 논리적 역할 그룹을 정의한다. 권한은 사용자 개인이 아닌 그룹 단위로 부여되므로, 이 테이블이 인가 체계의 핵심 허브다. `is_active = false` 이면 소속 사용자 전체의 그룹 권한이 비활성화된다.

#### 컬럼 명세

| 컬럼명 | 타입 | NOT NULL | 기본값 | 설명 |
|---|---|:---:|---|---|
| `group_id` | `bigint` | ✅ | identity (자동 증가) | PK |
| `group_code` | `varchar(50)` | ✅ | - | 그룹 식별 코드 (전역 UNIQUE). 예: `ADMIN`, `OPERATOR` |
| `group_name` | `varchar(100)` | ✅ | - | 그룹 표시 이름. 예: `시스템 관리자` |
| `description` | `varchar(1000)` | - | - | 그룹 용도 및 권한 범위 설명 |
| `is_active` | `boolean` | ✅ | `true` | 그룹 활성 여부 |
| `created_at` | `timestamptz(3)` | ✅ | `CURRENT_TIMESTAMP` | 생성 일시 |
| `updated_at` | `timestamptz(3)` | ✅ | `CURRENT_TIMESTAMP` | 수정 일시 |

#### 인덱스 명세

| 인덱스명 | 컬럼 | 종류 | 목적 |
|---|---|---|---|
| `pk_tc_user_group` | `group_id` | PRIMARY KEY | PK 단건 조회 |
| `uk_tc_user_group_group_code` | `group_code` | UNIQUE | 그룹 코드 중복 방지 |
| `ix_tc_user_group_is_active` | `is_active` | INDEX | 활성 그룹만 필터링 |

#### 제약 조건

| 제약명 | 조건 |
|---|---|
| `ck_tc_user_group_is_active` | `is_active IN (true, false)` |

#### 외래 키

```
없음 (이 테이블은 참조 대상이며 다른 테이블에서 참조함)
```

---

### 3-4. `tc_user_group_member`

#### 개요

`tc_user_info` 와 `tc_user_group` 의 N:M 관계를 연결하는 중간 테이블이다. 사용자+그룹 조합이 UNIQUE 다. 부여 일시(`granted_at`)와 부여자(`granted_by`)를 기록하여 멤버십 이력을 관리한다.

#### 컬럼 명세

| 컬럼명 | 타입 | NOT NULL | 기본값 | 설명 |
|---|---|:---:|---|---|
| `ugm_key` | `bigint` | ✅ | identity (자동 증가) | PK |
| `user_pk` | `bigint` | ✅ | - | FK → `tc_user_info.user_pk` |
| `group_id` | `bigint` | ✅ | - | FK → `tc_user_group.group_id` |
| `granted_at` | `timestamptz(3)` | ✅ | `CURRENT_TIMESTAMP` | 그룹 부여 일시 |
| `granted_by` | `varchar(50)` | - | - | 그룹을 부여한 관리자 ID |

#### 인덱스 명세

| 인덱스명 | 컬럼 | 종류 | 목적 |
|---|---|---|---|
| `pk_tc_user_group_member` | `ugm_key` | PRIMARY KEY | PK 단건 조회 |
| `uk_tc_user_group_member_user_pk_group_id` | `user_pk`, `group_id` | UNIQUE | 사용자+그룹 중복 방지 |
| `ix_tc_user_group_member_user_pk` | `user_pk` | INDEX | 사용자의 그룹 목록 조회 (권한 체크 핵심 쿼리) |
| `ix_tc_user_group_member_group_id` | `group_id` | INDEX | 그룹의 멤버 목록 조회 |

#### 제약 조건

```
없음 (NOT NULL 및 UNIQUE 제약만 존재)
```

#### 외래 키

| FK명 | 컬럼 | 참조 테이블.컬럼 | 삭제 정책 |
|---|---|---|---|
| `fk_tc_user_group_member_user_pk__tc_user_info` | `user_pk` | `tc_user_info.user_pk` | CASCADE |
| `fk_tc_user_group_member_group_id__tc_user_group` | `group_id` | `tc_user_group.group_id` | CASCADE |

---

### 3-5. `tc_ui_permission`

#### 개요

화면(PAGE) 또는 API 엔드포인트 단위의 접근 권한을 정의한다. 그룹에 이 권한을 부여하면 소속 사용자가 해당 리소스에 접근할 수 있다. `match_type` 으로 경로 매칭 방식을 제어한다.

| `match_type` | 설명 |
|---|---|
| `EXACT` | `resource` 와 완전 일치하는 경로만 허용 |
| `PREFIX` | `resource` 로 시작하는 모든 하위 경로 허용 (기본값) |
| `REGEX` | `resource` 를 정규표현식으로 매칭 |

#### 컬럼 명세

| 컬럼명 | 타입 | NOT NULL | 기본값 | 설명 |
|---|---|:---:|---|---|
| `perm_id` | `bigint` | ✅ | identity (자동 증가) | PK |
| `perm_code` | `varchar(80)` | ✅ | - | 권한 식별 코드 (전역 UNIQUE). 예: `USER_READ`, `ADMIN_FULL` |
| `perm_name` | `varchar(120)` | ✅ | - | 권한 표시 이름. 예: `사용자 조회` |
| `resource_type` | `varchar(10)` | ✅ | - | 리소스 종류. `PAGE` / `API` |
| `match_type` | `varchar(10)` | ✅ | `'PREFIX'` | 경로 매칭 방식. `EXACT` / `PREFIX` / `REGEX` |
| `resource` | `varchar(255)` | ✅ | - | 접근 대상 경로 또는 패턴 |
| `http_method` | `varchar(10)` | - | - | HTTP 메서드. `GET`, `POST` 등. NULL 이면 전체 허용 |
| `description` | `varchar(1000)` | - | - | 권한 용도 설명 |
| `is_active` | `boolean` | ✅ | `true` | 권한 활성 여부. `false` 이면 권한 체크 시 무시 |
| `created_at` | `timestamptz(3)` | ✅ | `CURRENT_TIMESTAMP` | 생성 일시 |
| `updated_at` | `timestamptz(3)` | ✅ | `CURRENT_TIMESTAMP` | 수정 일시 |
| `created_by` | `varchar(50)` | ✅ | `'SYSTEM'` | 생성 주체 |
| `updated_by` | `varchar(50)` | ✅ | `'SYSTEM'` | 수정 주체 |

#### 인덱스 명세

| 인덱스명 | 컬럼 | 종류 | 목적 |
|---|---|---|---|
| `pk_tc_ui_permission` | `perm_id` | PRIMARY KEY | PK 단건 조회 |
| `uk_tc_ui_permission_perm_code` | `perm_code` | UNIQUE | 권한 코드 중복 방지 |
| `ix_tc_ui_permission_resource_type_resource` | `resource_type`, `resource` | INDEX | 리소스 접근 시 권한 매칭 조회 (권한 체크 핵심 쿼리) |
| `ix_tc_ui_permission_is_active` | `is_active` | INDEX | 활성 권한만 필터링 |

#### 제약 조건

| 제약명 | 조건 |
|---|---|
| `ck_tc_ui_permission_resource_type` | `resource_type IN ('PAGE', 'API')` |
| `ck_tc_ui_permission_match_type` | `match_type IN ('EXACT', 'PREFIX', 'REGEX')` |
| `ck_tc_ui_permission_is_active` | `is_active IN (true, false)` |

#### 외래 키

```
없음 (이 테이블은 참조 대상이며 다른 테이블에서 참조함)
```

---

### 3-6. `tc_user_group_permission`

#### 개요

`tc_user_group` 과 `tc_ui_permission` 의 N:M 관계를 연결하는 중간 테이블이다. 그룹+권한 조합이 UNIQUE 다. 부여 일시(`granted_at`)와 부여자(`granted_by`)를 기록하여 권한 부여 이력을 관리한다.

#### 컬럼 명세

| 컬럼명 | 타입 | NOT NULL | 기본값 | 설명 |
|---|---|:---:|---|---|
| `ugp_key` | `bigint` | ✅ | identity (자동 증가) | PK |
| `group_id` | `bigint` | ✅ | - | FK → `tc_user_group.group_id` |
| `perm_id` | `bigint` | ✅ | - | FK → `tc_ui_permission.perm_id` |
| `granted_at` | `timestamptz(3)` | ✅ | `CURRENT_TIMESTAMP` | 권한 부여 일시 |
| `granted_by` | `varchar(50)` | - | - | 권한을 부여한 관리자 ID |

#### 인덱스 명세

| 인덱스명 | 컬럼 | 종류 | 목적 |
|---|---|---|---|
| `pk_tc_user_group_permission` | `ugp_key` | PRIMARY KEY | PK 단건 조회 |
| `uk_tc_user_group_permission_group_id_perm_id` | `group_id`, `perm_id` | UNIQUE | 그룹+권한 중복 방지 |
| `ix_tc_user_group_permission_group_id` | `group_id` | INDEX | 그룹의 권한 목록 조회 (권한 체크 핵심 쿼리) |
| `ix_tc_user_group_permission_perm_id` | `perm_id` | INDEX | 특정 권한이 부여된 그룹 목록 조회 |

#### 제약 조건

```
없음 (NOT NULL 및 UNIQUE 제약만 존재)
```

#### 외래 키

| FK명 | 컬럼 | 참조 테이블.컬럼 | 삭제 정책 |
|---|---|---|---|
| `fk_tc_user_group_permission_group_id__tc_user_group` | `group_id` | `tc_user_group.group_id` | CASCADE |
| `fk_tc_user_group_permission_perm_id__tc_ui_permission` | `perm_id` | `tc_ui_permission.perm_id` | CASCADE |

---

## 4. FK 관계 요약

```
tc_user_info.user_pk
    ├──[RESTRICT]── tc_ui_auth_session.user_pk
    └──[CASCADE]──► tc_user_group_member.user_pk

tc_user_group.group_id
    ├──[CASCADE]──► tc_user_group_member.group_id
    └──[CASCADE]──► tc_user_group_permission.group_id

tc_ui_permission.perm_id
    └──[CASCADE]──► tc_user_group_permission.perm_id
```

---

## 5. 주요 쿼리 패턴

### 5-1. 로그인 시 사용자 조회

```sql
SELECT user_pk, user_id, password_hash, status, company, department
  FROM tc_user_info
 WHERE user_id_norm = :user_id_norm
   AND status       = 'ACTIVE';
```

### 5-2. 세션 토큰 유효성 검증

```sql
SELECT user_pk
  FROM tc_ui_auth_session
 WHERE token      = :token
   AND revoked    = false
   AND expires_at > NOW();
```

### 5-3. 사용자의 유효 권한 전체 조회

```sql
SELECT p.perm_id, p.perm_code, p.resource_type, p.match_type, p.resource, p.http_method
  FROM tc_ui_permission         p
  JOIN tc_user_group_permission ugp ON ugp.perm_id  = p.perm_id
  JOIN tc_user_group_member     ugm ON ugm.group_id = ugp.group_id
 WHERE ugm.user_pk  = :user_pk
   AND p.is_active  = true;
```

### 5-4. 사용자의 모든 세션 강제 폐기 (강제 로그아웃)

```sql
UPDATE tc_ui_auth_session
   SET revoked = true
 WHERE user_pk = :user_pk
   AND revoked = false;
```

### 5-5. 특정 그룹의 권한 목록 조회

```sql
SELECT p.perm_code, p.perm_name, p.resource_type, p.resource, p.http_method
  FROM tc_ui_permission         p
  JOIN tc_user_group_permission ugp ON ugp.perm_id = p.perm_id
 WHERE ugp.group_id = :group_id
   AND p.is_active  = true;
```

### 5-6. 만료 세션 정기 정리 (배치)

```sql
DELETE FROM tc_ui_auth_session
 WHERE expires_at < NOW() - INTERVAL '30 days';
```
