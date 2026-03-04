# Phase 1 자격증명 재설정 가이드

## 1. 목적
이 문서는 `SEC-04(Task 1.0)` 작업 이후 신규/기존 개발자가 로컬 환경을 재설정할 수 있도록 안내합니다.

## 2. 템플릿에서 실제 설정 파일 생성
아래 템플릿을 복사해서 실제 설정 파일을 생성합니다.

1. `config/tc-db.properties.template` -> `config/tc-db.properties`
2. `apps/tc-ui-backend-app/config/tc-redis.properties.template` -> `apps/tc-ui-backend-app/config/tc-redis.properties`
3. `apps/tc-business-core-app/config/tc-redis.properties.template` -> `apps/tc-business-core-app/config/tc-redis.properties`
4. `apps/tc-comm-gateway-app/config/tc-redis.properties.template` -> `apps/tc-comm-gateway-app/config/tc-redis.properties`

## 3. 환경변수 설정
실제 비밀번호는 파일에 직접 적지 않고 환경변수로 주입합니다.

### Windows PowerShell
```powershell
$env:TC_DB_PASSWORD="<DB 비밀번호>"
$env:TC_GATEWAY_REDIS_PASSWORD="<Gateway Redis 비밀번호>"
$env:TC_BUSINESS_REDIS_PASSWORD="<Business Redis 비밀번호>"
```

### Linux/macOS Bash
```bash
export TC_DB_PASSWORD="<DB 비밀번호>"
export TC_GATEWAY_REDIS_PASSWORD="<Gateway Redis 비밀번호>"
export TC_BUSINESS_REDIS_PASSWORD="<Business Redis 비밀번호>"
```

## 4. 앱별 오버라이드(선택)
같은 Redis를 쓰더라도 앱별로 다른 자격증명을 쓰고 싶다면 아래 앱 전용 변수로 덮어쓸 수 있습니다.

1. UI Backend
- `TC_UI_BACKEND_DB_PASSWORD`
- `TC_UI_BACKEND_GATEWAY_REDIS_PASSWORD`
- `TC_UI_BACKEND_BUSINESS_REDIS_PASSWORD`

2. Business Core
- `TC_BUSINESS_CORE_REDIS_PASSWORD`

3. Comm Gateway
- `TC_COMM_GATEWAY_REDIS_PASSWORD`

## 5. 검증 방법
1. 애플리케이션 기동 시 `authentication failed` 또는 `password` 관련 오류가 없는지 확인합니다.
2. DB 접속 테스트(로그인 API)와 Redis 연동 테스트(토큰 캐시/비동기 결과 저장)를 실행합니다.
3. `git status`에서 비밀값 파일이 추적 대상에 다시 올라오지 않는지 확인합니다.
