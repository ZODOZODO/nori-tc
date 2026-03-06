> 작성일: 2026-03-05

# tc-ui-backend-app HttpOnly Cookie 인증 전환 + Kafka DLT 제거 설계 (D03)

## 목적
- UI Backend 인증 방식을 `Authorization: Bearer` 헤더 기반에서 `HttpOnly Cookie` 기반으로 완전 전환합니다.
- `tc.ui.commands.DLT` 기반 파싱 실패 보관 전략을 제거하고, 관측 중심(로그/메트릭) 운영으로 단순화합니다.
- 본 문서는 `tc-ui-backend-app` 인증/보안 및 Kafka 수신 오류 처리의 최신 기준 설계로 사용합니다.

## 최신 기준 선언
- 본 문서(`D03`)는 인증/보안 및 Kafka 파싱 실패 처리에 대한 최신 기준 문서입니다.
- `01-system-architecture.md`, `02-ui-management-pages-design.md`는 이력 문서로 유지합니다.
- 기존 문서와 본 문서가 충돌할 경우 본 문서를 우선 적용합니다.

## 범위
- 인증 전달 방식 전환 (`Bearer` 제거, Cookie 전환)
- CSRF/CORS 정책 정식 반영
- 로그인/로그아웃/API 계약 변경
- Kafka DLT 설정/구성/코드/테스트 제거 정책

## 비범위
- UI 화면 구현
- 기존 `Eqp/Model/User/Group/Permission` 기능 요구사항 자체 변경
- 데이터베이스 스키마 신규 추가

## 변경 요약

| 구분 | 기존 | 변경 |
|---|---|---|
| 인증 전달 | `Authorization: Bearer {token}` | `TC_UI_AUTH` HttpOnly Cookie |
| 로그인 응답 data | `token, userPk, issuedAt, expiresAt` | `userPk, issuedAt, expiresAt` |
| 인증 추출 위치 | 헤더 | 쿠키 |
| CSRF | 비활성 | 활성 (`XSRF-TOKEN` + `X-XSRF-TOKEN`) |
| CORS | 명시 정책 부재 | `allowCredentials=true`, 허용 Origin 프로퍼티 기반 |
| Kafka 파싱 실패 | DLT 발행 후 ACK | WARN 로그 + 메트릭 증가 + ACK |
| Kafka DLT 설정 | `commands-dlt-*` 사용 | 전면 제거 |

## 인증/보안 상세 설계

### 1) 로그인 (`POST /api/auth/login`)
- 인증 성공 시 세션 토큰은 응답 본문이 아닌 `Set-Cookie` 헤더로만 전달합니다.
- 쿠키는 `HttpOnly` 속성으로 발급하여 JavaScript 접근을 차단합니다.
- 응답 본문 예시는 아래와 같습니다.

```json
{
  "success": true,
  "data": {
    "userPk": 123,
    "issuedAt": "2026-03-05T10:00:00+09:00",
    "expiresAt": "2026-03-05T18:00:00+09:00"
  },
  "errorCode": null,
  "errorMsg": null
}
```

### 2) 로그아웃 (`POST /api/auth/logout`)
- SecurityContext에 적재된 토큰으로 DB revoke + Redis evict를 수행합니다.
- 응답 시 `TC_UI_AUTH` 삭제 쿠키(`Max-Age=0`)를 내려 클라이언트 쿠키를 즉시 만료시킵니다.

### 3) 현재 사용자 조회 (`GET /api/auth/me`)
- 기존과 동일하게 인증된 사용자 정보(`userPk`, `userId`, `permissionCodes`)를 반환합니다.
- 인증 근거는 헤더가 아닌 쿠키입니다.

### 4) CSRF 토큰 발급 (`GET /api/auth/csrf`) 신규
- 프런트 초기 진입 시 호출하여 CSRF 토큰 쿠키를 확보합니다.
- 상태 변경 요청(`POST`, `PUT`, `DELETE`)은 `X-XSRF-TOKEN` 헤더를 포함해야 합니다.

### 5) 인증 필터 동작
- `UiTokenAuthenticationFilter`는 `TC_UI_AUTH` 쿠키만 검사합니다.
- 토큰 미존재 시 SecurityContext 미설정 상태로 다음 필터로 통과합니다.
- 토큰 존재 시 `ValidateTokenUseCase`를 통해 유효성 검증 후 인증 객체를 SecurityContext에 등록합니다.

