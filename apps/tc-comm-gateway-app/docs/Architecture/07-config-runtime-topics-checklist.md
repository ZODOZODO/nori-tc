# 07. 설정 / 런타임 / 토픽 체크리스트 안내서

## 문서 목적

이 문서는 `tc-comm-gateway-app`를 운영/개발 환경에서 실행할 때 필요한 설정과 점검 포인트를 정리한 체크리스트입니다.

초급 개발자가 가장 자주 겪는 문제는 "코드는 맞아 보이는데 실행이 안 되는" 상황입니다. 이 문서는 그런 문제를 줄이기 위해 다음을 중심으로 설명합니다.

1. 설정 파일 구조 (`application.yaml` + `config/*.properties`)
2. 프로퍼티 prefix별 역할
3. Kafka topic/partition 불변조건
4. Netty/라이프사이클/큐 관련 필수 값
5. 기동 전/기동 후 체크리스트

관련 문서:

1. 구동 순서: [`01-startup-sequence.md`](./01-startup-sequence.md)
2. Kafka command 경로: [`06-kafka-tc-eqp-commands-to-equipment-lifecycle.md`](./06-kafka-tc-eqp-commands-to-equipment-lifecycle.md)

주의:

1. 이 문서의 `connectionMode` ACTIVE/PASSIVE 해석은 gateway 기준입니다.
2. 상세 생명주기 문서(`04`, `05`)도 gateway 기준 파일명/내용으로 정리되어 있습니다.

## 1. 설정 파일 구조를 먼저 이해하기

## 1-1. 앱 기본 설정 파일: `application.yaml`

파일:

- `apps/tc-comm-gateway-app/src/main/resources/application.yaml`

핵심 특징:

1. `spring.main.web-application-type: none`
   - 웹 서버형 애플리케이션이 아님
2. `spring.config.import` 사용
   - 실제 운영 설정이 외부 properties 파일로 분리됨

초급 개발자 포인트:

문제가 생겼을 때 `application.yaml`만 보고 판단하면 안 됩니다. 대부분의 실질 설정은 `config/*.properties`에 있습니다.

## 1-2. 외부 설정 파일들 (`apps/tc-comm-gateway-app/config` + 루트 `config`)

대표 파일:

1. `apps/tc-comm-gateway-app/config/tc-comm.properties`
2. `apps/tc-comm-gateway-app/config/tc-messaging.properties`
3. `apps/tc-comm-gateway-app/config/tc-redis.properties`
4. `config/tc-log.properties`
5. `config/tc-db.properties`

참고:

1. `tc-db.properties`, `tc-log.properties`는 앱 하위 폴더가 아니라 저장소 루트 `config/`에 위치하며, `application.yaml`에서 `file:config/...` 경로로 import됩니다.

대략적인 역할:

1. `tc-comm.properties`
   - 게이트웨이 통신/Netty/라이프사이클/runtime/shard/관측/UI task 정책
2. `tc-messaging.properties`
   - Kafka client 설정, topic 이름
3. `tc-redis.properties`
   - Redis 연결 + DLQ/quarantine TTL
4. `tc-log.properties` (루트 `config/`)
   - 공통 로그 패턴/분리/로테이션/압축 정책
   - 앱별 EQP 로그 파일명 prefix는 각 앱 전용 설정 파일에서 override
5. `tc-db.properties`
   - DB 연결 관련

## 2. 설정 확인 순서 (초급 개발자용)

설정을 처음 점검할 때는 아래 순서가 가장 효율적입니다.

1. `spring.config.import`가 모든 필요한 파일을 실제로 import하는지 확인
2. Kafka topic 이름과 `spring.kafka.*` 연결 정보 확인
3. shard/partition 설정 확인 (`ownedPartitions`, `commandsPartitionCount`)
4. Netty/timeout/queue 크기 설정 확인
5. 장비 프로파일과 mode/port/socketType 정합성 확인

## 3. 프로퍼티 prefix별 역할과 체크포인트

이 섹션은 실제로 많이 쓰는 prefix를 중심으로 설명합니다.

## 3-1. `tc.comm.gateway.runtime` (`GatewayRuntimeProperties`)

역할:

1. 장비 처리 runtime 전반(큐/드레인/재시도/워커) 설정

