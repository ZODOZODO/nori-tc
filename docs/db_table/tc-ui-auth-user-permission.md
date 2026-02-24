# TC UI 인증 · 사용자 · 권한 테이블 명세

## 1. 도메인 개요

이 문서에서 다루는 6개 테이블은 **UI 백엔드 앱의 인증(Authentication) 및 인가(Authorization) 도메인** 전체를 구성합니다.

| 역할 | 담당 테이블 |
|---|---|
| 사용자 계정 관리 | `tc_user_info` |
| 로그인 세션 관리 | `tc_ui_auth_session` |
| 그룹 정의 | `tc_user_group` |
| 사용자 ↔ 그룹 매핑 | `tc_user_group_member` |
| 권한 정의 | `tc_ui_permission` |
| 그룹 ↔ 권한 매핑 | `tc_user_group_permission` |

### 설계 철학

- 권한은 **사용자 개인이 아닌 그룹 단위**로 부여합니다 (GBAC, Group-Based Access Control).
- 한 사용자는 **여러 그룹**에 속할 수 있고, 한 그룹은 **여러 권한**을 가질 수 있습니다.
- 세션은 **토큰 기반**이며, 서버 측 저장소(DB)에서 유효성을 관리합니다.
- 권한 리소스는 `PAGE`(화면 라우팅)와 `API`(백엔드 엔드포인트) 두 가지 타입을 지원합니다.

---

## 2. 전체 구조 다이어그램

```
                         ┌──────────────────────┐
                         │    tc_user_info      │  ← 사용자 계정 원장
                         │  (PK: user_pk)       │
                         └──────────┬───────────┘
                                    │
               ┌────────────────────┼────────────────────┐
               │                                         │
               ▼                                         ▼
  ┌─────────────────────────┐           ┌──────────────────────────┐
  │   tc_ui_auth_session    │           │   tc_user_group_member   │  ← 사용자-그룹 매핑
  │  (PK: token)            │           │  (PK: ugm_key)           │
  │  로그인 세션/토큰 관리     │           │  (FK: user_pk, group_id) │
  └─────────────────────────┘           └────────────┬─────────────┘
                                                     │
                                                     ▼
                                        ┌────────────────────────┐
                                        │    tc_user_group       │  ← 그룹 정의
                                        │  (PK: group_id)        │
                                        └────────────┬───────────┘
                                                     │
                                                     ▼
                                        ┌────────────────────────────┐
                                        │  tc_user_group_permission  │  ← 그룹-권한 매핑
                                        │  (PK: ugp_key)             │
                                        │  (FK: group_id, perm_id)   │
                                        └────────────┬───────────────┘
                                                     │
                                                     ▼
                                        ┌────────────────────────┐
                                        │   tc_ui_permission     │  ← 권한 정의
                                        │  (PK: perm_id)         │
                                        └────────────────────────┘
```

---

## 3. 권한 체크 런타임 흐름

실제 HTTP 요청이 들어왔을 때 아래 순서로 테이블을 조회하여 접근 허용 여부를 결정합니다.

```
HTTP 요청 수신
    │
    ▼
[STEP 1] tc_ui_auth_session
         WHERE token = :token
           AND revoked = false
           AND expires_at > NOW()
         → 실패 시 401 Unauthorized
    │
    ▼
[STEP 2] tc_user_group_member
         WHERE user_pk = :user_pk
         → 사용자가 속한 group_id 목록 확보
    │
    ▼
[STEP 3] tc_user_group_permission
         WHERE group_id IN (:group_id_list)
         → 그룹에 부여된 perm_id 목록 확보
    │
    ▼
[STEP 4] tc_ui_permission
         WHERE perm_id IN (:perm_id_list)
           AND is_active = true
           AND resource_type = :type    (PAGE or API)
           AND resource 매칭           (EXACT / PREFIX / REGEX)
           AND http_method = :method   (API 타입인 경우)
         → 실패 시 403 Forbidden
    │
    ▼
접근 허용
```

---

## 4. 테이블 상세 명세

---

### 4-1. `tc_user_info` — 사용자 계정 원장

#### 목적
시스템에 등록된 모든 사용자의 **식별 정보, 로그인 자격증명, 계정 상태**를 저장하는 핵심 테이블입니다.
모든 사용자 관련 테이블(세션, 그룹 멤버십)의 **최상위 부모**이며, 이 테이블 없이는 로그인 자체가 불가합니다.

#### 계정 상태 전이

