# 07. Redis 연동 (Redis Integration)

## 개요

nori-tc의 모든 앱은 **Redis**를 다양한 용도로 활용합니다.
Redis는 인메모리 데이터 저장소로, DB보다 훨씬 빠른 읽기/쓰기 성능을 제공합니다.

각 앱이 Redis를 어떤 용도로 사용하는지, 어떤 키 패턴을 사용하는지를 이 문서에서 설명합니다.

---

## Redis 인스턴스 구성

nori-tc에서는 용도에 따라 **별도의 Redis 인스턴스**를 사용합니다.

```
Redis 인스턴스 구성:

┌──────────────────────────────────────────────────────────────┐
│  Gateway Redis (port 6379)                                   │
│  → tc-comm-gateway-app 전용                                  │
│  → DLQ, Quarantine 저장                                     │
│  → tc-ui-backend-app도 조회 목적으로 접근 가능              │
└──────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────┐
│  Business Redis (port 6380)                                  │
│  → tc-business-core-app 전용                                 │
│  → DLQ, UI Task 중복 방지                                   │
│  → tc-ui-backend-app도 접근 (토큰 캐시, 비동기 결과)        │
└──────────────────────────────────────────────────────────────┘
```

```properties
# tc-redis.properties (Gateway)
spring.data.redis.host=192.168.0.13
spring.data.redis.port=6379
spring.data.redis.password=redis1234
```

---

## 앱별 Redis 사용 용도

### tc-comm-gateway-app

| 용도 | 키 패턴 | TTL | 설명 |
|------|---------|-----|------|
| DLQ | `tc:comm:gateway:dlq:{id}` | 7일 | 처리 실패 명령 보관 |
| Quarantine | `tc:comm:gateway:quarantine:{id}` | 14일 | 미매칭 장비 데이터 격리 |
| UI Task 중복 방지 | `tc:comm:gateway:ui:dedup:{traceId}` | 600초 | 동일 traceId 중복 요청 차단 |

### tc-ui-backend-app

| 용도 | 키 패턴 | TTL | Redis 인스턴스 |
|------|---------|-----|--------------|
| 세션 토큰 캐시 | `tc:ui:backend:session:{token}` | 300초 | Business Redis |
| 비동기 결과 (START/END) | `tc:ui:backend:async:{traceId}` | 600초 | Business Redis |
| Gateway DLQ 조회 | `tc:comm:gateway:dlq:*` | — | Gateway Redis (읽기 전용) |
| Business DLQ 조회 | `tc:business:core:dlq:*` | — | Business Redis (읽기 전용) |

### tc-business-core-app

| 용도 | 키 패턴 | TTL | 설명 |
|------|---------|-----|------|
| DLQ | `tc:business:core:dlq:{id}` | 별도 설정 | 처리 실패 메시지 보관 |
| UI Task 중복 방지 | `tc:business:ui:dedup:{traceId}` | 별도 설정 | UI 메시지 중복 처리 방지 |

---

## TTL (Time To Live) 개념

Redis에 저장되는 모든 데이터에는 **TTL(만료 시간)** 이 설정됩니다.
TTL이 지나면 Redis가 자동으로 해당 키를 삭제합니다.

```
저장 시 TTL 설정:

Key: tc:comm:gateway:dlq:01JNCMX7YB
Value: {"eqpId": "EQP-001", "reason": "..."}
TTL: 604800초 (7일)

┌────────────────────────────────────────────┐
│ 저장 시점: 2026-03-11 10:00:00             │
│ 만료 시점: 2026-03-18 10:00:00 (7일 후)   │
│ 만료 후: Redis가 자동 삭제                  │
└────────────────────────────────────────────┘
```

**TTL을 사용하는 이유:**
- 운영자가 수동으로 삭제하지 않아도 오래된 데이터가 자동 정리됩니다
- Redis 메모리가 무한정 증가하지 않습니다
- 오래된 DLQ 데이터가 남아 혼란을 주지 않습니다

---

## 세션 토큰 캐시 (UI Backend)

UI Backend는 사용자 인증 토큰을 DB에 저장하지만,
매 요청마다 DB를 조회하면 성능이 저하됩니다.
이를 위해 Redis에 토큰을 캐싱합니다.

```
인증 흐름:

1. 사용자 로그인
       ↓
2. DB에 토큰 저장 (TTL: 8시간)
3. Redis에 토큰 캐싱 (TTL: 300초 = 5분)
4. HttpOnly 쿠키로 토큰 전달

5. 다음 API 요청에 쿠키 포함
       ↓
6. Redis에서 토큰 조회 (캐시 히트) → 빠름
   Redis에 없으면 DB 조회 (캐시 미스) → 느리지만 정확
       ↓
7. 인증 성공 → 요청 처리
```

```
키: tc:ui:backend:session:{token}
값: UserPrincipal 직렬화 (사용자 정보)
TTL: 300초 (설정: tc.ui.backend.auth.token-cache-ttl-seconds)
```

---

## 비동기 결과 저장 (UI Backend)

