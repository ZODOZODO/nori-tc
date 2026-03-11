# 03. 외부 설정 파일 로딩 (External Config Loading)

## 개요

nori-tc의 모든 앱은 설정을 **앱 jar 파일 내부가 아닌 외부 파일**에서 로드합니다.

각 앱 디렉토리에는 `config/` 폴더가 있고, 그 안에 기능별로 분리된 `tc-*.properties` 파일들이 존재합니다.
Spring Boot의 `spring.config.import` 기능을 활용해서 이 파일들을 기동 시 읽어옵니다.

---

## 왜 외부 파일에서 설정을 로드하는가?

### 문제: 설정을 jar 내부에 넣으면?

```
jar 파일 내부에 application.properties를 포함시키면:
- Kafka broker 주소를 바꾸려면 → 코드를 수정하고 재빌드해야 함
- 개발/스테이징/운영 환경마다 → 별도의 jar를 만들어야 함
- DB 비밀번호가 바뀌면 → 다시 배포해야 함
```

### 해결: 외부 파일에서 로드

```
운영 환경의 config/ 디렉토리에 설정을 관리:
- Kafka broker 주소 변경 → config/tc-messaging.properties 수정 후 재시작
- 환경별 설정 → 환경마다 다른 config/ 디렉토리 사용
- 비밀번호 변경 → jar 재빌드 없이 파일만 수정
```

---

## 디렉토리 구조

```
apps/tc-comm-gateway-app/
├── src/main/resources/
│   └── application.yaml        ← Spring Boot 기본 설정 (jar 내부)
│                                  주로 web-application-type, config.import 선언만 포함
└── config/                     ← 외부 설정 파일 디렉토리 (jar 외부)
    ├── tc-comm.properties          게이트웨이 핵심 설정 (Netty, HSMS, 큐 용량 등)
    ├── tc-messaging.properties     Kafka/RabbitMQ/Tibco RV 설정
    ├── tc-redis.properties         Redis 연결 및 TTL 설정
    └── (tc-log.properties)         로그 설정 (선택)
```

```
apps/tc-ui-backend-app/
├── src/main/resources/
│   └── application.yaml
└── config/
    ├── tc-ui-backend.properties    UI 백엔드 핵심 설정 (인증, Kafka 토픽 등)
    ├── tc-messaging.properties     Kafka 설정
    ├── tc-redis.properties         이중 Redis 설정
    ├── tc-db.properties            DB 연결 설정
    └── tc-log.properties           로그 설정
```

```
apps/tc-business-core-app/
├── src/main/resources/
│   └── application.yaml
└── config/
    ├── tc-business-core.properties  비즈니스 코어 설정 (스레드, 큐, 타임아웃 등)
    ├── tc-messaging.properties      Kafka 설정
    ├── tc-redis.properties          Redis 설정
    ├── tc-db.properties             DB 설정
    └── tc-log.properties            로그 설정
```

---

## application.yaml 에서의 선언

각 앱의 `application.yaml`에서 외부 파일 로딩을 선언합니다.

### tc-comm-gateway-app

```yaml
spring:
  application:
    name: tc-comm-gateway-app
  main:
    web-application-type: none   # 웹 서버 없음 (백그라운드 프로세스)
    keep-alive: true             # 웹 서버 없어도 프로세스 유지
  config:
    import:
      - optional:file:config/tc-comm.properties        # 필수 (게이트웨이 설정)
      - optional:file:config/tc-messaging.properties   # 필수 (Kafka 설정)
      - optional:file:config/tc-redis.properties       # 선택 (Redis 미사용 시 생략 가능)
      - optional:file:config/tc-log.properties         # 선택 (로그 커스터마이징)
```

### tc-ui-backend-app

```yaml
spring:
  application:
    name: tc-ui-backend-app
  main:
    web-application-type: servlet  # REST API 서버 (Tomcat 기동)
  config:
    import:
      - optional:file:config/tc-ui-backend.properties
      - optional:file:config/tc-messaging.properties
      - optional:file:config/tc-redis.properties
      - optional:file:config/tc-db.properties
      - optional:file:config/tc-log.properties
```

---

## `optional:` 접두사의 의미

```yaml
- optional:file:config/tc-redis.properties   # ← optional: 붙어 있음
```

| 접두사 | 파일이 없으면? |
|--------|--------------|
| `optional:file:` | 파일이 없어도 앱이 **정상 기동**됩니다 |
| `file:` (optional 없음) | 파일이 없으면 앱 기동에 **실패**합니다 |