```
              관리자 잠금
    ACTIVE ────────────────► LOCKED
      │                        │
      │ 관리자 비활성화          │ 관리자 해제
      ▼                        ▼
  DISABLED                  ACTIVE
      │
      │ 논리 삭제
      ▼
   DELETED   ← 실제 row 는 삭제하지 않음 (이력 보존)
```

#### 컬럼 명세

| 컬럼명 | 타입 | NOT NULL | 기본값 | 설명 |
|---|---|:---:|---|---|
| `user_pk` | `bigint` | ✅ | identity (자동 증가) | **PK**. 시스템 내부 식별자 |
| `company` | `varchar(100)` | ✅ | - | 소속 회사 |
| `department` | `varchar(100)` | ✅ | - | 소속 부서 |
| `user_name` | `varchar(100)` | ✅ | - | 사용자 실명 |
| `user_id` | `varchar(50)` | ✅ | - | 로그인 ID 원본 (대소문자 유지, 표시용) |
| `user_id_norm` | `varchar(50)` | ✅ | - | 로그인 ID 정규화 값 (소문자 변환 등). **실제 로그인 조회에 사용** |
| `password_hash` | `varchar(255)` | ✅ | - | 해시 처리된 비밀번호 (BCrypt 권장) |
| `email` | `varchar(255)` | ✅ | - | 이메일 주소. 전역 UNIQUE |
| `status` | `varchar(20)` | ✅ | `'ACTIVE'` | 계정 상태. `ACTIVE` / `LOCKED` / `DISABLED` / `DELETED` |
| `created_at` | `timestamptz(3)` | ✅ | `CURRENT_TIMESTAMP` | 계정 생성 일시 |
| `updated_at` | `timestamptz(3)` | ✅ | `CURRENT_TIMESTAMP` | 계정 최종 수정 일시 |
| `created_by` | `varchar(50)` | ✅ | `'SYSTEM'` | 생성 주체 (관리자 ID 또는 SYSTEM) |
| `updated_by` | `varchar(50)` | ✅ | `'SYSTEM'` | 수정 주체 |

#### 인덱스

| 인덱스명 | 대상 컬럼 | 종류 | 목적 |
|---|---|---|---|
| `pk_tc_user_info` | `user_pk` | PRIMARY KEY | PK 단건 조회 |
| `uk_tc_user_info_user_id_norm` | `user_id_norm` | UNIQUE | 로그인 ID 중복 방지. **로그인 시 WHERE 조건으로 사용** |
| `uk_tc_user_info_email` | `email` | UNIQUE | 이메일 중복 방지 |
| `ix_tc_user_info_status` | `status` | INDEX | 상태별 사용자 목록 조회 (관리자 화면) |
| `ix_tc_user_info_company_department` | `company`, `department` | INDEX | 부서별 사용자 조회 |

#### 주요 제약

```sql
CHECK (status IN ('ACTIVE', 'LOCKED', 'DISABLED', 'DELETED'))
```

#### 참조 관계 (자식 테이블)

| 자식 테이블 | FK 컬럼 | 삭제 정책 |
|---|---|---|
| `tc_ui_auth_session` | `user_pk` | RESTRICT (세션이 있으면 삭제 불가) |
| `tc_user_group_member` | `user_pk` | CASCADE (멤버십 자동 삭제) |

---

### 4-2. `tc_ui_auth_session` — 로그인 세션

#### 목적
로그인 성공 시 발급되는 **토큰 기반 서버 측 세션**을 관리합니다.
매 API 요청마다 토큰의 유효성(`revoked`, `expires_at`)을 이 테이블에서 검증합니다.

#### 세션 생명주기

```
로그인 성공
    │
    ▼
INSERT (token, user_pk, issued_at, expires_at, revoked=false)
    │
    ├─── [정상 요청마다] UPDATE last_seen_at = NOW()  (슬라이딩 세션 구현 시)
    │
    ├─── [로그아웃] UPDATE revoked = true
    │
    └─── [만료 배치] DELETE WHERE expires_at < NOW() - interval '30 days'
                      (만료된 세션 정기 정리)
```

#### 유효 세션 판단 조건

```sql
WHERE token     = :token
  AND revoked   = false
  AND expires_at > NOW()
```

#### 컬럼 명세