설비 START/END 명령은 즉시 결과를 알 수 없습니다.
UI Backend는 Redis를 임시 저장소로 사용해서 비동기 결과를 저장합니다.

```
START/END 요청 흐름:

1. 프론트엔드: POST /api/eqp/{eqpId}/start
       ↓
2. UI Backend: Redis에 "PENDING" 상태 저장
   키: tc:ui:backend:async:{traceId}
   값: {"status": "PENDING"}
   TTL: 600초
       ↓
3. UI Backend: Kafka에 이벤트 발행 (비동기)
4. UI Backend: HTTP 202 Accepted + traceId 반환
       ↓
5. 프론트엔드: GET /api/async/{traceId} 로 결과 폴링
       ↓
6. Gateway에서 START 완료 후 tc.ui.commands에 응답 발행
       ↓
7. UI Backend Subscriber: Redis 업데이트
   키: tc:ui:backend:async:{traceId}
   값: {"status": "SUCCESS"}
       ↓
8. 프론트엔드 폴링: Redis에서 "SUCCESS" 확인 → 완료
```

---

## UI Task 중복 방지

같은 UI 요청이 Kafka로 두 번 발행될 경우(재시도, 네트워크 오류 등),
동일한 작업을 두 번 처리하면 안 됩니다.
Redis의 `SET NX(Not Exists)` 기능으로 중복을 방지합니다.

```
중복 방지 흐름:

1. traceId=01JNCMX7YB 인 UI Task 수신
       ↓
2. Redis에 "SET NX tc:comm:gateway:ui:dedup:01JNCMX7YB"
   - 키가 없으면: SET 성공 → 새 요청으로 처리
   - 키가 이미 있으면: SET 실패 → 중복으로 거절
       ↓
3. 처리 완료 후 TTL 600초 유지
   (600초 이내 같은 traceId 재요청은 모두 거절)
```

자세한 내용은 [11-ui-task-deduplication.md](../gateway/11-ui-task-deduplication.md)를 참고하세요.

---

## Redis 어댑터 구성

Redis 연동은 포트-어댑터 패턴으로 구현되어 있습니다.

```
Port (인터페이스)                 Adapter (구현체)
─────────────────────────────────────────────────────
DlqStorePort              ←→   GatewayRedisDlqAdapter
QuarantineStorePort       ←→   GatewayRedisQuarantineAdapter
UiDeduplicationPort       ←→   RedisUiDeduplicationAdapter
AsyncResultStorePort      ←→   RedisAsyncResultAdapter
SessionCachePort          ←→   RedisSessionCacheAdapter
```

각 어댑터는 `StringRedisTemplate` 또는 `RedisTemplate`을 통해 Redis에 접근합니다.

---

## UI Backend 이중 Redis 연결

UI Backend는 Gateway Redis와 Business Redis에 모두 접근해야 합니다.
두 Redis 인스턴스를 동시에 연결하기 위해 **수동 Bean 구성**을 사용합니다.

```java
// Spring 자동 설정이 이중 Redis를 지원하지 않으므로 수동으로 구성

// Gateway Redis 연결 (DLQ 조회용)
@Bean("gatewayRedisTemplate")
public StringRedisTemplate gatewayRedisTemplate() {
    LettuceConnectionFactory factory = new LettuceConnectionFactory(
        "192.168.0.13", 6379
    );
    return new StringRedisTemplate(factory);
}

// Business Redis 연결 (토큰 캐시, 비동기 결과용)
@Bean("businessRedisTemplate")
public StringRedisTemplate businessRedisTemplate() {
    LettuceConnectionFactory factory = new LettuceConnectionFactory(
        "192.168.0.13", 6380
    );
    return new StringRedisTemplate(factory);
}
```

```yaml
# application.yaml에서 Spring 자동 Redis 설정 제외
spring:
  autoconfigure:
    exclude:
      - TcDbRedisAutoConfiguration
      - DataRedisAutoConfiguration
      - DataRedisRepositoriesAutoConfiguration
```

---

## 운영 포인트

| 항목 | 내용 |
|------|------|
| **메모리 모니터링** | `INFO memory` 명령으로 Redis 메모리 사용량을 주기적으로 확인하세요 |
| **TTL 확인** | `TTL {key}` 명령으로 특정 키의 남은 만료 시간을 확인할 수 있습니다 |
| **비밀번호 보안** | `tc-redis.properties`의 `spring.data.redis.password`를 강력한 값으로 설정하고 파일 권한을 제한하세요 |
| **클러스터/센티널** | 운영 환경에서는 Redis Sentinel 또는 Cluster 구성을 권장합니다 |
| **DLQ TTL** | 7일 이내에 DLQ 항목을 확인하지 않으면 자동 삭제됩니다. 알람 설정을 권장합니다 |
| **키 충돌** | Gateway와 Business의 키 prefix가 다르므로(`tc:comm:gateway:` vs `tc:business:core:`) 충돌은 없습니다. 하지만 같은 Redis 인스턴스를 공유할 경우 prefix를 명확히 관리하세요 |