**의도:**
- Redis, DB, 로그 설정은 선택 사항이므로 `optional:` 사용
- Kafka, 게이트웨이 핵심 설정은 없으면 동작 자체가 불가하므로 `optional:` 사용 여부를 신중히 결정

---

## 주요 설정 파일 내용

### tc-messaging.properties (Kafka 연결 설정)

```properties
# Kafka 브로커 주소
spring.kafka.bootstrap-servers=192.168.0.13:9092

# Producer 신뢰성 설정
spring.kafka.producer.acks=all                           # 모든 replica 확인
spring.kafka.producer.properties.enable.idempotence=true # 중복 발행 방지
spring.kafka.producer.retries=2147483647                 # 사실상 무한 재시도

# Consumer 설정
spring.kafka.consumer.auto-offset-reset=latest           # 새 consumer는 최신 메시지부터
spring.kafka.consumer.enable-auto-commit=false           # 수동 커밋 (안정성)

# 토픽 이름 매핑
tc.messaging.kafka.topic.eqp-events=tc.eqp.events
tc.messaging.kafka.topic.eqp-commands=tc.eqp.commands
tc.messaging.kafka.topic.ui-events=tc.ui.events.gateway
tc.messaging.kafka.topic.ui-commands=tc.ui.commands
```

### tc-redis.properties (Redis 연결 설정)

```properties
spring.data.redis.host=192.168.0.13
spring.data.redis.port=6379
spring.data.redis.password=redis1234

# DLQ 보관 기간: 7일
tc.comm.gateway.redis.dlq-ttl-seconds=604800

# Quarantine 보관 기간: 14일
tc.comm.gateway.redis.quarantine-ttl-seconds=1209600
```

### tc-comm.properties (게이트웨이 핵심 설정)

```properties
# 처리 성능
tc.comm.gateway.inbound-queue-capacity=2048
tc.comm.gateway.worker-threads=8

# Netty 연결
tc.comm.gateway.netty.boss-threads=1
tc.comm.gateway.netty.worker-threads=4
tc.comm.gateway.netty.reconnect-delay-seconds=3
tc.comm.gateway.netty.max-connect-failures=3

# HSMS 타이머
tc.comm.gateway.hsms.t3-seconds=45
tc.comm.gateway.hsms.t5-seconds=10

# Kafka Shard 설정
tc.comm.gateway.commands-partition-count=6
tc.comm.gateway.owned-partitions=0,1   # 이 인스턴스가 담당할 partition
```

---

## 환경별 설정 오버라이드

로컬 개발 환경에서는 `*-local.properties` 파일로 일부 설정을 덮어쓸 수 있습니다.

```
apps/tc-ui-backend-app/config/
├── tc-ui-backend.properties           ← 기본 설정
└── tc-ui-backend-local.properties     ← 로컬 전용 오버라이드
```

```yaml
# application.yaml에서 Spring Profile로 조건부 로딩
spring:
  config:
    import:
      - optional:file:config/tc-ui-backend.properties
      - optional:file:config/tc-ui-backend-local.properties   # 존재하면 적용
```

> 로컬 파일의 설정이 기본 파일 설정보다 나중에 로드되어 덮어씁니다.
> `tc-ui-backend-local.properties`가 없으면 기본 설정이 그대로 적용됩니다.

---

## 설정 파일 로딩 우선순위

```
높음 ───────────────────────────────────── 낮음

환경변수 > JVM 시스템 프로퍼티 > config/*.properties > application.yaml 내부
```

즉, 같은 키가 여러 곳에 있으면 우선순위가 높은 쪽이 이깁니다.

```
예시: spring.data.redis.host 가
  application.yaml: localhost       (낮음)
  tc-redis.properties: 192.168.0.13 (중간)
  환경변수 SPRING_DATA_REDIS_HOST: 10.0.0.5 (높음)

→ 최종 적용값: 10.0.0.5 (환경변수 우선)
```

---

## 운영 포인트

| 항목 | 내용 |
|------|------|
| **파일 권한** | `config/` 디렉토리에는 DB 비밀번호, Redis 비밀번호가 포함됩니다. 파일 권한을 `600` 또는 `640`으로 제한하세요 |
| **버전 관리** | 비밀 정보가 포함된 파일은 Git에 커밋하지 마세요. `.gitignore`에 `config/*.properties` 추가를 검토하세요 |
| **환경 분리** | 개발/스테이징/운영 서버마다 별도의 `config/` 내용을 관리합니다 |
| **필수 파일 확인** | 앱 기동 전 필수 설정 파일 존재 여부와 내용을 반드시 확인합니다 |
| **인코딩** | 모든 `.properties` 파일은 UTF-8로 저장합니다 |