| 컬럼명 | 타입 | NOT NULL | 기본값 | 설명 |
|---|---|:---:|---|---|
| `token` | `varchar(64)` | ✅ | - | **PK**. 랜덤 생성 세션 토큰 문자열 |
| `user_pk` | `bigint` | ✅ | - | **FK** → `tc_user_info.user_pk` |
| `issued_at` | `timestamptz(3)` | ✅ | `CURRENT_TIMESTAMP` | 토큰 최초 발급 일시 |
| `expires_at` | `timestamptz(3)` | ✅ | - | 토큰 만료 일시. 이 시각 이후 자동 무효 |
| `last_seen_at` | `timestamptz(3)` | - | - | 마지막 요청 수신 일시. 슬라이딩 세션 갱신에 사용 |
| `revoked` | `boolean` | ✅ | `false` | 강제 폐기 여부. `true` 이면 즉시 무효 처리 |

#### 인덱스

| 인덱스명 | 대상 컬럼 | 종류 | 목적 |
|---|---|---|---|
| `pk_tc_ui_auth_session` | `token` | PRIMARY KEY | 토큰 단건 검증 |
| `ix_tc_ui_auth_session_user_pk` | `user_pk` | INDEX | 사용자별 세션 전체 조회 (강제 로그아웃 시) |
| `ix_tc_ui_auth_session_expires_at` | `expires_at` | INDEX | 만료 세션 정리 배치 |
| `ix_tc_ui_auth_session_revoked_expires_at` | `revoked`, `expires_at` | INDEX | 유효 세션 고속 필터링 (복합 조건 쿼리 최적화) |

#### 주요 제약

```sql
CHECK (revoked IN (true, false))
FOREIGN KEY (user_pk) REFERENCES tc_user_info(user_pk)
```

---

### 4-3. `tc_user_group` — 그룹 정의

#### 목적
사용자를 묶는 **논리적 역할 그룹**을 정의합니다.
권한은 사용자 개인이 아닌 **그룹 단위로 부여**되므로, 이 테이블이 인가 체계의 핵심 허브입니다.

예시 그룹 : `ADMIN`(시스템 관리자), `OPERATOR`(운영자), `VIEWER`(조회 전용)

#### 컬럼 명세

| 컬럼명 | 타입 | NOT NULL | 기본값 | 설명 |
|---|---|:---:|---|---|
| `group_id` | `bigint` | ✅ | identity (자동 증가) | **PK** |
| `group_code` | `varchar(50)` | ✅ | - | 그룹 식별 코드. UNIQUE. 예: `ADMIN`, `OPERATOR` |
| `group_name` | `varchar(100)` | ✅ | - | 그룹 표시 이름. 예: `시스템 관리자` |
| `description` | `varchar(1000)` | - | - | 그룹 용도 및 권한 범위 설명 |
| `is_active` | `boolean` | ✅ | `true` | 그룹 활성 여부. `false` 이면 소속 사용자 전체 권한 비활성 |
| `created_at` | `timestamptz(3)` | ✅ | `CURRENT_TIMESTAMP` | 생성 일시 |
| `updated_at` | `timestamptz(3)` | ✅ | `CURRENT_TIMESTAMP` | 수정 일시 |

#### 인덱스

| 인덱스명 | 대상 컬럼 | 종류 | 목적 |
|---|---|---|---|
| `pk_tc_user_group` | `group_id` | PRIMARY KEY | 단건 조회 |
| `uk_tc_user_group_group_code` | `group_code` | UNIQUE | 코드 중복 방지 |
| `ix_tc_user_group_is_active` | `is_active` | INDEX | 활성 그룹만 필터링 |

#### 주요 제약

```sql
CHECK (is_active IN (true, false))
```

#### 참조 관계 (자식 테이블)

| 자식 테이블 | FK 컬럼 | 삭제 정책 |
|---|---|---|
| `tc_user_group_member` | `group_id` | CASCADE |
| `tc_user_group_permission` | `group_id` | CASCADE |

---

### 4-4. `tc_user_group_member` — 사용자-그룹 매핑

#### 목적
`tc_user_info` 와 `tc_user_group` 의 **N:M 관계를 연결하는 중간 테이블**입니다.
한 사용자는 여러 그룹에 속할 수 있으며, 각 매핑에 부여 이력(일시, 부여자)을 기록합니다.

#### 컬럼 명세