### 6) CSRF 정책
- `CookieCsrfTokenRepository`를 사용합니다.
- CSRF 쿠키 이름: `XSRF-TOKEN`
- CSRF 헤더 이름: `X-XSRF-TOKEN`
- 상태 변경 요청에 유효한 토큰이 없으면 `403`을 반환합니다.

### 7) CORS 정책
- `allowCredentials=true`를 사용하여 쿠키 인증을 허용합니다.
- 허용 Origin 목록은 프로퍼티로 관리합니다.
- 운영에서는 정확한 Origin 화이트리스트를 사용합니다.

## 인증 관련 설정 키

| 키 | 기본값(운영) | 설명 |
|---|---|---|
| `tc.ui.backend.auth.session-ttl-hours` | `8` | 세션 만료 시간 |
| `tc.ui.backend.auth.token-cache-ttl-seconds` | `300` | Redis 인증 캐시 TTL |
| `tc.ui.backend.auth.cookie-name` | `TC_UI_AUTH` | 인증 쿠키 이름 |
| `tc.ui.backend.auth.cookie-path` | `/` | 인증 쿠키 Path |
| `tc.ui.backend.auth.cookie-domain` | (비워둠) | 필요 시 Domain 지정 |
| `tc.ui.backend.auth.cookie-secure` | `true` | HTTPS 전용 쿠키 |
| `tc.ui.backend.auth.cookie-same-site` | `None` | 교차 출처 쿠키 전송 허용 |
| `tc.ui.backend.auth.csrf-cookie-name` | `XSRF-TOKEN` | CSRF 쿠키 이름 |
| `tc.ui.backend.auth.csrf-header-name` | `X-XSRF-TOKEN` | CSRF 헤더 이름 |
| `tc.ui.backend.auth.cors-allowed-origins` | 환경별 설정 | 허용 Origin 목록 |

## 로컬 프로파일 정책
- `config/tc-ui-backend-local.properties`를 별도로 사용합니다.
- 로컬 기본값:
  - `tc.ui.backend.auth.cookie-secure=false`
  - `tc.ui.backend.auth.cookie-same-site=Lax`

## Kafka DLT 제거 설계

### 제거 대상
- 프로퍼티:
  - `tc.ui.backend.kafka.commands-dlt-topic`
  - `tc.ui.backend.kafka.commands-dlt-partitions`
  - `tc.ui.backend.kafka.commands-dlt-replication-factor`
  - `tc.ui.backend.kafka.commands-dlt-retention-ms`
- 구성:
  - DLT `NewTopic` 생성 빈
  - `DeadLetterPublishingRecoverer` 기반 에러 핸들링
- 구독기:
  - 파싱 실패 DLT 발행 로직 및 DLT 헤더 복사 로직

### 파싱 실패 신규 처리 정책
- `WARN` 로그 기록
- `kafka.command.parse_error` 메트릭 증가
- 현재 레코드 `ACK` 후 스킵
- 재시도/격리 저장 없이 운영 관측(로그/메트릭/알람)으로 대응

## Breaking Changes
1. 프런트는 `Authorization` 헤더를 더 이상 사용하지 않습니다.
2. 로그인 응답에서 `data.token` 필드가 제거됩니다.
3. 상태 변경 요청은 CSRF 헤더 누락 시 `403`이 발생합니다.
4. Kafka 파싱 실패 메시지는 DLT에 적재되지 않습니다.

## 검증 시나리오
1. 로그인 성공 시 `Set-Cookie`에 `TC_UI_AUTH`, `HttpOnly`, `SameSite`, `Secure`, `Path=/`가 포함되는지 확인
2. 로그인 응답 JSON에 `data.token`이 없는지 확인
3. 쿠키 없는 보호 API 요청이 `401`인지 확인
4. 쿠키 + 권한 없는 요청이 `403`인지 확인
5. 로그아웃 후 쿠키 삭제 및 동일 세션 재사용 `401` 확인
6. CSRF 헤더 누락 상태 변경 요청이 `403`인지 확인
7. `tc.ui.commands` 파싱 실패 시 DLT 발행이 발생하지 않는지 확인

## 운영 관측 포인트
- 인증 실패율 (`401`) 및 CSRF 실패율 (`403`) 모니터링
- `kafka.command.parse_error` 메트릭 알람 임계치 설정
- 파싱 실패 로그의 `traceId`, `topic`, `partition`, `offset` 필드 기반 원인 추적