대표 체크포인트:

1. queue 용량 관련 값이 0 이하가 아닌지
2. drain/retry/worker 관련 값이 유효 범위인지
3. 너무 작은 값으로 인해 과도한 drop/overflow가 발생하지 않는지

운영 관점 팁:

queue가 너무 작으면 burst 트래픽에서 quarantine/DLQ가 늘어날 수 있습니다. 너무 크게만 잡아도 메모리 사용량이 커지므로 장비 수와 메시지 패턴을 기준으로 조정해야 합니다.

## 3-2. `tc.comm.gateway.lifecycle` (`GatewayLifecycleProperties`)

역할:

1. `EquipmentLifecycleStateMachine`의 mailbox/worker/timeout scheduler 설정
2. 기본 timeout 값 제공

대표 체크포인트:

1. mailbox/worker/scheduler 쓰레드 수가 0 이하가 아닌지
2. `defaultTimeoutMs`가 0 이하가 아닌지
3. START/END timeout 정책이 실제 장비 연결 시간과 맞는지

초급 개발자 주의:

timeout을 너무 짧게 잡으면 정상 장비도 `START_TIMEOUT`이 많이 발생할 수 있습니다.

## 3-3. `tc.comm.gateway.kafka` (`GatewayKafkaShardProperties`)

역할:

1. shard 소유 파티션(`ownedPartitions`)
2. commands partition 개수 기대값
3. poll timeout / lag / admin / shutdown timeout 등 Kafka 운영 파라미터

대표 체크포인트:

1. `commandsPartitionCount > 0`
2. `ownedPartitions` 비어 있지 않음
3. `ownedPartitions` 값이 실제 파티션 범위 안에 있음
4. poll timeout, shutdown timeout 값이 0 이하가 아님

매우 중요한 포인트:

이 설정은 `GatewayEqpCommandKafkaSubscriber`의 assign 기반 소비와 직접 연결됩니다. 잘못 설정하면 특정 파티션을 아예 소비하지 못합니다.

## 3-4. `tc.messaging.kafka.topic.*` (`GatewayKafkaTopicProperties`)

역할:

게이트웨이가 사용하는 Kafka 토픽 이름들을 정의합니다.

대표 항목(예시):

1. eqp events/commands
2. ui events/commands
3. mes events/commands

체크포인트:

1. 필수 토픽 이름이 비어 있지 않은지
2. 운영 환경의 실제 Kafka 토픽명과 정확히 일치하는지
3. 오타/환경별 접두사 차이가 없는지

## 3-5. `spring.kafka.*` (`GatewayKafkaClientProperties` 연계)

역할:

1. Kafka 클라이언트 연결/직렬화 설정

중요 체크포인트:

1. `spring.kafka.bootstrap-servers` 설정 여부
2. consumer group/id 관련 값 확인
3. deserializer 설정 확인
4. `spring.kafka.consumer.enable-auto-commit = false` 인지 확인 (중요)
5. `spring.kafka.consumer.properties.*`의 JsonDeserializer 관련 설정 확인

초급 개발자 주의:

`enable-auto-commit=true`로 잘못 설정하면 레코드 처리/실패 정책이 의도와 다르게 동작할 수 있습니다.

## 3-6. `tc.comm.gateway.netty` (`GatewayNettyProperties`)

역할:

1. Netty boss/worker 스레드
2. bind timeout / connect timeout
3. bind executor 설정
4. reconnect delay / scheduler
5. outbound 연속 실패 허용 횟수
6. unbound inbox 용량 관련 설정
7. socket initialize 관련 설정

대표 체크포인트:

1. boss/worker thread 수 > 0
2. bind/connect timeout > 0
3. reconnect delay / scheduler 설정 유효
4. outbound max failures > 0
5. unbound inbox 초기값 <= 최대값

운영 관점 팁:

ACTIVE 장비가 많으면 connect timeout / reconnect delay / max failures 조합이 START 실패 패턴(`OUTBOUND_RETRY_EXHAUSTED`)에 직접 영향을 줍니다.

## 3-7. `tc.comm.gateway.socket` (`GatewaySocketProperties`)

역할:

1. 기본 socketType
2. 최대 프레임 크기
3. 빈 프레임 허용 여부
4. 정규식 종료 패턴 등 SOCKET 파싱 정책

체크포인트:

1. `default-socket-type` 지정 여부
2. `max-frame-bytes` 값이 실제 장비 메시지 크기와 맞는지
3. `regex-end-pattern`가 해당 socketType 사용 시 유효한지

초급 개발자 주의:

SOCKET command 인코딩/디코딩 문제는 실제로 `socketType`/파싱 정책 불일치에서 많이 발생합니다.

## 3-8. `tc.comm.gateway.hsms` (`GatewayHsmsProperties`)

역할:

1. HSMS 타이머(T3/T5/T6/T7/T8)
2. linktest 관련 설정
3. max frame bytes
4. select-before-data 정책
5. device id 등

체크포인트:

1. 타이머 값이 0 이하가 아닌지
2. linktest 설정이 장비/운영 정책과 맞는지
3. `maxFrameBytes`가 실제 메시지 크기와 맞는지
4. `requireSelectBeforeData` 정책이 설비 동작과 맞는지

## 3-9. `tc.comm.gateway.ui-task` (`GatewayUiTaskPolicyProperties`)

역할:

1. UI task별 timeout
2. retry/backoff 정책
3. dedup/mailbox/thread/shutdown 대기 설정

체크포인트:

1. START/END/SEND_MESSAGE timeout 값 > 0
2. retry/backoff 값이 지나치게 크거나 작은지
3. mailbox/thread 수가 처리량에 맞는지

왜 중요한가:

UI START/END 요청은 deferred lifecycle reply 구조이므로, timeout/retry 정책이 사용자 체감 품질에 직접 영향을 줍니다.

## 3-10. `tc.comm.gateway.observability` (`GatewayObservabilityProperties`)

역할:

1. 메트릭/샘플링 로그 주기(`*-log-every`) 설정

체크포인트:

1. 모든 `*-log-every` 값 > 0
2. 너무 작은 값으로 인해 로그가 과도하지 않은지
3. 너무 큰 값으로 인해 장애 징후를 놓치지 않는지

## 3-11. `tc.comm.gateway.publish-policy` (`GatewayPublishPolicyProperties`)

역할:

1. publish 정책 버전/기본 모드
2. 패턴 매칭 규칙 기반 publish 제어

체크포인트:

1. `version`, `default-mode`, `updated-at-epoch-ms` 존재 여부
2. rules의 `match-type`, `pattern`, `publish-mode`가 유효한지

초급 개발자 주의:

publish 정책 오류는 "수신은 되는데 이벤트 발행이 기대와 다름" 문제로 나타날 수 있습니다.

## 3-12. `tc.comm.gateway.redis` (`GatewayRedisProperties`)

역할:

1. DLQ / quarantine TTL 등 Redis 기반 보관 정책

체크포인트:

1. TTL 값이 음수가 아닌지 (`>= 0`)
2. 운영 요구사항에 맞는 보관 기간인지

## 3-13. `tc.comm.gateway.plugin-runtime` (`GatewaySocketPluginRuntimeProperties`)

역할:

1. SOCKET payload 플러그인 런타임 동작 관련 설정

체크포인트:

1. 플러그인 사용 여부/경로/정책이 배포 환경과 맞는지
2. 플러그인 미존재 시 fallback 동작이 의도와 맞는지

## 4. Kafka 토픽/파티션 운영 체크리스트 (필수)

이 섹션은 `GatewayKafkaOperationalInvariantChecker`와 직접 연결되는 내용입니다.

## 4-1. 기동 전 필수 확인

1. 필수 Kafka 토픽이 모두 생성되어 있는가?
2. `tc.eqp.commands` 파티션 수가 `commandsPartitionCount`와 정확히 같은가?
3. 각 게이트웨이 인스턴스의 `ownedPartitions` 합집합이 운영 의도와 맞는가?
4. `ownedPartitions`에 중복/범위 초과 값이 없는가?
5. Kafka broker 접속 정보(`bootstrap-servers`)가 올바른가?

## 4-2. 기동 후 확인

1. invariant checker가 실패 없이 통과했는가?
2. `GatewayEqpCommandKafkaSubscriber`가 기대한 파티션을 assign 받았는가?
3. lag 메트릭/로그가 정상인가?