| 컬럼명 | 타입 | NOT NULL | 기본값 | 설명 |
|---|---|:---:|---|---|
| `ugm_key` | `bigint` | ✅ | identity (자동 증가) | **PK**. Surrogate Key |
| `user_pk` | `bigint` | ✅ | - | **FK** → `tc_user_info.user_pk` |
| `group_id` | `bigint` | ✅ | - | **FK** → `tc_user_group.group_id` |
| `granted_at` | `timestamptz(3)` | ✅ | `CURRENT_TIMESTAMP` | 그룹 부여 일시 |
| `granted_by` | `varchar(50)` | - | - | 그룹을 부여한 관리자 ID |

#### 인덱스

| 인덱스명 | 대상 컬럼 | 종류 | 목적 |
|---|---|---|---|
| `pk_tc_user_group_member` | `ugm_key` | PRIMARY KEY | 단건 조회 |
| `uk_tc_user_group_member_user_pk_group_id` | `user_pk`, `group_id` | UNIQUE | 동일 사용자의 중복 그룹 가입 방지 |
| `ix_tc_user_group_member_user_pk` | `user_pk` | INDEX | **사용자의 그룹 목록 조회** (권한 체크 핵심 쿼리) |
| `ix_tc_user_group_member_group_id` | `group_id` | INDEX | 그룹의 멤버 목록 조회 (관리자 화면) |

#### 참조 무결성

| FK | 참조 대상 | 삭제 정책 |
|---|---|---|
| `user_pk` | `tc_user_info.user_pk` | CASCADE (사용자 삭제 시 멤버십 자동 삭제) |
| `group_id` | `tc_user_group.group_id` | CASCADE (그룹 삭제 시 멤버십 자동 삭제) |

---

### 4-5. `tc_ui_permission` — 권한 정의

#### 목적
**화면(PAGE) 또는 API 엔드포인트** 단위의 접근 권한을 정의합니다.
그룹에 이 권한을 부여하면 소속 사용자가 해당 리소스에 접근할 수 있습니다.

#### 리소스 타입 및 매칭 방식 상세

```
resource_type = 'PAGE'
    → 프론트엔드 라우팅 경로 접근 제어
    → 예: resource = '/admin', match_type = 'PREFIX'
    →     /admin, /admin/users, /admin/settings 모두 허용

resource_type = 'API'
    → 백엔드 REST API 접근 제어
    → http_method 와 함께 사용 가능
    → 예: resource = '/api/v1/users', method = 'GET', match_type = 'EXACT'
    →     GET /api/v1/users 만 허용

match_type = 'EXACT'   → resource 와 완전 일치
match_type = 'PREFIX'  → resource 로 시작하는 모든 경로 (기본값)
match_type = 'REGEX'   → 정규표현식 매칭
```

#### 컬럼 명세

| 컬럼명 | 타입 | NOT NULL | 기본값 | 설명 |
|---|---|:---:|---|---|
| `perm_id` | `bigint` | ✅ | identity (자동 증가) | **PK** |
| `perm_code` | `varchar(80)` | ✅ | - | 권한 식별 코드. UNIQUE. 예: `USER_READ`, `ADMIN_FULL` |
| `perm_name` | `varchar(120)` | ✅ | - | 권한 표시 이름. 예: `사용자 조회` |
| `resource_type` | `varchar(10)` | ✅ | - | 리소스 종류. `PAGE` 또는 `API` |
| `match_type` | `varchar(10)` | ✅ | `'PREFIX'` | 경로 매칭 방식. `EXACT` / `PREFIX` / `REGEX` |
| `resource` | `varchar(255)` | ✅ | - | 접근 대상 경로 또는 패턴 문자열 |
| `http_method` | `varchar(10)` | - | - | HTTP 메서드. `GET`, `POST` 등. `NULL` 이면 전체 허용 |
| `description` | `varchar(1000)` | - | - | 권한 용도 설명 |
| `is_active` | `boolean` | ✅ | `true` | 권한 활성 여부. `false` 이면 체크 시 무시 |
| `created_at` | `timestamptz(3)` | ✅ | `CURRENT_TIMESTAMP` | 생성 일시 |
| `updated_at` | `timestamptz(3)` | ✅ | `CURRENT_TIMESTAMP` | 수정 일시 |
| `created_by` | `varchar(50)` | ✅ | `'SYSTEM'` | 생성 주체 |
| `updated_by` | `varchar(50)` | ✅ | `'SYSTEM'` | 수정 주체 |

#### 인덱스

