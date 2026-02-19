# TC Business Core App 공통화 경계 정의 (Step 2)

## 1. 목적
`tc-comm-gateway-app`과 `tc-business-core-app` 간에 재사용 가능한 영역과 앱 고유 영역을 분리하여,
공통 모듈 추출 시 강결합을 방지합니다.

## 2. 공통 모듈 대상

### 2.1 tc-common-mailbox
- 포함
  - Mailbox 자료구조
  - ReadyQueue
  - Dispatcher/Scheduler
  - `MailboxExecutionRuntime` (dispatcher/worker 실행 루프 공통화)
  - `inFlight=1` 동시성 제어
  - Queue overflow/backpressure 훅
  - 공통 메트릭 포인트
- 제외
  - workflow 매칭
  - 도메인 실패 분류 규칙
  - 장비 제어/비즈니스 액션 실행

### 2.2 tc-common-kafka-consumer-runtime
- 포함
  - Consumer lifecycle
  - Poll 루프
  - Ack 수집
  - Partition 연속 offset commit tracker
  - Commit 재시도/종료 훅
- 제외
  - 토픽별 payload 파싱
  - 앱별 라우팅/업무 처리

### 2.3 tc-common-task-execution
- 포함
  - Retry/backoff
  - Timeout interrupt(3분)
  - DLQ 전이 템플릿
  - 실패 공통 분류 인터페이스
  - `tc.ui.events -> 처리 -> tc.ui.commands REP` 실행 템플릿
  - REP 발행 재시도/실패 처리 훅
- 제외
  - Mailbox 실행 루프(`MailboxExecutionRuntime`) 책임
  - 이벤트 타입별 도메인 처리기
  - 앱별 검증/응답 필드 규칙
  - 앱별 failureCategory 매핑 상세
  - 앱별 DLQ payload 구성 상세

## 3. 앱 고유 영역

### 3.1 tc-comm-gateway-app 고유
- Netty/HSMS/SOCKET 채널 상태 관리
- 채널 전송/연결/재연결 제어
- 게이트웨이 프로토콜 변환

### 3.2 tc-business-core-app 고유
- model/runtime 캐시 구성
- workflow/filter/action 도메인 해석
- plugin JAR 로딩/검증/레지스트리

## 4. 의존성 규칙
- 공통 모듈은 `apps/*` 패키지에 의존 금지
- 공통 모듈은 gateway/business adapter 구현에 의존 금지
- 공통 모듈은 인터페이스(포트) 중심으로만 확장

## 5. DLQ 및 inFlight 규칙
- 공통 모듈에서 `DLQ`와 `inFlight`를 결합하지 않습니다.
- DLQ 이관 후에도 현재 작업은 완료 처리되며 `inFlight`는 해제됩니다.
- 설비 중단 정책은 TC 내부 공통 모듈 책임이 아닙니다(MES 책임).

## 6. 적용 우선순위
1. `tc-common-mailbox`
2. `tc-common-kafka-consumer-runtime`
3. `tc-common-task-execution`
