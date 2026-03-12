# nori-tc 아키텍처 개요

> 본 문서는 `nori-tc`의 **현재 구현 기준 소개서**입니다.
> 경영진이 `nori-tc`를 볼 때 가장 먼저 이해해야 할 것은, 이 시스템이 단순한 UI 서버가 아니라
> **Gateway와 Business Core를 중심으로 설비 연결과 업무 처리를 안정적으로 운영하기 위한 플랫폼**이라는 점입니다.

---

## 1. nori-tc를 한 줄로 말하면

`nori-tc`는 **설비를 안정적으로 연결하고, 설비에서 들어오는 이벤트를 실제 업무 처리로 이어 주는 Gateway + Business 중심 TC 플랫폼**입니다.

쉽게 말해,

- **Gateway**는 설비를 붙이고 끊고 통신하는 현장 실행 계층입니다.
- **Business Core**는 들어온 이벤트를 규칙에 따라 처리하는 두뇌 역할입니다.
- **UI Backend**는 운영자가 요청을 넣고 결과를 확인하는 관리 창구입니다.

즉, 중심축은 UI가 아니라 **설비 연결을 책임지는 Gateway**와 **업무 처리를 책임지는 Business Core**입니다.

---

## 2. 경영진이 먼저 봐야 할 핵심 키워드

| 핵심 키워드 | 의미 | 경영진 관점에서 중요한 이유 |
|---|---|---|
| **안정적 설비 연결 허브** | Gateway가 설비 연결, 해제, 재연결, 통신 흐름을 관리합니다. | 설비가 많아져도 연결과 통신을 한 체계 안에서 운영할 수 있습니다. |
| **순서가 꼬이지 않는 제어 구조** | 같은 설비의 요청은 항상 같은 처리 경로로 흘러가도록 설계되어 있습니다. | 설비 상태 꼬임, 중복 제어, 순서 역전 같은 운영 사고를 줄입니다. |
| **실시간 업무 처리 엔진** | Business Core가 설비 이벤트를 받아 규칙에 따라 바로 업무 처리로 연결합니다. | 설비 메시지가 단순 수집에 그치지 않고 실제 운영/업무 가치로 이어집니다. |
| **장애를 격리하고 추적할 수 있는 구조** | traceId, DLQ, Quarantine, 비동기 결과 저장 구조를 갖고 있습니다. | 문제가 생겼을 때 “어디서 왜 실패했는지”를 설명하고 후속 대응하기 쉽습니다. |
| **변경과 확장에 강한 구조** | 앱 역할 분리, 외부 설정, 플러그인, 포트-어댑터 구조를 사용합니다. | 고객사별 차이, 운영 설정 변경, 기술 확장에 대응하기 쉽습니다. |

---

## 3. 시스템 전체 그림

### 3.1 중심 구조

```text
[운영 화면 / 사용자]
          │
          ▼
[UI Backend]
  요청 접수 / 인증 / 결과 조회
          │
          │ Kafka
          ├──────────────────────► [Gateway]
          │                         설비 연결 / 상태 제어 / 메시지 송수신
          │
          └──────────────────────► [Business Core]
                                    이벤트 해석 / 규칙 매칭 / 업무 처리

[설비] ◄────────────── TCP/HSMS/SOCKET ──────────────► [Gateway]

[DB / Redis / Kafka]
  세 앱이 함께 사용하는 공통 운영 기반
```

### 3.2 각 영역의 역할

| 영역 | 역할 | 비중 |
|---|---|---|
| **Gateway** | 설비와 직접 연결되어 현장 통신과 상태를 책임집니다. | **핵심** |
| **Business Core** | 설비 이벤트를 실제 처리 규칙과 액션으로 연결합니다. | **핵심** |
| UI Backend | 운영 요청을 접수하고 결과를 사용자에게 보여줍니다. | 지원 |
| Kafka / Redis / DB | 시스템 전체를 묶는 공통 운영 기반입니다. | 기반 |

이 구조의 핵심은 UI가 중심이 아니라, **현장 실행은 Gateway**, **업무 실행은 Business Core**가 담당한다는 점입니다.

---

## 4. 현재 구현된 핵심 역량

### 4.1 Gateway: 설비 운영의 실행 중심

Gateway는 `nori-tc`에서 가장 현장에 가까운 계층입니다. 설비와 직접 붙어 실제 통신과 상태를 관리합니다.

현재 구현된 핵심 역량은 다음과 같습니다.

- 설비별 **라이프사이클 상태 관리**
  - START, END, 연결, 해제, 재연결 흐름을 상태머신으로 관리합니다.
- 설비별 **고정 처리 경로**
  - 같은 설비의 요청이 항상 같은 처리 경로로 들어오게 하여 순서를 유지합니다.
- **Netty 기반 TCP 통신**
  - ACTIVE/PASSIVE 연결 모드와 HSMS/SOCKET 프로토콜을 처리합니다.
- **인바운드/아웃바운드 파이프라인**
  - 수신 데이터 검증, 디코딩, 분류, 발행과 송신 시 검증, 인코딩, 전송을 표준화합니다.