| 인덱스명 | 대상 컬럼 | 종류 | 목적 |
|---|---|---|---|
| `pk_tc_ui_permission` | `perm_id` | PRIMARY KEY | 단건 조회 |
| `uk_tc_ui_permission_perm_code` | `perm_code` | UNIQUE | 코드 중복 방지 |
| `ix_tc_ui_permission_resource_type_resource` | `resource_type`, `resource` | INDEX | **리소스 접근 시 권한 매칭 조회** (권한 체크 핵심 쿼리) |
| `ix_tc_ui_permission_is_active` | `is_active` | INDEX | 활성 권한만 필터링 |

#### 주요 제약

```sql
CHECK (resource_type IN ('PAGE', 'API'))
CHECK (match_type IN ('EXACT', 'PREFIX', 'REGEX'))
CHECK (is_active IN (true, false))
```

---

### 4-6. `tc_user_group_permission` — 그룹-권한 매핑

#### 목적
`tc_user_group` 과 `tc_ui_permission` 의 **N:M 관계를 연결하는 중간 테이블**입니다.
특정 그룹에 특정 권한을 부여하며, 부여 이력(일시, 부여자)을 기록합니다.

#### 컬럼 명세

| 컬럼명 | 타입 | NOT NULL | 기본값 | 설명 |
|---|---|:---:|---|---|
| `ugp_key` | `bigint` | ✅ | identity (자동 증가) | **PK**. Surrogate Key |
| `group_id` | `bigint` | ✅ | - | **FK** → `tc_user_group.group_id` |
| `perm_id` | `bigint` | ✅ | - | **FK** → `tc_ui_permission.perm_id` |
| `granted_at` | `timestamptz(3)` | ✅ | `CURRENT_TIMESTAMP` | 권한 부여 일시 |
| `granted_by` | `varchar(50)` | - | - | 권한을 부여한 관리자 ID |

#### 인덱스

| 인덱스명 | 대상 컬럼 | 종류 | 목적 |
|---|---|---|---|
| `pk_tc_user_group_permission` | `ugp_key` | PRIMARY KEY | 단건 조회 |
| `uk_tc_user_group_permission_group_id_perm_id` | `group_id`, `perm_id` | UNIQUE | 동일 그룹에 중복 권한 부여 방지 |
| `ix_tc_user_group_permission_group_id` | `group_id` | INDEX | **그룹의 권한 목록 조회** (권한 체크 핵심 쿼리) |
| `ix_tc_user_group_permission_perm_id` | `perm_id` | INDEX | 특정 권한이 부여된 그룹 목록 조회 |

#### 참조 무결성

| FK | 참조 대상 | 삭제 정책 |
|---|---|---|
| `group_id` | `tc_user_group.group_id` | CASCADE (그룹 삭제 시 권한 매핑 자동 삭제) |
| `perm_id` | `tc_ui_permission.perm_id` | CASCADE (권한 삭제 시 그룹 매핑 자동 삭제) |

---

## 5. 테이블 간 FK 관계 요약

```
tc_user_info
│   (user_pk)
│
├──[FK]── tc_ui_auth_session.user_pk          (RESTRICT on delete)
│
└──[FK]── tc_user_group_member.user_pk        (CASCADE on delete)
              │
              │  (group_id)
              └──[FK]── tc_user_group
                            │
                            └──[FK]── tc_user_group_permission.group_id  (CASCADE on delete)
                                          │
                                          │  (perm_id)
                                          └──[FK]── tc_ui_permission      (CASCADE on delete)
```

---

## 6. 핵심 쿼리 패턴

### 6-1. 로그인 시 사용자 조회

```sql
SELECT *
  FROM tc_user_info
 WHERE user_id_norm = :user_id_norm   -- 정규화된 ID로 조회
   AND status = 'ACTIVE';
```

### 6-2. 세션 토큰 유효성 검증

```sql
SELECT user_pk
  FROM tc_ui_auth_session
 WHERE token      = :token
   AND revoked    = false
   AND expires_at > NOW();
```

### 6-3. 사용자의 유효 권한 전체 조회

```sql
SELECT p.*
  FROM tc_ui_permission       p
  JOIN tc_user_group_permission ugp ON ugp.perm_id  = p.perm_id
  JOIN tc_user_group_member     ugm ON ugm.group_id = ugp.group_id
 WHERE ugm.user_pk  = :user_pk
   AND p.is_active  = true;
```

### 6-4. 만료 세션 정기 정리 (배치)

```sql
DELETE FROM tc_ui_auth_session
 WHERE expires_at < NOW() - INTERVAL '30 days';
```