## 5. 장비 프로파일 + Netty 설정 정합성 체크리스트

ACTIVE/PASSIVE 문제는 설정 정합성 문제로 자주 발생합니다.

## 5-1. gateway 기준 ACTIVE 장비 (outbound/client)

체크포인트:

1. 장비 `connectionMode = ACTIVE`인지
2. 설비 IP/port가 올바른지
3. connect timeout / reconnect delay / max failures 설정이 현실적인지
4. END 시 suppress가 적용되는지

실패 증상 예시:

1. `OUTBOUND_RETRY_EXHAUSTED`
2. START 요청 후 `START_TIMEOUT`

## 5-2. gateway 기준 PASSIVE 장비 (listener/server)

체크포인트:

1. 장비 `connectionMode = PASSIVE`인지
2. interfaceType/port 설정이 올바른지
3. 같은 PASSIVE SOCKET 포트를 공유하는 장비들의 `socketType`가 동일한지
4. 부팅 시 shared listener 제약 위반이 없는지

실패 증상 예시:

1. `PASSIVE_LISTENER_START_FAILED`
2. START 요청 후 `START_TIMEOUT`

## 6. 라이프사이클 timeout 설정 체크리스트

`EquipmentLifecycleStateMachine` timeout은 실제 네트워크/장비 응답 시간보다 너무 짧으면 안 됩니다.

추천 점검 항목:

1. START timeout >= (connect timeout + 재시도 간격 * 예상 재시도 횟수) 관점으로 검토
2. PASSIVE 장비는 설비가 실제 접속할 때까지 걸릴 수 있는 시간을 고려
3. END timeout은 channel close 및 unbind 지연 가능성을 고려

초급 개발자 주의:

timeout은 "작을수록 좋다"가 아닙니다. 너무 짧으면 정상 장비도 `ERROR` 상태로 자주 떨어집니다.

## 7. 큐/메일박스/백프레셔 체크리스트

대표 관련 설정:

1. `tc.comm.gateway.runtime.*`
2. `tc.comm.gateway.ui-task.*`
3. `tc.comm.gateway.netty.*` (unbound inbox 관련)

점검 포인트:

1. 장비 수 대비 mailbox/thread 수가 충분한가?
2. burst 트래픽 대비 inbound/outbound queue 용량이 충분한가?
3. `unboundInbox`가 너무 작아 bind 전 데이터 overflow가 자주 발생하지 않는가?
4. overflow 발생 시 DLQ/quarantine/로그 관찰 체계가 준비되어 있는가?

## 8. 기동 전 체크리스트 (실전용)

배포 전/실행 전 아래 체크리스트를 순서대로 확인하는 것을 권장합니다.

1. `application.yaml`의 `spring.config.import` 경로가 실제 파일과 일치하는가?
2. `tc-messaging.properties`의 Kafka broker/topic 이름이 정확한가?
3. `tc-comm.properties`의 shard/partition/Netty/lifecycle 값이 환경에 맞는가?
4. `ownedPartitions`가 운영 인스턴스 구성과 맞게 분배되었는가?
5. 설비 프로파일의 `connectionMode`, `interfaceType`, IP/port/socketType이 올바른가?
6. PASSIVE SOCKET 포트 공유 장비들의 `socketType` 충돌이 없는가?
7. Redis/DB/로그 설정 파일이 누락되지 않았는가?

## 9. 기동 후 체크리스트 (실전용)

앱이 올라온 뒤 아래 순서로 확인하면 안정적으로 점검할 수 있습니다.

1. `GatewayKafkaOperationalInvariantChecker` 통과 로그 확인
2. `GatewayNettyBootstrap` start 로그 확인 (event loop/reconnect scheduler)
3. enabled 장비 런타임 시작 시도 로그 확인
4. PASSIVE 장비 listener 생성/재사용 로그 확인
5. ACTIVE 장비 outbound connect/reconnect 로그 확인
6. `EquipmentLifecycleStateMachine` START/END outcome 로그 확인
7. Kafka subscriber assign/poll 로그 및 lag 메트릭 확인
8. `GATEWAY_TASK_DISPOSITION` 로그로 command/UI 처리 상태 확인