- **UI 작업 직접 처리**
  - START, END, SEND_MESSAGE 같은 운영 요청을 Kafka로 받아 처리합니다.
- **중복 방지와 지연 응답**
  - 같은 요청이 두 번 처리되지 않도록 막고, 시간이 걸리는 작업은 완료 시점에 응답합니다.
- **Quarantine 격리**
  - 정상 처리할 수 없는 설비 수신 데이터는 격리 저장해 후속 분석이 가능하게 합니다.

왜 중요한가:
설비 통신은 현장에서 가장 변수가 많은 영역인데, `nori-tc`는 이 불안정성을 Gateway 안에서 흡수하도록 설계되어 있습니다.

대표 근거 문서:
[장비 라이프사이클 상태머신](./gateway/02-equipment-lifecycle-state-machine.md),
[Kafka Partition 기반 고정 라우팅](./gateway/03-kafka-partition-shard-routing.md),
[Netty 기반 장비 TCP 통신](./gateway/04-netty-tcp-communication.md),
[인바운드 파이프라인](./gateway/06-inbound-pipeline.md),
[아웃바운드 파이프라인](./gateway/07-outbound-pipeline.md),
[Quarantine 처리](./gateway/10-quarantine-handling.md)

### 4.2 Business Core: 설비 이벤트를 업무 처리로 바꾸는 두뇌

Business Core는 설비에서 들어온 데이터를 “의미 있는 업무 처리”로 연결하는 실행 엔진입니다.

현재 구현된 핵심 역량은 다음과 같습니다.

- **3단계 처리 구조**
  - 수신 큐, 설비별 작업함, 실행 워커를 분리해 처리량과 안정성을 함께 확보합니다.
- **설비별 순서 보장**
  - 같은 설비의 이벤트는 순차적으로, 다른 설비는 병렬로 처리합니다.
- **워크플로우 매칭**
  - 메시지 이름과 조건 규칙을 기준으로 어떤 처리를 해야 하는지 자동 결정합니다.
- **액션 실행 체계**
  - 매칭된 결과에 따라 필요한 액션을 실행합니다.
- **모델 런타임 캐시**
  - 자주 쓰는 모델, 메시지, 변수 정의를 메모리에 적재해 빠르게 처리합니다.
- **재시도 / 타임아웃 / DLQ 정책**
  - 실패를 한 가지로 보지 않고, 재시도할지, 격리할지, 정상 종료로 볼지를 정책적으로 결정합니다.
- **UI 태스크 처리**
  - UI에서 들어온 비즈니스 요청도 별도 경로로 받아 결과를 다시 돌려줍니다.

왜 중요한가:
설비 이벤트를 받는 것만으로는 사업 가치가 생기지 않습니다. Business Core가 있어야 설비 이벤트가 실제 운영 처리와 자동화로 이어집니다.

대표 근거 문서:
[3단계 큐 구조](./business/02-three-stage-queue-structure.md),
[워크플로우 매칭](./business/03-workflow-matching.md),
[워크플로우 액션 타입](./business/04-workflow-action-types.md),
[모델 런타임 캐시](./business/05-model-runtime-cache.md),
[태스크 재시도/타임아웃 정책](./business/06-task-retry-timeout-policy.md),
[UI 태스크 처리](./business/07-ui-task-handling.md)

### 4.3 UI Backend: 운영을 연결하는 지원 계층

UI Backend는 제품의 중심 엔진이라기보다, 운영자와 내부 런타임을 연결하는 관리 창구입니다.

현재 구현된 핵심 역량은 다음과 같습니다.

- EQP, Model, 사용자/권한, DLQ 조회를 위한 REST API 제공
- 쿠키 기반 인증과 브라우저 보안 처리
- 시간이 짧은 작업과 긴 작업을 구분한 응답 방식
- Gateway와 Business의 응답을 모아 최종 결과를 정리하는 구조
- Kafka를 통한 운영 요청 발행과 결과 조회

왜 중요한가:
운영자는 하나의 관리 창구에서 요청을 넣고 결과를 확인할 수 있어야 하며, UI Backend는 이 접점을 안정적으로 제공하는 역할을 합니다.

대표 근거 문서:
[REST API 구조](./ui/01-rest-api-structure.md),
[쿠키 기반 인증](./ui/02-cookie-based-authentication.md),
[Dual Response 패턴](./ui/04-dual-response-pattern.md),
[비동기 결과 폴링](./ui/05-async-result-polling.md),
[Kafka 이벤트 발행](./ui/06-kafka-event-publishing.md)

---

## 5. 이 구조가 운영에서 주는 의미

### 5.1 설비가 많아져도 운영 질서가 무너지지 않습니다

같은 설비의 요청이 같은 경로로 흐르고, 설비별 순서 보장 구조를 갖고 있기 때문에
설비 수가 늘어나도 제어 순서가 꼬일 가능성을 줄일 수 있습니다.

### 5.2 장애가 나도 바로 원인 추적이 가능합니다

요청 단위 추적 정보, 실패 격리 저장소, 비동기 결과 저장 구조가 있어
“무슨 요청이 어디서 실패했는지”를 설명하기 쉽습니다.

