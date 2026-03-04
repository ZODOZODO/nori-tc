# nori-tc 개선 작업 플랜

| 항목 | 내용 |
|------|------|
| 최초 작성 | 2026-03-03 |
| 최종 업데이트 | 2026-03-04 |
| 참조 문서 | [01-initial-code-review.md](../design/01-initial-code-review.md) |
| 작업 단위 | Phase 단위, 각 Phase는 독립적으로 배포 가능 |

---

## 목차

1. [작업 진행 원칙](#작업-진행-원칙)
2. [Phase 1 - 치명적 보안 및 안정성 수정](#phase-1---치명적-보안-및-안정성-수정)
3. [Phase 2 - 높음 위험도 수정](#phase-2---높음-위험도-수정)
4. [Phase 3 - 중간 위험도 개선](#phase-3---중간-위험도-개선)
5. [Phase 4 - 낮음 위험도 개선 및 운영성 향상](#phase-4---낮음-위험도-개선-및-운영성-향상)
6. [전체 작업 체크리스트](#전체-작업-체크리스트)

---

## 작업 진행 원칙

1. **Phase 순서대로 진행**: 치명적 문제 → 높음 → 중간 → 낮음 순서로 처리한다.
2. **각 Task 완료 후 테스트 실행**: 기존 @WebMvcTest 시나리오 테스트가 모두 통과해야 한다.
3. **Task 단위로 커밋**: 하나의 Task = 하나의 커밋. 롤백이 용이하도록 한다.
4. **리뷰 ID 참조**: 각 Task는 `design/01-initial-code-review.md` 의 ID (예: ARCH-01) 를 참조한다.
5. **완료 기준**: 코드 변경 + 테스트 통과 + 로컬 실행 확인.
6. **리뷰 ID 완전성 유지**: `design/01-initial-code-review.md` 의 모든 리뷰 ID가 본 문서의 Task에 직접 매핑되어야 한다.

---

## Phase 1 - 치명적 보안 및 안정성 수정

> 운영 환경에 배포하기 전에 반드시 완료해야 하는 항목입니다.
> 이 Phase의 미완료 상태로 다중 인스턴스 운영 시 서비스가 불가능합니다.

---

### Task 1.0 - [SEC-04] ⭐ 평문 비밀번호 git 이력 제거 및 자격증명 교체

**참조:** [SEC-04 상세](../design/01-initial-code-review.md#sec-04-치명적---평문-비밀번호가-git-tracked-파일에-존재)
**예상 범위:** config/, apps/*/config/ + 인프라 (DB/Redis 비밀번호 교체)
**위험도:** 치명적 (git 이력에 비밀번호 영구 기록)
**선행 필수:** 다른 Task보다 반드시 먼저 수행

#### 작업 목록

- [x] **1.0.1** 현재 비밀번호가 commit된 커밋 이력 범위 파악
  ```bash
  git log --all --full-history -- "config/tc-db.properties"
  git log --all --full-history -- "apps/*/config/tc-redis.properties"
  ```

- [ ] **1.0.2** DB 비밀번호 즉시 교체 (인프라팀 협의)
  - 기존 `REDACTED_DB_PASSWORD` → 새 강력 비밀번호로 교체
  - 새 비밀번호는 환경변수 또는 Vault로 관리

- [ ] **1.0.3** Redis 비밀번호 즉시 교체 (인프라팀 협의)
  - 기존 `REDACTED_REDIS_PASSWORD` → 새 강력 비밀번호로 교체
  - Gateway Redis(6379), Business Redis(6380) 모두 교체

- [x] **1.0.4** `.gitignore` 에 config 파일 추가
  ```bash
  # .gitignore 에 추가
  config/tc-db.properties
  config/tc-db.mybatis.properties
  config/tc-redis.properties
  apps/tc-ui-backend-app/config/tc-redis.properties
  apps/tc-business-core-app/config/tc-redis.properties
  apps/tc-comm-gateway-app/config/tc-redis.properties
  ```

- [x] **1.0.5** template 파일 생성 (빈값으로 git 추적)
  ```
  config/tc-db.properties.template       (spring.datasource.password= 처럼 빈값)
  apps/tc-ui-backend-app/config/tc-redis.properties.template
  apps/tc-business-core-app/config/tc-redis.properties.template
  apps/tc-comm-gateway-app/config/tc-redis.properties.template
  ```

- [ ] **1.0.6** git 이력에서 비밀번호 파일 제거 (BFG Repo-Cleaner 사용)
  ```bash
  # 방법 1: BFG (권장)
  bfg --delete-files tc-db.properties --no-blob-protection
  bfg --delete-files tc-redis.properties --no-blob-protection
  git reflog expire --expire=now --all
  git gc --prune=now --aggressive

  # 방법 2: git filter-repo
  git filter-repo --path config/tc-db.properties --invert-paths
  ```
  > **주의**: 팀 전체와 협의 후 force push 진행. 모든 팀원 재clone 필요.

- [ ] **1.0.7** 원격 저장소 force push 및 팀 공지
  ```bash
  git push origin --force --all
  git push origin --force --tags
  ```

- [x] **1.0.8** 개발 환경 재설정 가이드 작성
  - template 파일에서 실제 config 파일 생성 방법
  - 환경변수 설정 방법 (또는 로컬 config 파일 작성 방법)

#### 완료 기준
- `git log -p --all | grep -i "REDACTED_DB_PASSWORD\|REDACTED_REDIS_PASSWORD"` 결과 없음
- 모든 config 파일이 `.gitignore` 에 포함됨
- template 파일로 신규 개발자 환경 설정 가능

---

### Task 1.1 - [SEC-01] JDK 직렬화 제거 → JSON 직렬화로 교체

**참조:** [SEC-01 상세](../design/01-initial-code-review.md#sec-01-치명적---jdk-직렬화-역직렬화-공격-취약점-rce-위험)
**예상 범위:** tc-ui-redis-adapter 모듈
**위험도:** 치명적 (RCE 가능)

#### 작업 목록

- [x] **1.1.1** `UiRedisConfiguration.java` 수정
  - `JdkSerializationRedisSerializer` → `GenericJackson2JsonRedisSerializer` 로 교체
  - `businessRedisTemplate` 의 value/hashValue serializer 변경
  - `gatewayRedisTemplate` 의 value/hashValue serializer 변경
  - 필요한 ObjectMapper 빈 생성 (JavaTimeModule 등록 포함)

- [x] **1.1.2** `RedisUiSessionEntry.java` 수정
  - `implements Serializable` 제거
  - Jackson 직렬화 가능하도록 기본 생성자 확인
  - `UserPrincipal` 이 Jackson 직렬화 가능한지 확인 (record → DTO 변환 필요 시 수정)

- [x] **1.1.3** `RedisUiAsyncResultEntry.java` 수정
  - `implements Serializable` 제거
  - `KafkaUiTaskReplyMessage` 가 Jackson 직렬화 가능한지 확인
  - Jackson 역직렬화 시 타입 정보 포함 설정 (`@JsonTypeInfo`) 검토

- [x] **1.1.4** Redis 데이터 마이그레이션 계획 수립
  - 기존 JDK 직렬화 데이터와 새 JSON 데이터 혼재 방지
  - 배포 전 Redis flush 또는 키 패턴 변경으로 격리

- [x] **1.1.5** 변경 후 테스트
  - UiSessionCacheService: put → get 왕복 테스트
  - AsyncResultStoreService: save → get 왕복 테스트
  - 기존 @WebMvcTest 시나리오 테스트 전체 실행

#### 완료 기준
- `JdkSerializationRedisSerializer` 가 코드베이스에 남아 있지 않음
- 세션 캐시 put/get 정상 동작 확인

---

### Task 1.2 - [ARCH-01] DualResponseRegistry Redis 기반으로 교체

**참조:** [ARCH-01 상세](../design/01-initial-code-review.md)
**예상 범위:** tc-ui-core, tc-ui-redis-adapter 모듈
**위험도:** 치명적 (다중 인스턴스 운영 불가)

#### 작업 목록

- [x] **1.2.1** tc-ui-core: `DualResponseRedisPort` 인터페이스 정의
  ```
  위치: libs/ui/tc-ui-core/src/.../port/DualResponseRedisPort.java
  ```
  - `void register(String traceId, long timeoutMs)` - Redis 등록
  - `void record(String traceId, String source, UiTaskResult result)` - 응답 수집
  - `void cancel(String traceId)` - 취소/정리
  - `Optional<UiDualTaskFinalResult> getResult(String traceId)` - 결과 조회

- [x] **1.2.2** tc-ui-core: `DualResponseRegistry` 리팩토링
  - 현재 `ConcurrentHashMap` 기반 인메모리 로직을 `DualResponseRedisPort` 를 통해 Redis로 위임
  - `CompletableFuture` 로컬 Future는 Redis Pub/Sub 이벤트 수신 시 완료
  - 타임아웃 처리: Redis TTL + `orTimeout()` 병행

- [x] **1.2.3** tc-ui-redis-adapter: `DualResponseRedisAdapter.java` 구현
  ```
  위치: libs/ui/adapter/tc-ui-redis-adapter/src/.../registry/DualResponseRedisAdapter.java
  ```
  - Redis Hash로 양쪽 응답 저장
    - Key: `tc:ui:backend:dual:{traceId}`
    - Field: `gateway` → gateway 결과 JSON
    - Field: `business` → business 결과 JSON
    - TTL: `timeoutMs + 5000ms`
  - Redis Pub/Sub으로 완료 알림
    - 완료 채널: `tc:ui:backend:dual:complete`
    - 구독 채널 필터: 메시지에 traceId 포함

- [x] **1.2.4** tc-ui-redis-adapter: Pub/Sub 구독자 구현
  ```
  위치: libs/ui/adapter/tc-ui-redis-adapter/src/.../registry/DualResponsePubSubListener.java
  ```
  - `MessageListenerAdapter` 등록
  - 완료 메시지 수신 시 해당 traceId의 `CompletableFuture` 완료 처리
  - 로컬 Map(Future 참조용)과 Redis 상태 분리

- [x] **1.2.5** `UiRedisConfiguration.java` 수정
  - Pub/Sub `RedisMessageListenerContainer` 빈 추가
  - `DualResponsePubSubListener` 채널 등록

- [x] **1.2.6** 변경 후 테스트
  - 단일 인스턴스 환경에서 DualResponse 정상 동작 확인
  - 타임아웃 동작 확인
  - 기존 @WebMvcTest 시나리오 테스트 전체 실행

- [ ] **1.2.7** (선택) 다중 인스턴스 로컬 검증
  - 동일 프로젝트를 포트 다르게 2개 기동
  - curl로 POST /api/eqp 요청 → Kafka 응답이 다른 인스턴스로 라우팅되는 경우 처리 확인

#### 완료 기준
- 2개 인스턴스 기동 시 DualResponse 정상 완료
- `ConcurrentHashMap<String, DualResponseTracker>` 이 코드에 남아 있지 않음

---

### Task 1.3 - [EX-04 / SEC-03] 권한 캐시 초기화 실패 시 failsafe 처리

**참조:** [EX-04 상세](../design/01-initial-code-review.md#ex-04-중요---uiapipermissioncache-초기화-실패-시-보안-개방)
**예상 범위:** tc-ui-web-adapter 모듈
**위험도:** 치명적 (보안 전체 개방)

#### 작업 목록

- [x] **1.3.1** `UiApiPermissionCache.java` 수정
  - `@PostConstruct` 에서 권한 로드 실패 시 `IllegalStateException` 발생 → 기동 중단
  - 또는 `initializationFailed` 플래그 → `isAuthorized()` 에서 전체 차단

  ```java
  // 권장: failsafe 모드 (기동은 허용하되 모든 권한 차단)
  private volatile boolean initializationFailed = false;

  @PostConstruct
  void init() {
      try {
          permissions = permissionPort.findAllActiveApiPermissions();
      } catch (Exception e) {
          log.error("권한 로드 실패 - failsafe 모드 활성화 (모든 API 차단)", e);
          initializationFailed = true;
      }
  }

  public boolean isAuthorized(...) {
      if (initializationFailed) {
          log.warn("failsafe 모드: 권한 캐시 미초기화 - 요청 차단");
          return false;
      }
      // 기존 로직
  }
  ```

- [x] **1.3.2** `isAuthorized()` 정책 변경: open by default → closed by default
  - 매핑된 권한이 없는 API는 기본 차단 (false 반환)
  - 공개 API는 `tc_ui_permission` 에 `permission_code = 'PUBLIC'` 등록
  - `/auth/login`, `/actuator/health` 는 `UiSecurityConfig` 에서 `permitAll()` 로 처리 (DB 무관)

- [x] **1.3.3** `tc_ui_permission` 테이블에 기존 공개 API 데이터 등록
  - 현재 사용 중인 모든 API 엔드포인트에 대한 permission_code 확인 및 등록
  - DLQ 조회, 비동기 결과 조회 등 기존 동작 유지
  - 반영: `docs/db_table/sample_data/postgres_insert_sample_data.sql`
    - ADMIN / DEVELOPER / OPERATOR 그룹 upsert
    - `tc_ui_permission` upsert + `tc_user_group_permission` 정책 매핑
    - `tc_user_info` 그룹별 샘플 사용자 1명(admin/developer/operator) 생성
    - `tc_user_group_member` 1:1 그룹 매핑

- [x] **1.3.4** 변경 후 테스트
  - 권한 로드 실패 시 모든 API 차단 확인
  - 권한 등록된 API 정상 접근 확인
  - 권한 미등록 API 차단 확인
  - 반영: `UiAuthScenarioTest`
    - `권한_캐시_초기화_실패시_보호_API_차단_403`
    - `권한_미등록_API_기본차단_403`
  - 검증: `.\gradlew :apps:tc-ui-backend-app:test --tests "com.nori.tc.apps.uibackend.scenario.UiAuthScenarioTest"` 성공

#### 완료 기준
- DB 연결 실패 시 서버가 "모두 허용"이 아닌 "모두 차단" 상태로 동작

---

### Task 1.4 - [ARCH-04] ⭐ Dual 발행 fire-and-forget → 브로커 확인 동기화

**참조:** [ARCH-04 상세](../design/01-initial-code-review.md#arch-04-치명적---dual-발행-fire-and-forget-브로커-실패를-호출부가-인지-불가)
**예상 범위:** tc-ui-kafka-adapter 모듈 (UiGatewayEventKafkaPublisher, UiBusinessEventKafkaPublisher)
**위험도:** 치명적 (브로커 실패 시 false success, 원인 파악 불가)

#### 작업 목록

- [x] **1.4.1** `UiGatewayEventKafkaPublisher.java` 수정
  - `kafkaTemplate.send(record).whenComplete(...)` → `kafkaTemplate.send(record).get(timeout)` 방식으로 변경
  - 발행 전용 타임아웃 상수 추가 (예: `PUBLISH_TIMEOUT_SECONDS = 3`)
  - `ExecutionException`, `TimeoutException` 을 `UiKafkaPublishException` (RuntimeException) 으로 래핑하여 throw

  ```java
  // 변경 후
  public void publish(KafkaUiTaskMessage message) {
      // ... route partition 조회, record 생성 ...
      try {
          SendResult<String, Object> result =
              kafkaTemplate.send(record).get(PUBLISH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
          log.debug("Gateway 발행 완료. partition={}, offset={}",
              result.getRecordMetadata().partition(),
              result.getRecordMetadata().offset());
      } catch (TimeoutException e) {
          log.error("Gateway 발행 타임아웃. traceId={}, eqpId={}", traceId, eqpId);
          throw new UiKafkaPublishException("Gateway 브로커 응답 타임아웃", e);
      } catch (ExecutionException e) {
          log.error("Gateway 발행 실패. traceId={}, eqpId={}", traceId, eqpId, e.getCause());
          throw new UiKafkaPublishException("Gateway 브로커 전송 실패", e.getCause());
      }
  }
  ```

- [x] **1.4.2** `UiBusinessEventKafkaPublisher.java` 동일 방식으로 수정

- [x] **1.4.3** `UiKafkaPublishException.java` 생성
  ```
  위치: libs/ui/adapter/tc-ui-kafka-adapter/src/.../exception/UiKafkaPublishException.java
  ```
  - `RuntimeException` 상속
  - 발행 대상(Gateway/Business), traceId, cause를 담는 생성자

- [x] **1.4.4** `EqpController.java` 검증
  - `publish()` 가 동기화되어 `UiKafkaPublishException` 을 throw하므로
    기존 `try-catch(Exception e)` 에서 올바르게 잡히는지 확인
  - Gateway 발행 성공 후 Business 발행 실패 시 보상 이벤트 발행 로직 연계 ([Task 2.4] 참조)

- [x] **1.4.5** 발행 타임아웃 설정 외부화
  ```yaml
  # application.yaml 또는 tc-messaging.properties
  tc.ui.backend.kafka.publish-timeout-seconds: 3
  ```

- [x] **1.4.6** 변경 후 테스트
  - 정상 발행: 브로커 정상 시 publish() 정상 완료 확인
  - 타임아웃 발행: 브로커 응답 지연 시 `UiKafkaPublishException` throw 확인
  - EqpController: 발행 실패 시 500 응답 확인 (타임아웃 504와 구분)
  - 기존 @WebMvcTest 시나리오 테스트 전체 실행

#### 완료 기준
- `kafkaTemplate.send(record).whenComplete(...)` 가 `UiGatewayEventKafkaPublisher` 에 없음
- 브로커 실패 시 `publish()` 호출부(EqpController)가 즉시 예외로 인지

---

## Phase 2 - 높음 위험도 수정

> 운영 중 데이터 유실 및 불일치를 유발하는 항목입니다.

---

### Task 2.1 - [EX-01] Kafka 파싱 실패 메시지 Dead Letter Topic 전송

**참조:** [EX-01 상세](../design/01-initial-code-review.md#ex-01-치명적---kafka-파싱-실패-메시지-영구-소멸)
**예상 범위:** tc-ui-kafka-adapter 모듈
**위험도:** 높음 (메시지 영구 소멸)

#### 작업 목록

- [x] **2.1.1** Dead Letter Topic 설정
  - 토픽명: `tc.ui.commands.DLT` (Dead Letter Topic)
  - Kafka Admin 또는 인프라에서 토픽 생성 (replication-factor, retention 설정)

- [x] **2.1.2** `UiKafkaConfiguration.java` 수정
  - `DefaultErrorHandler` + `DeadLetterPublishingRecoverer` 빈 추가
  - 재시도 없이 즉시 DLT로 전송 (파싱 실패는 재시도해도 의미 없음)

  ```java
  @Bean
  public DefaultErrorHandler dltErrorHandler(KafkaTemplate<String, String> template) {
      DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
          template,
          (record, ex) -> new TopicPartition("tc.ui.commands.DLT", record.partition())
      );
      // 재시도 없음 (파싱 오류는 재시도해도 동일 실패)
      return new DefaultErrorHandler(recoverer, new FixedBackOff(0L, 0));
  }
  ```

- [x] **2.1.3** `UiCommandKafkaSubscriber.java` 수정
  - `JsonProcessingException` catch 블록에서 DLT 전송 로직 추가
  - `ContainerStoppedException` 등 인프라 예외와 비즈니스 예외 구분 처리
  - DLT 전송 실패 시 로그 + 원본 payload 로그 기록 (추적 가능하도록)

- [ ] **2.1.4** DLT 모니터링 설정 (선택)
  - `tc.ui.commands.DLT` 에 쌓이는 메시지 수 알람 설정 (Kafka UI, Prometheus 등)

- [x] **2.1.5** 변경 후 테스트
  - 의도적으로 잘못된 JSON 메시지를 `tc.ui.commands` 에 발행
  - `tc.ui.commands.DLT` 에 메시지 도착 확인
  - 이후 정상 메시지 처리 확인 (DLT로 인한 consumer 중단 없음)

#### 완료 기준
- 파싱 실패 메시지가 `tc.ui.commands.DLT` 에 저장됨
- 기존 정상 메시지 처리에 영향 없음

---

### Task 2.2 - [EX-02] LogoutUseCase Redis evict 실패 처리 강화

**참조:** [EX-02 상세](../design/01-initial-code-review.md#ex-02-중요---logoutusecase-dbRedis-불일치-가능성)
**예상 범위:** tc-ui-core 모듈
**위험도:** 높음 (로그아웃 후 Redis TTL 동안 인증 통과)

#### 작업 목록

- [x] **2.2.1** `LogoutUseCase.java` 수정
  - `tokenCachePort.evict(token)` 을 try-catch 로 감싸고 실패 시 ERROR 로그
  - Redis 실패 시 로그아웃 전체를 실패로 처리하지 않음 (DB revoke는 이미 완료)

  ```java
  public void execute(String token) {
      sessionPort.revoke(token);

      try {
          tokenCachePort.evict(token);
      } catch (Exception e) {
          log.error("Redis 토큰 캐시 제거 실패 - DB revoke는 완료. " +
                    "캐시 TTL 만료 전까지 해당 토큰 유효할 수 있음. token=***", e);
          // 운영팀 알람 또는 재시도 스케줄링 고려
      }
  }
  ```

- [x] **2.2.2** `ValidateTokenUseCase.java` 에 revoke 확인 로직 추가
  - 캐시 히트된 세션이 DB에서 revoked = true 인 경우 캐시 무효화
  - 단, 이 검증을 매번 수행하면 DB 조회 부하 → 캐시 히트 확률이 낮아짐
  - 대안: 로그아웃 이벤트를 Redis Pub/Sub으로 전달하여 모든 인스턴스 캐시 무효화

  ```java
  // 방법 1: 캐시 히트 후 주기적 DB 검증 (30초에 1번)
  // 방법 2: Redis Pub/Sub 로그아웃 이벤트 → 전체 인스턴스 캐시 무효화
  ```

- [x] **2.2.3** 변경 후 테스트
  - tokenCachePort.evict() 가 예외 발생 시 로그아웃 응답이 정상(200) 임을 확인
  - DB에 revoked = true 로 저장됨을 확인

#### 완료 기준
- Redis evict 실패 시 LogoutUseCase가 예외를 전파하지 않고 정상 완료

---

### Task 2.3 - [ARCH-02 / DEP-01] tc-ui-core Port 기술 중립화

**참조:** [ARCH-02 상세](../design/01-initial-code-review.md#arch-02-중요---tc-ui-core-port가-kafka-계약-타입에-직접-의존), [DEP-01 상세](../design/01-initial-code-review.md#dep-01-중요---core-계층이-messaging-계약에-의존)
**예상 범위:** tc-ui-core, tc-ui-kafka-adapter 모듈
**위험도:** 높음 (헥사고날 구조 오염, 기술 교체 불가)

#### 작업 목록

- [x] **2.3.1** tc-ui-core: `UiCommandReply.java` 도메인 DTO 생성
  ```
  위치: libs/ui/tc-ui-core/src/.../domain/UiCommandReply.java
  또는: libs/ui/tc-ui-domain/src/.../task/UiCommandReply.java
  ```
  ```java
  public record UiCommandReply(
      String traceId,
      String source,
      String eventType,
      UiTaskStatus status,
      String errorCode,
      String errorMsg
  ) {}
  ```

- [x] **2.3.2** `UiCommandIngressPort.java` 시그니처 변경
  ```java
  // 변경 전
  void handle(KafkaUiTaskReplyMessage reply);

  // 변경 후
  void handle(UiCommandReply reply);
  ```

- [x] **2.3.3** `AsyncResultStorePort.java` 시그니처 변경
  ```java
  // 변경 전
  void save(String traceId, KafkaUiTaskReplyMessage reply);
  Optional<KafkaUiTaskReplyMessage> get(String traceId);

  // 변경 후
  void save(String traceId, UiCommandReply reply);
  Optional<UiCommandReply> get(String traceId);
  ```

- [x] **2.3.4** `UiCommandIngressService.java` 수정
  - `handle(KafkaUiTaskReplyMessage)` → `handle(UiCommandReply)` 로 수신 타입 변경
  - `KafkaUiTaskReplyMessage` 에서 `UiCommandReply` 로의 변환은 Kafka Adapter에서 담당

- [x] **2.3.5** `UiCommandKafkaSubscriber.java` 수정
  - `KafkaUiTaskReplyMessage` → `UiCommandReply` 변환 로직 추가
  - 변환 후 `ingressPort.handle(commandReply)` 호출

  ```java
  private UiCommandReply toCommandReply(KafkaUiTaskReplyMessage msg) {
      return new UiCommandReply(
          msg.metadata().traceId(),
          msg.metadata().source(),
          msg.metadata().eventType(),
          UiTaskStatus.valueOf(msg.data().STATUS()),
          msg.data().ERRORCODE(),
          msg.data().ERRORMSG()
      );
  }
  ```

- [x] **2.3.6** `AsyncResultStoreService.java` (Redis Adapter) 수정
  - `KafkaUiTaskReplyMessage` 대신 `UiCommandReply` 저장/조회

- [x] **2.3.7** `AsyncResultController.java` 수정
  - `AsyncResultStorePort.get()` 반환 타입이 `Optional<UiCommandReply>` 로 변경됨에 따라 응답 매핑 수정

- [x] **2.3.8** `tc-ui-core/build.gradle.kts` 에서 `tc-messaging-kafka-contract` 의존성 제거 확인

- [x] **2.3.9** 변경 후 테스트
  - 기존 @WebMvcTest 시나리오 테스트 전체 실행
  - EQP_START/END 비동기 결과 조회 정상 동작 확인

#### 완료 기준
- `tc-ui-core` 모듈의 `import` 에 `tc-messaging-kafka-contract` 가 없음
- Port 인터페이스가 `UiCommandReply` 만 사용

---

### Task 2.4 - [OOP-01] EQP 발행 실패 시 보상 처리 추가

**참조:** [OOP-01 상세](../design/01-initial-code-review.md#oop-01-중요---eqp-발행-실패-시-보상-트랜잭션-부재)
**예상 범위:** tc-ui-web-adapter, tc-ui-kafka-adapter 모듈
**위험도:** 높음 (Gateway/Business 데이터 불일치)

#### 작업 목록

- [x] **2.4.1** `EqpController.java` 수정
  - Gateway 발행 성공 여부를 추적하는 로컬 변수 추가
  - Business 발행 실패 시 Gateway에 보상 이벤트 발행

  ```java
  boolean gatewayPublished = false;
  try {
      gatewayPort.publish(gatewayMsg);
      gatewayPublished = true;
      businessPort.publish(businessMsg);
  } catch (Exception e) {
      if (gatewayPublished) {
          log.error("Business 발행 실패 - Gateway 보상 이벤트 발행: traceId={}", traceId);
          try {
              gatewayPort.publish(buildRollbackMessage(traceId, eqpId, eventType));
          } catch (Exception rollbackEx) {
              log.error("보상 이벤트 발행도 실패 - 수동 조치 필요: traceId={}, eqpId={}", traceId, eqpId, rollbackEx);
              // 운영팀 알람 발송 (Slack, PagerDuty 등)
          }
      }
      registry.cancel(traceId);
      deferredResult.setErrorResult(ResponseEntity.status(500)
          .body(ApiResponse.error("PUBLISH_FAILED", "요청 처리 중 오류가 발생했습니다.")));
  }
  ```

- [x] **2.4.2** 보상 식별 정보 추가 (기존 `EQP_DELETE` + rollback 플래그 사용)
  - 신규 이벤트 타입을 추가하지 않고 `uiMessage=ROLLBACK|...` 형식으로 보상 요청임을 식별

- [x] **2.4.3** Gateway가 보상 `EQP_DELETE` 이벤트를 처리하는지 확인
  - `GatewayUiTaskProcessorRegistry` 에서 rollback 플래그(`uiMessage` prefix) 감지 로그 및 삭제 처리 연계 확인

- [x] **2.4.4** 변경 후 테스트
  - Business 발행 실패 시 Gateway 보상 이벤트 발행 확인
  - 보상 이벤트 발행도 실패 시 ERROR 로그 확인

#### 완료 기준
- Gateway 발행 후 Business 발행 실패 시 보상 이벤트가 Gateway로 전송됨

---

### Task 2.5 - [SEC-02] Redis Key 토큰 원문 → SHA-256 해시로 교체

**참조:** [SEC-02 상세](../design/01-initial-code-review.md#sec-02-중요---redis-key에-토큰-원문-사용)
**예상 범위:** tc-ui-redis-adapter 모듈
**위험도:** 높음 (Redis 접근 시 유효 토큰 전체 노출)

#### 작업 목록

- [x] **2.5.1** `UiSessionCacheService.java` 수정
  - `buildCacheKey(String token)` private 메서드 추가 (SHA-256 해시)

  ```java
  private String buildCacheKey(String token) {
      try {
          MessageDigest md = MessageDigest.getInstance("SHA-256");
          byte[] hash = md.digest(token.getBytes(StandardCharsets.UTF_8));
          return SESSION_KEY_PREFIX + HexFormat.of().formatHex(hash);
      } catch (NoSuchAlgorithmException e) {
          throw new IllegalStateException("SHA-256 not available", e);
      }
  }
  ```

  - `get()`, `put()`, `evict()` 에서 token 대신 `buildCacheKey(token)` 사용

- [x] **2.5.2** Redis 기존 세션 캐시 무효화 계획
  - 배포 시 기존 `tc:ui:backend:session:{원문토큰}` 형식 키 만료 또는 일괄 삭제
  - 사용자는 재로그인 필요 (공지 또는 강제 로그아웃 처리)

- [x] **2.5.3** 변경 후 테스트
  - put 후 get 정상 동작 확인
  - evict 후 get 캐시 미스 확인
  - Redis CLI에서 SCAN으로 키 패턴 확인 (원문 토큰이 없는지)

#### 완료 기준
- Redis 키에 토큰 원문이 포함되지 않음
- put → get → evict 왕복 정상 동작

---

### Task 2.6 - [PERF-02 / DEP-02] GatewayEquipmentProfileSnapshot 메시지 경량화 및 위치 정리

**참조:** [PERF-02 상세](../design/01-initial-code-review.md#perf-02-중요---gatewayequipmentprofilesnapshot-kafka-메시지-크기), [DEP-02 상세](../design/01-initial-code-review.md#dep-02-경미---gatewayequipmentprofilesnapshot이-kafka-계약-레이어에-위치)
**예상 범위:** tc-messaging-kafka-contract, tc-ui-kafka-adapter, tc-ui-core, tc-comm-domain(또는 tc-db-domain)
**위험도:** 높음 (대형 메시지로 인한 발행 실패 + 도메인 레이어 의존성 오염)

#### 작업 목록

- [x] **2.6.1** 이벤트별 payload 정책 정리
  - EQP_START/END처럼 프로파일 전체가 불필요한 이벤트는 `equipmentProfile = null` 또는 경량 DTO 사용
  - 생성/수정처럼 실제로 필요한 이벤트만 상세 스냅샷 포함

- [x] **2.6.2** `GatewayEquipmentProfileSnapshot` 위치 이동
  - Kafka 계약 모듈(`tc-messaging-kafka-contract`)에서 분리
  - 공용 도메인 모듈(`tc-comm-domain` 또는 `tc-db-domain`)로 이동 후 계약 모듈은 참조만 하도록 변경

- [x] **2.6.3** 메시지 크기 가드레일 추가
  - Kafka producer 설정의 `max.request.size` 점검
  - 브로커 `message.max.bytes` 점검
  - 필요 시 사전 직렬화 크기 측정 로깅 또는 초과 방어 로직 추가

- [x] **2.6.4** 변경 후 테스트
  - 파라미터/포트 수가 많은 설비 데이터로 발행 시나리오 테스트
  - `RecordTooLargeException` 미발생 확인
  - 스냅샷 이동 후 컴파일/의존성 그래프 정상 확인

#### 완료 기준
- `GatewayEquipmentProfileSnapshot` 이 Kafka 계약 모듈의 소유 타입이 아님
- 대형 설비 기준 발행 시 `RecordTooLargeException` 없이 정상 처리

---

## Phase 3 - 중간 위험도 개선

> 운영 성능과 안정성을 높이는 항목입니다.

---

### Task 3.1 - [EX-03] lastSeenAt 업데이트 실패 예외 차단

**참조:** [EX-03 상세](../design/01-initial-code-review.md#ex-03-중요---validatetokenusecase-lastseenat-실패-시-인증-실패-전파-위험)
**예상 범위:** tc-ui-core 모듈

#### 작업 목록

- [x] **3.1.1** `ValidateTokenUseCase.java` 에서 `lastSeenAt` 업데이트 실패 예외를 격리 (인증 성공 유지)

  ```java
  try {
      sessionPort.updateLastSeenAt(token, OffsetDateTime.now());
  } catch (Exception e) {
      log.warn("lastSeenAt 업데이트 실패 (인증은 정상 처리): token=***, error={}", e.getMessage());
  }
  ```

- [x] **3.1.2** 변경 후 테스트
  - updateLastSeenAt 예외 발생 시 인증 성공 응답 확인

---

### Task 3.2 - [PERF-01] lastSeenAt 비동기 업데이트로 전환

**참조:** [PERF-01 상세](../design/01-initial-code-review.md#perf-01-중요---lastseenat-동기-db-업데이트-캐시-미스-경로)
**예상 범위:** tc-ui-core 모듈

#### 작업 목록

- [x] **3.2.1** 비동기 실행 방식 적용 확인 (CompletableFuture 기반 비동기 업데이트 적용)
  - `TcUiBackendAutoConfiguration` 또는 별도 AsyncConfig에 `@EnableAsync` 추가
  - 전용 ThreadPool 설정 (core: 2, max: 4, queue: 100)

- [x] **3.2.2** `ValidateTokenUseCase.java` 수정
  - `sessionPort.updateLastSeenAt()` 호출을 @Async 메서드로 분리

  ```java
  // ValidateTokenUseCase.java
  asyncUpdater.updateLastSeenAt(token);  // 비동기 실행, 결과 대기 없음

  // UiSessionAsyncUpdater.java (새 클래스)
  @Component
  public class UiSessionAsyncUpdater {
      @Async("sessionUpdateExecutor")
      public void updateLastSeenAt(String token) {
          try {
              sessionPort.updateLastSeenAt(token, OffsetDateTime.now());
          } catch (Exception e) {
              log.warn("lastSeenAt 비동기 업데이트 실패: {}", e.getMessage());
          }
      }
  }
  ```

- [x] **3.2.3** 변경 후 테스트
  - 인증 응답 시간이 개선됐는지 확인 (DB write 시간 제외)
  - 비동기 업데이트가 실제로 실행되는지 로그 확인

---

### Task 3.3 - [STAB-01 / API-02] EQP_START/END 비동기 결과 상태 구분 개선

**참조:** [STAB-01 상세](../design/01-initial-code-review.md#stab-01-중요---eqpstartend-비동기-결과-소멸-가능성), [API-02 상세](../design/01-initial-code-review.md#api-02-중요---polling-처리-중과-없음-구분-불가)
**예상 범위:** tc-ui-core, tc-ui-redis-adapter, tc-ui-web-adapter 모듈

#### 작업 목록

- [x] **3.3.1** `AsyncStatus` enum 추가 (tc-ui-domain 또는 tc-ui-core)
  ```java
  public enum AsyncStatus { PENDING, COMPLETED, TIMEOUT }
  ```

- [x] **3.3.2** `AsyncResultStorePort.java` 에 상태 관리 메서드 추가
  ```java
  void registerPending(String traceId, long timeoutMs);
  void markCompleted(String traceId, UiCommandReply reply);
  void markTimeout(String traceId);
  Optional<AsyncResultEntry> getWithStatus(String traceId);
  ```

- [x] **3.3.3** `EqpController.java` 수정
  - EQP_START/END 발행 직전 `registerPending(traceId, timeoutMs)` 호출

- [x] **3.3.4** `UiCommandIngressService.java` 수정
  - EQP_START_REP/EQP_END_REP 수신 시 `markCompleted()` 호출

- [x] **3.3.5** 타임아웃 스케줄러 추가
  - 일정 시간(예: timeoutMs + 5초) 경과 후 PENDING 상태를 TIMEOUT으로 변경
  - Redis TTL 활용 또는 별도 @Scheduled 처리

- [x] **3.3.6** `AsyncResultController.java` 응답 코드 변경
  - PENDING → 202 Accepted (처리 중)
  - COMPLETED → 200 OK (결과 포함)
  - TIMEOUT → 408 Request Timeout
  - 없음 (잘못된 traceId) → 404 Not Found

---

### Task 3.4 - [OOP-02] UiApiPermissionCache Closed by Default 전환

**참조:** [OOP-02 상세](../design/01-initial-code-review.md#oop-02-보안설계---uiapipermissioncache-기본-개방-정책)
**예상 범위:** tc-ui-web-adapter 모듈

> Task 1.3에서 이미 `closed by default` 로 변경했다면 이 Task는 Task 1.3 에 포함됩니다.

---

### Task 3.5 - [QUALITY-01] DeferredResult 비동기 재디스패치 토큰 이중 검증 제거

**참조:** [QUALITY-01 상세](../design/01-initial-code-review.md#quality-01-중요---deferredresult-비동기-재디스패치-시-토큰-이중-검증)
**예상 범위:** tc-ui-web-adapter 모듈

#### 작업 목록

- [x] **3.5.1** `UiTokenAuthenticationFilter.java` 수정
  ```java
  @Override
  protected void doFilterInternal(HttpServletRequest request,
                                   HttpServletResponse response,
                                   FilterChain chain) throws ServletException, IOException {
      // 비동기 재디스패치이고 이미 인증 정보가 있는 경우만 스킵
      if (isAsyncDispatch(request)
              && SecurityContextHolder.getContext().getAuthentication() != null) {
          chain.doFilter(request, response);
          return;
      }
      // 토큰 검증 로직
  }

  @Override
  protected boolean shouldNotFilterAsyncDispatch() {
      return false;  // 유지하되 위의 중복 체크로 실질적 재검증 방지
  }
  ```

- [x] **3.5.2** 변경 후 테스트
  - DeferredResult 응답 시 SecurityContext 인증 상태 유지 확인
  - Redis 조회 횟수가 1회인지 확인 (로그 또는 Spy 활용)

---

### Task 3.6 - [MEM-01] DualResponseTracker 정리 finally 블록으로 보장

**참조:** [MEM-01 상세](../design/01-initial-code-review.md#mem-01-중요---dualresponseregistry-trackers-누수-가능-경로-검증-필요)
**예상 범위:** tc-ui-core 모듈

#### 작업 목록

- [x] **3.6.1** `DualResponseRegistry.java` 의 `whenComplete` 수정
  ```java
  .whenComplete((result, ex) -> {
      try {
          handleCompletion(traceId, deferredResult, result, ex);
      } finally {
          trackers.remove(traceId);  // 반드시 실행
      }
  });
  ```

- [x] **3.6.2** `registry.size()` 또는 상태 확인 메서드 추가 (테스트 및 모니터링용)

- [x] **3.6.3** 변경 후 테스트
  - 정상 완료 후 trackers.size() == 0 확인
  - 타임아웃 후 trackers.size() == 0 확인
  - cancel 후 trackers.size() == 0 확인

---

### Task 3.7 - [PERF-03] UiApiPermissionCache 주기적 갱신 추가

**참조:** [PERF-03 상세](../design/01-initial-code-review.md#perf-03-경미---uiapipermissioncache-동적-갱신-불가)
**예상 범위:** tc-ui-web-adapter 모듈

#### 작업 목록

- [x] **3.7.1** `UiApiPermissionCache.java` 에 `@Scheduled` 메서드 추가
  ```java
  @Scheduled(fixedDelayString = "${tc.ui.backend.permission.refresh-interval-ms:300000}")
  public void refresh() {
      try {
          List<TcUiPermission> updated = permissionPort.findAllActiveApiPermissions();
          this.permissions = updated;
          log.info("권한 캐시 갱신: {}건", updated.size());
      } catch (Exception e) {
          log.warn("권한 캐시 갱신 실패 - 기존 캐시 유지");
      }
  }
  ```

- [x] **3.7.2** `@EnableScheduling` 설정 확인 또는 추가

---

### Task 3.8 - [QUALITY-04] ⭐ AuthController 강제 캐스팅 → 안전한 instanceof 패턴

**참조:** [QUALITY-04 상세](../design/01-initial-code-review.md#quality-04-중간---authcontroller-강제-캐스팅--classcastexception-위험)
**예상 범위:** tc-ui-web-adapter 모듈
**위험도:** 중간 (비정상 SecurityContext 시 ClassCastException → 500)

#### 작업 목록

- [x] **3.8.1** `AuthController.java` 수정 - `logout()` 메서드
  - `(String) authentication.getCredentials()` → `instanceof String token` 패턴 매칭으로 변경
  - 타입 불일치 시 401 응답 반환 (500 방지)

- [x] **3.8.2** `AuthController.java` 수정 - `me()` 메서드
  - `(UserPrincipal) authentication.getPrincipal()` → `instanceof UserPrincipal principal` 패턴 매칭으로 변경
  - 타입 불일치 시 401 응답 반환

- [x] **3.8.3** 변경 후 테스트
  - MockMvc에서 Anonymous Authentication으로 `/auth/logout` 요청 시 500이 아닌 401 확인
  - MockMvc에서 Anonymous Authentication으로 `/auth/me` 요청 시 500이 아닌 401 확인

#### 완료 기준
- `AuthController` 에 강제 캐스팅 `(String)`, `(UserPrincipal)` 이 없음
- 비정상 Authentication 타입에서 500 대신 401 반환

---

### Task 3.9 - [EX-05] ⭐ EqpSequentialProcessor 무로그 예외 삼킴 수정

**참조:** [EX-05 상세](../design/01-initial-code-review.md#ex-05-낮음---eqpsequentialprocessor-예외-삼킴--무로그)
**예상 범위:** tc-comm-core 모듈 (libs/comm/tc-comm-core)
**위험도:** 낮음 (DLQ/격리 실패가 무소음으로 사라짐)

#### 작업 목록

- [x] **3.9.1** `EqpSequentialProcessor.java:313` DLQ 발행 실패 catch 블록에 `log.error()` 추가
  ```java
  } catch (Exception dlqEx) {
      log.error("DLQ 발행 실패 - 메시지 영구 유실 위험. eqpId={}, reasonCode={}",
                profile.equipmentId(), reasonCode, dlqEx);
  }
  ```

- [x] **3.9.2** `EqpSequentialProcessor.java:320` 격리 실패 catch 블록에 `log.error()` 추가
  ```java
  } catch (Exception qEx) {
      log.error("설비 격리 실패 - 수동 격리 조치 필요. eqpId={}, reasonCode={}",
                profile.equipmentId(), reasonCode, qEx);
  }
  ```

- [x] **3.9.3** 코드베이스 전체에서 유사 패턴 확인
  ```bash
  # 비어 있거나 주석만 있는 catch 블록 탐색
  grep -rn "catch.*Exception" libs/comm/ | grep -v "log\." | grep -v "//"
  ```

- [x] **3.9.4** 변경 후 테스트
  - dlqPublisherPort 예외 발생 시 ERROR 로그 출력 확인
  - quarantinePort 예외 발생 시 ERROR 로그 출력 확인

#### 완료 기준
- `EqpSequentialProcessor` 의 DLQ/격리 catch 블록에 `log.error()` 호출 존재

---

### Task 3.10 - [STAB-02] DualResponseRegistry 완료/정리 경합 방지

**참조:** [STAB-02 상세](../design/01-initial-code-review.md#stab-02-중요---dualresponseregistry-타임아웃-후-정리-불확실성)
**예상 범위:** tc-ui-core, tc-ui-web-adapter 모듈
**위험도:** 중간 (중복 완료 처리 시 IllegalStateException 위험)

#### 작업 목록

- [x] **3.10.1** 완료 처리 단일화
  - `setResult/setErrorResult` 호출 경로를 하나의 메서드로 통합
  - `AtomicBoolean completed` 로 1회만 응답 완료되도록 보장

- [x] **3.10.2** 정리 로직 보장
  - 완료/타임아웃/취소 모든 경로에서 `trackers.remove(traceId)` 가 반드시 실행되도록 `finally` 구조 적용

- [x] **3.10.3** 경합 시나리오 테스트 추가
  - `timeout` 과 `cancel` 이 거의 동시에 발생하는 케이스
  - Gateway/Business 응답 지연으로 완료 경로가 교차되는 케이스

#### 완료 기준
- 하나의 `traceId` 에 대해 `DeferredResult` 완료가 최대 1회만 발생
- 경합 상황에서도 `IllegalStateException` 없이 `trackers` 정리 보장

---

## Phase 4 - 낮음 위험도 개선 및 운영성 향상

---

### Task 4.1 - [QUALITY-02] 401 응답 포맷 통일

**참조:** [QUALITY-02 상세](../design/01-initial-code-review.md#quality-02-경미---401-응답-포맷-불일치)
**예상 범위:** tc-ui-web-adapter 모듈

#### 작업 목록

- [ ] **4.1.1** `UiTokenAuthenticationFilter.java` 수정
  - `response.getWriter().write(...)` 대신 `ApiResponse.error()` JSON 직렬화 후 write

- [ ] **4.1.2** `AuthenticationEntryPoint` 커스텀 구현
  - `UiAuthenticationEntryPoint.java` 생성: 항상 `ApiResponse.error()` 포맷 반환

---

### Task 4.2 - [OOP-03] KafkaUiTaskReplyData 필드명 수정

**참조:** [OOP-03 상세](../design/01-initial-code-review.md#oop-03-코드-컨벤션---kafkauitaskreplydata-필드명-위반)
**예상 범위:** tc-messaging-kafka-contract 모듈

#### 작업 목록

- [ ] **4.2.1** `KafkaUiTaskReplyData.java` 수정
  ```java
  // 변경 전
  String STATUS, ERRORMSG, ERRORCODE

  // 변경 후 (@JsonProperty 로 JSON 키 유지)
  @JsonProperty("STATUS")   String status,
  @JsonProperty("ERRORMSG") String errorMsg,
  @JsonProperty("ERRORCODE") String errorCode
  ```

- [ ] **4.2.2** 사용처 전체 수정 (`.STATUS()` → `.status()` 등)

- [ ] **4.2.3** Gateway/Business Core와 JSON 키 대소문자 계약 재확인

---

### Task 4.3 - [ARCH-03] ComponentScan 범위 명시화

**참조:** [ARCH-03 상세](../design/01-initial-code-review.md#arch-03-구조적---componentscan-과다-스캔)
**예상 범위:** tc-ui-backend-starter 모듈

#### 작업 목록

- [ ] **4.3.1** 각 어댑터 모듈에 `@AutoConfiguration` 클래스 추가
- [ ] **4.3.2** `TcUiBackendAutoConfiguration.java` 에서 `@ComponentScan` 범위를 core 패키지로 한정
- [ ] **4.3.3** 각 어댑터 AutoConfiguration을 `@Import` 로 명시 등록

---

### Task 4.4 - [OPS-01] Metrics 도입 (Micrometer)

**참조:** [OPS-01 상세](../design/01-initial-code-review.md#ops-01-중요---metrics-부재)
**예상 범위:** tc-ui-core, tc-ui-redis-adapter 모듈

#### 작업 목록

- [ ] **4.4.1** `tc-ui-backend-app/build.gradle.kts` 에 Micrometer 의존성 추가
  ```kotlin
  implementation(libs.spring.boot.starter.actuator)
  runtimeOnly(libs.micrometer.registry.prometheus)  // 또는 사용 중인 메트릭 시스템
  ```

- [ ] **4.4.2** `DualResponseRegistry.java` 에 메트릭 추가
  - `dual_response.registered` counter
  - `dual_response.completed` counter (tag: status=success|timeout|cancelled)
  - `dual_response.duration` timer

- [ ] **4.4.3** `UiSessionCacheService.java` 에 메트릭 추가
  - `session_cache.hit` counter
  - `session_cache.miss` counter

- [ ] **4.4.4** `UiCommandKafkaSubscriber.java` 에 메트릭 추가
  - `kafka.command.received` counter (tag: eventType)
  - `kafka.command.parse_error` counter

---

### Task 4.5 - [OPS-02] Distributed Tracing MDC 연계

**참조:** [OPS-02 상세](../design/01-initial-code-review.md#ops-02-중요---distributed-tracing-미연계)
**예상 범위:** tc-ui-core, tc-ui-kafka-adapter, tc-ui-web-adapter 모듈

#### 작업 목록

- [ ] **4.5.1** `UiTokenAuthenticationFilter.java` 에 MDC 설정 추가
  - 인증 완료 후 `MDC.put("traceId", requestId)` 또는 X-Request-Id 헤더 활용

- [ ] **4.5.2** `UiCommandKafkaSubscriber.java` 에 MDC 설정 추가
  - Kafka 메시지의 `traceId` 를 MDC에 설정 후 처리, 완료 후 제거

- [ ] **4.5.3** `EqpController.java` 에 MDC 설정 추가
  - DualResponse traceId를 MDC에 설정

- [ ] **4.5.4** logback 설정 파일에 `%X{traceId}` 패턴 추가

---

### Task 4.6 - [TEST-01] DualResponseRegistry 동시성 단위 테스트 추가

**참조:** [TEST-01 상세](../design/01-initial-code-review.md#test-01-중요---dualresponseregistry-동시성-테스트-부재)
**예상 범위:** tc-ui-core 모듈 테스트

#### 작업 목록

- [ ] **4.6.1** `DualResponseRegistryTest.java` 작성
  - 정상: Gateway + Business 순서대로 응답 → PASS 결과
  - 정상: Business + Gateway 역순으로 응답 → PASS 결과
  - 하나라도 FAIL → 최종 FAIL 결과
  - 타임아웃: 한쪽 응답만 수신 → TimeoutException
  - 동시성: 다수 traceId 동시 처리 → 각각 정상 완료
  - 정리: 완료 후 trackers.size() == 0

---

### Task 4.7 - [TEST-02] UseCase 단위 테스트 추가

**참조:** [TEST-02 상세](../design/01-initial-code-review.md#test-02-중요---usecase-단위-테스트-부재)
**예상 범위:** tc-ui-core 모듈 테스트

#### 작업 목록

- [ ] **4.7.1** `LoginUseCaseTest.java`
  - 정상 로그인 → AuthToken 반환
  - 미존재 사용자 → 동일 예외 (사용자 열거 방지)
  - 비밀번호 불일치 → 동일 예외 (사용자 열거 방지)
  - 비활성 계정 → 예외
  - 토큰 생성 후 세션 저장 확인

- [ ] **4.7.2** `ValidateTokenUseCaseTest.java`
  - 캐시 히트 → DB 미조회 확인
  - 캐시 미스 → DB 조회 후 캐시 저장
  - 만료 세션 → 예외
  - revoked 세션 → 예외
  - lastSeenAt 실패 → 인증 성공 확인

- [ ] **4.7.3** `LogoutUseCaseTest.java`
  - 정상 로그아웃 → DB revoke + 캐시 제거
  - Redis evict 실패 → 예외 미전파, 정상 완료

---

### Task 4.8 - [SEC-05] ⭐ 플러그인 서명 검증 구현

**참조:** [SEC-05 상세](../design/01-initial-code-review.md#sec-05-중간---플러그인-서명-검증-미구현-운영-반영-전-필수)
**예상 범위:** tc-comm-gateway-plugin-adapter 모듈 (libs/comm/adapter)
**위험도:** 중간 (공급망 공격 가능성, 코드 자체에서 "운영 반영 전 필수"로 명시)

#### 작업 목록

- [ ] **4.8.1** `GatewaySocketPluginRuntimeManager.java` 분석
  - 현재 플러그인 로드 흐름 파악 (URLClassLoader, JAR 파일 처리 방식)
  - `SECURITY_TODO_BACKLOG` 에 명시된 항목 전체 검토

- [ ] **4.8.2** 플러그인 허용 해시 목록 관리 방식 결정
  - 방법 A: DB 테이블에 `plugin_allowlist(jar_name, sha256, enabled)` 저장
  - 방법 B: 설정 파일에 allowlist 관리 (`tc-plugin.properties`)
  - 방법 C: 배포 파이프라인에서 서명된 JAR만 배포 디렉터리에 위치

- [ ] **4.8.3** JAR 파일 로드 전 SHA-256 해시 검증 구현
  - 로드 시점에 파일 해시를 계산하고 allowlist와 비교
  - 불일치 시 `PluginSecurityException` throw + ERROR 로그

- [ ] **4.8.4** JAR 서명 검증 구현 (선택 - 보안 요구 수준에 따라)
  - Java `JarFile` API의 서명 검증 기능 활용
  - 신뢰 인증서 목록 관리 방식 결정

- [ ] **4.8.5** `SECURITY_TODO_BACKLOG` 에서 완료된 항목 제거 또는 상태 업데이트

#### 완료 기준
- 플러그인 JAR 로드 전 해시 검증이 실행됨
- 미허용 JAR 로드 시도 시 예외 발생 + ERROR 로그

---

### Task 4.9 - [QUALITY-03] EqpController 발행 실패 흐름 단순화

**참조:** [QUALITY-03 상세](../design/01-initial-code-review.md#quality-03-경미---eqpcontroller-발행-실패-흐름-복잡성)
**예상 범위:** tc-ui-web-adapter, tc-ui-core 모듈

#### 작업 목록

- [ ] **4.9.1** `EqpController.create()` 실패 경로 단순화
  - `future.cancel(true)` + `whenComplete(CancellationException)` 의 간접 흐름 제거
  - 발행 실패 시 `registry.cancel(traceId)` 후 `deferredResult.setErrorResult(...)` 를 즉시 호출하고 반환

- [ ] **4.9.2** 실패 원인 로그 일원화
  - 발행 실패, 보상 실패, 응답 반환 코드(500/504)를 구조화 로그로 분리 기록

- [ ] **4.9.3** 변경 후 테스트
  - Gateway/Business 발행 실패 시 즉시 500 반환 확인
  - 타임아웃(504)과 발행 실패(500) 경계가 명확히 유지되는지 확인

#### 완료 기준
- `EqpController` 의 발행 실패 경로에 `CancellationException` 의존 로직이 없음
- 발행 실패와 타임아웃 응답이 분리되어 반환됨

---

### Task 4.10 - [API-01] DELETE 요청 Body 의존 제거

**참조:** [API-01 상세](../design/01-initial-code-review.md#api-01-경미---delete-메서드-request-body)
**예상 범위:** tc-ui-web-adapter, tc-ui-core 모듈

#### 작업 목록

- [ ] **4.10.1** `DELETE /api/eqp/{eqpId}` 계약 정리
  - 삭제에 필요한 값은 경로 변수/쿼리 파라미터로만 전달
  - Request Body 의존 제거

- [ ] **4.10.2** 하위 호환 전환 전략 적용
  - 기존 클라이언트가 body를 보내는 경우 임시 경고 로그 후 무시 또는 명시적 400 반환 정책 결정

- [ ] **4.10.3** 변경 후 테스트
  - body 없이 삭제 요청 정상 처리 확인
  - 프록시/게이트웨이 구간에서 body 유실 환경에서도 동일 동작 확인

#### 완료 기준
- DELETE API가 Request Body 없이 동일 기능을 제공
- 클라이언트/게이트웨이 환경 차이에 따른 동작 불일치가 없음

---

## 전체 작업 체크리스트

> 이 섹션을 복사하여 작업 추적에 활용하세요.

### Phase 1 (치명적 - 배포 전 필수)
- [ ] Task 1.0 - ⭐ 평문 비밀번호 git 이력 제거 + 자격증명 교체 [SEC-04]
- [x] Task 1.1 - JDK 직렬화 → JSON 직렬화 교체 [SEC-01]
- [x] Task 1.2 - DualResponseRegistry Redis 기반 교체 [ARCH-01]
- [ ] Task 1.3 - 권한 캐시 failsafe + closed by default [EX-04/SEC-03]
- [x] Task 1.4 - ⭐ Dual 발행 fire-and-forget → 브로커 확인 동기화 [ARCH-04]

### Phase 2 (높음 - 빠른 수정 필요)
- [x] Task 2.1 - Kafka 파싱 실패 Dead Letter Topic [EX-01]
- [x] Task 2.2 - LogoutUseCase Redis evict 실패 처리 [EX-02]
- [x] Task 2.3 - tc-ui-core Port 기술 중립화 [ARCH-02/DEP-01]
- [x] Task 2.4 - EQP 발행 실패 보상 처리 [OOP-01]
- [x] Task 2.5 - Redis Key 토큰 SHA-256 해시 [SEC-02]
- [x] Task 2.6 - GatewayEquipmentProfileSnapshot 경량화 + 위치 정리 [PERF-02/DEP-02]

### Phase 3 (중간 - 안정성/성능 개선)
- [x] Task 3.1 - lastSeenAt 실패 예외 차단 [EX-03]
- [x] Task 3.2 - lastSeenAt 비동기 업데이트 [PERF-01]
- [x] Task 3.3 - EQP_START/END 상태 구분 개선 [STAB-01/API-02]
- [x] Task 3.4 - 권한 캐시 Closed by Default (1.3에 포함 가능) [OOP-02]
- [x] Task 3.5 - 비동기 재디스패치 이중 검증 제거 [QUALITY-01]
- [x] Task 3.6 - DualResponseTracker 정리 finally 보장 [MEM-01]
- [x] Task 3.7 - UiApiPermissionCache 주기적 갱신 [PERF-03]
- [x] Task 3.8 - ⭐ AuthController 강제 캐스팅 → instanceof 패턴 [QUALITY-04]
- [x] Task 3.9 - ⭐ EqpSequentialProcessor 무로그 예외 삼킴 수정 [EX-05]
- [x] Task 3.10 - DualResponseRegistry 완료/정리 경합 방지 [STAB-02]

### Phase 4 (낮음 - 운영성/코드품질)
- [ ] Task 4.1 - 401 응답 포맷 통일 [QUALITY-02]
- [ ] Task 4.2 - KafkaUiTaskReplyData 필드명 수정 [OOP-03]
- [ ] Task 4.3 - ComponentScan 범위 명시화 [ARCH-03]
- [ ] Task 4.4 - Metrics 도입 (Micrometer) [OPS-01]
- [ ] Task 4.5 - Distributed Tracing MDC 연계 [OPS-02]
- [ ] Task 4.6 - DualResponseRegistry 동시성 테스트 [TEST-01]
- [ ] Task 4.7 - UseCase 단위 테스트 추가 [TEST-02]
- [ ] Task 4.8 - ⭐ 플러그인 서명 검증 구현 [SEC-05]
- [ ] Task 4.9 - EqpController 발행 실패 흐름 단순화 [QUALITY-03]
- [ ] Task 4.10 - DELETE 요청 Body 의존 제거 [API-01]