## 10. 자주 발생하는 설정 실수와 증상

## 10-1. `ownedPartitions` 잘못 설정

증상:

1. 특정 `tc.eqp.commands` 메시지가 처리되지 않음
2. 일부 partition에 대한 consumer 동작 없음

원인:

1. 범위 초과 값
2. 빈 목록
3. 운영 인스턴스 간 분배 실수

## 10-2. `commandsPartitionCount` 불일치

증상:

1. 앱 기동 실패 (fail-fast)

원인:

1. Kafka 실제 토픽 파티션 수와 설정 기대값이 다름

## 10-3. PASSIVE SOCKET 동일 포트 `socketType` 충돌

증상:

1. PASSIVE listener start 실패
2. START 요청 후 `PASSIVE_LISTENER_START_FAILED`

원인:

1. 같은 포트를 공유하는 PASSIVE SOCKET 장비들의 `socketType` 불일치

## 10-4. ACTIVE connect timeout / retry 정책 과도하게 공격적

증상:

1. START 요청 후 빠르게 `OUTBOUND_RETRY_EXHAUSTED`
2. `START_TIMEOUT` 빈번

원인:

1. connect timeout 너무 짧음
2. reconnect delay 너무 짧거나 max failures 너무 작음
3. 실제 네트워크 지연/장비 응답 시간 미반영

## 10-5. `spring.kafka.consumer.enable-auto-commit=true`

증상:

1. 레코드 실패/재시도 동작이 예상과 다름
2. 장애 분석이 어려움

원인:

1. 수동 커밋/실패 처리 정책과 충돌

## 11. 초급 개발자를 위한 "문제 유형별 첫 번째 확인 위치"

1. 앱이 기동 자체가 안 됨
   - `application.yaml` import 경로, 프로퍼티 바인딩 오류, Kafka invariant checker
2. START 요청은 되는데 장비 연결이 안 됨
   - `GatewayNettyBootstrap`, `EquipmentLifecycleStateMachine`, ACTIVE/PASSIVE mode 설정
3. Kafka command는 읽는데 설비로 안 감
   - `GatewayCommandDispatcher` disposition, 채널 활성 여부, interfaceType/HSMS 미구현 여부
4. END 후 다시 연결됨
   - ACTIVE suppress 설정/cleanup, `GatewayNettyBootstrap` reconnect 로그
5. 로그가 너무 많거나 너무 적음
   - `tc.comm.gateway.observability.*`, `tc-log.properties`

## 12. 운영 변경 시 권장 절차 (안전하게)

설정 변경 시 아래 순서를 권장합니다.

1. 토픽/파티션 변경
   - Kafka 실제 리소스 변경 -> 설정(`commandsPartitionCount`, `ownedPartitions`, topic names) 변경 -> 기동 전 검증
2. timeout/retry 변경
   - 현재 failure reason/로그 패턴 확인 -> 점진 조정 -> START/END 테스트
3. ACTIVE/PASSIVE 장비 추가
   - 장비 프로파일 추가 -> mode/interfaceType/port/socketType 검증 -> 충돌 여부 확인 -> 배포

## 13. 최종 체크리스트 (요약 버전)

### 필수 설정

1. `spring.config.import`
2. `spring.kafka.*`
3. `tc.messaging.kafka.topic.*`
4. `tc.comm.gateway.kafka.*`
5. `tc.comm.gateway.netty.*`
6. `tc.comm.gateway.lifecycle.*`

### 필수 운영 점검

1. Kafka topic 존재/파티션 수
2. `ownedPartitions` 정합성
3. PASSIVE SOCKET 포트/`socketType` 충돌 없음
4. ACTIVE IP/port/connect timeout 정책 점검
5. START/END outcome 로그 확인
6. `GATEWAY_TASK_DISPOSITION` 확인

## 14. 다음 확장 문서 후보 (추천)

이 문서 이후 운영 수준을 높이려면 다음 문서를 추가로 만드는 것을 권장합니다.

1. 장애 대응 플레이북 (failure reason별 대응 절차)
2. 로그 키워드/메트릭 대시보드 가이드
3. 통합 테스트 시나리오 및 검증 절차