### 5.3 현장 통신과 업무 처리를 분리해 리스크를 낮춥니다

설비 연결 문제는 Gateway에서, 업무 처리 문제는 Business Core에서 흡수하도록 나누어 두었기 때문에
문제 발생 지점을 더 명확히 구분할 수 있습니다.

### 5.4 변경과 확장에 유리합니다

앱을 얇게 유지하고 실제 로직은 라이브러리 모듈로 분리했으며,
외부 설정, 플러그인, 포트-어댑터 구조를 사용해 고객사별 차이와 향후 확장에 대응하기 쉽습니다.

요약하면, `nori-tc`의 가치는 “예쁜 관리 화면”보다 **설비 연결을 안정화하는 Gateway**와
**업무 자동화를 담당하는 Business Core**를 이미 갖추고 있다는 데 있습니다.

---

## 6. 공통 아키텍처 특징

| 공통 특징 | 의미 | 경영진 관점 의미 |
|---|---|---|
| Thin Composition Root | 실행 앱은 가볍게 두고 실제 로직은 공통 모듈에 배치 | 중복 개발을 줄이고 구조 일관성을 유지합니다. |
| Hexagonal Architecture | 비즈니스 로직과 기술 구현을 분리 | 특정 기술 변경이 전체 핵심 로직을 흔들지 않게 합니다. |
| 외부 설정 로딩 | 운영 설정을 코드 밖에서 관리 | 환경 변경 시 재빌드 의존을 줄입니다. |
| Kafka 기반 비동기 연계 | 앱 간 요청/이벤트를 비동기로 연결 | 직접 결합을 줄이고 확장성을 높입니다. |
| Mailbox 기반 직렬 처리 | 같은 설비는 순차, 다른 설비는 병렬 처리 | 순서 보장과 처리량을 동시에 잡습니다. |
| MDC 추적 로깅 | 요청과 설비 단위 로그를 함께 추적 | 장애 분석과 보고가 쉬워집니다. |
| DLQ / Quarantine / Redis 활용 | 실패와 예외 데이터를 분리 저장 | 유실보다 분석과 복구가 가능한 운영 구조를 만듭니다. |
| Plugin Adapter | 설비별 차이를 플러그인으로 확장 | 고객사별 특수 요구를 흡수하기 쉽습니다. |

관련 문서:
[얇은 조립 진입점](./common/01-thin-composition-root.md),
[Hexagonal 아키텍처](./common/02-hexagonal-architecture.md),
[Kafka 메시지 처리 패턴](./common/04-kafka-messaging-pattern.md),
[MDC 기반 추적 로깅](./common/05-mdc-trace-logging.md),
[DLQ 처리](./common/06-dlq-handling.md),
[Redis 연동](./common/07-redis-integration.md),
[Mailbox 기반 설비 단위 직렬 처리](./common/09-mailbox-sequential-processing.md),
[플러그인 어댑터](./common/10-plugin-adapter.md)

---

## 7. 상세 문서 안내

### Gateway를 더 자세히 보고 싶다면

- [앱 기동 및 초기화](./gateway/01-startup-initialization.md)
- [장비 라이프사이클 상태머신](./gateway/02-equipment-lifecycle-state-machine.md)
- [Kafka Partition 기반 고정 라우팅](./gateway/03-kafka-partition-shard-routing.md)
- [Netty 기반 장비 TCP 통신](./gateway/04-netty-tcp-communication.md)
- [인바운드 파이프라인](./gateway/06-inbound-pipeline.md)
- [아웃바운드 파이프라인](./gateway/07-outbound-pipeline.md)

### Business Core를 더 자세히 보고 싶다면

- [앱 기동 및 초기화](./business/01-startup-initialization.md)
- [3단계 큐 구조](./business/02-three-stage-queue-structure.md)
- [워크플로우 매칭](./business/03-workflow-matching.md)
- [워크플로우 액션 타입](./business/04-workflow-action-types.md)
- [모델 런타임 캐시](./business/05-model-runtime-cache.md)
- [태스크 재시도/타임아웃 정책](./business/06-task-retry-timeout-policy.md)

### 운영/API 관점에서 보고 싶다면

- [REST API 구조](./ui/01-rest-api-structure.md)
- [Dual Response 패턴](./ui/04-dual-response-pattern.md)
- [비동기 결과 폴링](./ui/05-async-result-polling.md)
- [Kafka 이벤트 발행](./ui/06-kafka-event-publishing.md)

### 공통 구조를 먼저 보고 싶다면

- [얇은 조립 진입점](./common/01-thin-composition-root.md)
- [Hexagonal 아키텍처](./common/02-hexagonal-architecture.md)
- [Kafka 메시지 처리 패턴](./common/04-kafka-messaging-pattern.md)
- [Mailbox 기반 설비 단위 직렬 처리](./common/09-mailbox-sequential-processing.md)

---

## 8. 문서 경계

본 문서는 **현재 구현 기준 소개서**입니다.
향후 확장 방향이나 제안 성격의 future roadmap은 [next-nori-tc-architecture.md](./next-nori-tc-architecture.md)에서 별도로 설명합니다.
