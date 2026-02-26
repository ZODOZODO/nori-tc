# Gateway 고정 Partition 라우팅 규칙 (U0 설계 기준 고정)

이 문서는 Gateway 확장/운영 안정성을 위해 합의된 **고정 Partition 기반 라우팅 설계 규칙**을 U0 단계에서 고정하기 위한 SSOT(설계 기준 문서)입니다.

본 문서의 목적은 이후 `U1 ~ U17` 구현 단계에서 구현자가 추가 의사결정 없이 작업을 진행할 수 있도록 설계/운영/품질 기준을 명확히 정의하는 것입니다.

## 1. 문서 목적과 범위

### 1.1 목적

기존의 `eqpId -> hash(partition)` 기반 Gateway ownership 모델은 다음과 같은 운영 리스크가 있습니다.

- PASSIVE 공유 listener(`bindIp + port + socketType`)와 `eqpId` 단위 ownership 기준 충돌
- Gateway 증설 시 파티션/설비/운영 정책이 서로 섞여 복잡도 증가
- UI/Gateway/Business 라우팅 규칙 혼재로 인한 장애 분석 난이도 증가

이에 따라, Gateway 대상 메시지 라우팅 기준을 **DB 고정값(`tc_eqp.route_partition`)** 으로 전환하고, Kafka 파티션은 명시 partition 발행 규칙으로 사용합니다.

### 1.2 1차 범위 (In Scope)

- `tc.eqp.commands` Gateway 대상 명령 라우팅 규칙
- `tc.ui.events.gateway` Gateway 대상 UI 이벤트 라우팅 규칙
- `tc.ui.events.business` Business 대상 UI 이벤트 분리 규칙
- `tc_eqp.comm_mode` / `tc_eqp.route_partition` 중심 DB 정규화 방향
- PASSIVE listener-group 동일 `route_partition` 강제 규칙
- 무중단 Gateway 증설 시 파티션 증설 운영 원칙
- 구현 품질 규칙(UTF-8, 한글 주석, 로그 레벨 가이드)

### 1.3 1차 비범위 (Out of Scope)

- UI-backend 실제 구현
- 기존 생산 설비의 live shard migration (무중단 이동)
- fan-out 소비 방식
- `targetGatewayShardId` 기반 라우팅
- Gateway 간 직접 RPC/forward 토픽 설계

## 2. 용어 정의 (공통 용어 고정)

### 2.1 `route_partition`

- `tc_eqp`에 저장되는 **Gateway 대상 메시지 라우팅 기준 파티션 번호**
- Gateway 대상 토픽(`tc.eqp.commands`, `tc.ui.events.gateway`) 발행 시 사용되는 SSOT
- Kafka `ProducerRecord.partition`에 명시적으로 지정할 값

### 2.2 Gateway 대상 UI 토픽

- Gateway가 직접 처리해야 하는 UI 이벤트를 발행하는 토픽
- 1차 표준 토픽명: `tc.ui.events.gateway`

### 2.3 Business 대상 UI 토픽

- Business가 처리해야 하는 UI 이벤트를 발행하는 토픽
- 1차 표준 토픽명: `tc.ui.events.business`

### 2.4 listener-group

- PASSIVE 설비에서 listener 리소스를 공유하는 그룹
- 그룹 식별 기준(1차 설계 기준):
  - `interfaceType + bindIp + port + socketType`

### 2.5 고정 Partition 라우팅

- Kafka key hash 자동 분배에 의존하지 않고, DB의 `route_partition` 값을 명시 지정하여 발행하는 방식

### 2.6 무중단 증설

- 기존 생산 설비의 `route_partition`/처리 흐름을 변경하지 않고,
- 토픽 파티션 증설 및 신규 Gateway 추가 후 신규 설비만 신규 파티션으로 배정하여 수용하는 운영 방식

## 3. 핵심 설계 결정사항 (Decision Table)

| 항목 | 결정 | 상태 | 비고 |
|---|---|---|---|
| Gateway 대상 라우팅 기준 | `tc_eqp.route_partition` | 채택 | Gateway 대상 라우팅 SSOT |
| `targetGatewayShardId` 사용 | 1차 미사용 | 비채택(1차) | 필요 시 진단용 재검토 가능 |
| fan-out 소비 | 미사용 | 비채택(1차) | CPU/mem/deserialize 비용 증가 방지 |
| Gateway command 소비 | `ASSIGN + ownedPartitions` 유지 | 채택 | 현재 구조 활용 |
| Gateway UI 소비 | `ASSIGN + ownedPartitions` 전환 | 채택 | `SUBSCRIBE` 구조 제거 방향 |
| `tc.eqp.commands` 발행 | 명시 partition 발행 | 채택 | `route_partition` 사용 |
| `tc.ui.events.gateway` 발행 | 명시 partition 발행 | 채택 | `route_partition` 사용 |
| `tc.ui.events.business` 발행 | 일반 consumer-group 처리 | 채택 | 고정 partition 강제 없음 |
| Kafka key | `eqpId` 유지 | 채택 | 추적/정합성 용도 유지 |
| 신규 Gateway 추가 | 토픽 파티션 증설 + 신규 설비만 신규 파티션 배정 | 채택 | 기존 설비 무영향 원칙 |
| PASSIVE listener-group partition 규칙 | 동일 group = 동일 `route_partition` | 채택 | 운영/등록 검증 필요 |
| `1 partition = 1 gateway owner` | 운영 정책으로 유지 | 채택 | 중복 소유 금지 |

## 4. DB 정규화 결정사항 (`comm_mode` 통합 + `route_partition`)

### 4.1 `tc_eqp` 중심 통합 방향

1차 설계에서 `tc_eqp`를 다음 정보의 단일 기준 테이블로 확장합니다.

- `comm_mode` (ACTIVE / PASSIVE)
- `route_partition` (Gateway 대상 라우팅 SSOT)

### 4.2 하위 테이블(`tc_eqp_hsms`, `tc_eqp_socket`) 정리 방향

- `tc_eqp_hsms.connection_mode`는 최종적으로 제거 대상
- `tc_eqp_socket.connection_mode`는 최종적으로 제거 대상

### 4.3 마이그레이션 기본 순서 (원칙 선언)

실제 구현/운영 절차 상세는 후속 단계(U2/U17)에서 작성하되, 기본 순서는 아래 순서를 고정합니다.

1. **DB 확장**
   - `tc_eqp.comm_mode` 추가
   - `tc_eqp.route_partition` 추가
2. **백필(Backfill)**
   - `tc_eqp_hsms/socket.connection_mode` 값을 `tc_eqp.comm_mode`로 이관
3. **코드 전환**
   - 런타임/저장소가 `tc_eqp.comm_mode`, `tc_eqp.route_partition`를 사용하도록 전환
4. **DB 정리**
   - `tc_eqp_hsms/socket.connection_mode` 제거

### 4.4 호환성 유지 규칙 (1차)

`EquipmentContextProfile.HsmsSettings.connectionMode`, `EquipmentContextProfile.SocketSettings.connectionMode` 필드는 1차 구현에서 유지합니다.

- 값의 출처만 `tc_eqp.comm_mode`로 전환
- 하위 소비자 영향 최소화 목적

## 5. 토픽/라우팅 정책

### 5.1 Gateway 대상 토픽 (고정 Partition 명시 발행)

다음 토픽은 `route_partition`를 기준으로 **명시 partition 발행**합니다.

- `tc.eqp.commands`
- `tc.ui.events.gateway`

발행 원칙:

- Kafka key는 `eqpId` 유지
- 실제 라우팅 우선순위는 `ProducerRecord.partition`
- `record.partition`과 `tc_eqp.route_partition` 불일치는 오류로 간주 (수신측 정합성 검증 대상)

### 5.2 Business 대상 UI 토픽 (일반 처리)

- 토픽: `tc.ui.events.business`
- 고정 partition 강제 없음
- Business consumer-group 전략에 따라 일반 처리

### 5.3 Gateway 소비 원칙

- Gateway는 `ASSIGN + ownedPartitions` 기반 소비를 사용
- Gateway는 자신이 소유한 파티션만 처리
- fan-out 소비를 사용하지 않음

## 6. PASSIVE / ACTIVE 라우팅 규칙

### 6.1 PASSIVE 설비 규칙

PASSIVE 설비는 listener 리소스 공유 이슈가 있으므로, 등록/수정 시 다음 규칙을 만족해야 합니다.

- 동일 listener-group
  - (`interfaceType + bindIp + port + socketType`)
- 에 속하는 모든 설비는 **동일 `route_partition`** 이어야 함

이 규칙을 위반하면 등록/수정 요청을 차단합니다. (실제 구현은 U14)

### 6.2 ACTIVE 설비 규칙

- ACTIVE 설비는 `eqpId` 단위로 `route_partition`를 사용
- 해당 `route_partition`를 소유한 Gateway만 connect/start 및 명령 처리 수행

### 6.3 공통 규칙

- `route_partition`는 Gateway 대상 메시지의 단일 라우팅 기준(SSOT)
- `targetGatewayShardId`는 1차 설계에서 사용하지 않음

## 7. 무중단 증설 운영 정책 (Partition 증설 기반)

### 7.1 기본 원칙

신규 Gateway 추가 시 기존 생산 설비에 영향을 주지 않기 위해 아래 원칙을 사용합니다.

- 기존 설비의 `route_partition`는 변경하지 않음
- 기존 Gateway의 기존 partition ownership 유지
- 신규 Gateway는 신규 파티션만 담당
- 신규 설비만 신규 파티션에 배정

### 7.2 예시 시나리오 (개념 예시)

초기 상태:

- `tc.eqp.commands`: 6 partitions (`p0~p5`)
- `tc.ui.events.gateway`: 6 partitions (`p0~p5`)
- Gateway 3대 운영

증설 시:

1. 토픽 파티션 증설 (`6 -> 12`)
2. 신규 Gateway 추가
3. 신규 Gateway에 `p6~p11` 할당
4. 신규 설비 생성 시 `route_partition`를 `p6~p11`로 배정
5. 기존 설비는 기존 `route_partition(0~5)` 유지

### 7.3 비범위 정책 (1차)

다음 항목은 1차 무중단 증설 범위에서 제외합니다.

- 기존 생산 설비의 live rebalancing / live migration
- 기존 설비의 온라인 `route_partition` 재배치

필요 시 정지/계획 작업으로만 수행하거나 2차 설계에서 별도 정의합니다.

## 8. 구현 품질 규칙 (U1 이후 공통 적용)

이 섹션은 이후 구현 단계에서 반드시 지켜야 하는 공통 품질 규칙입니다.

### 8.1 인코딩/한글 규칙 (필수)

1. 모든 코드/주석/문서는 `UTF-8`로 작성합니다. (레포 `.editorconfig` 기준)
2. 새 파일은 `UTF-8 + LF`를 기본으로 합니다.
3. 한글이 깨져 보이면 파일 자체를 재작성하지 말고, 먼저 콘솔/에디터 인코딩을 점검합니다.
4. PowerShell에서 파일 확인 시 UTF-8 읽기 기준을 사용합니다.
   - 예: `Get-Content -Encoding UTF8 <파일경로>`
5. 콘솔 출력 확인이 필요한 경우 UTF-8 출력 인코딩을 명시적으로 설정합니다.

참고:

- 레포 `.editorconfig`에 주요 소스/문서 파일의 `charset = utf-8`가 이미 선언되어 있습니다.
- 콘솔 출력 깨짐은 파일 인코딩 문제와 분리해서 판단해야 합니다.

### 8.2 주석 작성 규칙 (한글 위주, 상세 작성)

#### 8.2.1 클래스 주석 (필수)

각 클래스에는 아래 내용을 포함한 상세 주석을 작성합니다.

- 클래스 역할/책임
- 외부와의 입력/출력 관계
- 상태 보유 여부 및 동시성 주의점(해당 시)
- 변경 시 주의사항 (무엇을 함께 봐야 하는지)

#### 8.2.2 메서드 주석 (필수)

각 메서드에는 아래 내용을 포함한 상세 주석을 작성합니다.

- 메서드 목적
- 호출 시점/호출 주체
- 주요 파라미터 의미
- 반환값 의미
- 예외/실패 시 동작
- 부작용 (DB 저장, Kafka 발행, 상태 변경, 외부 호출 등)

#### 8.2.3 메서드 내부 주석 (필수)

메서드 내부의 주요 처리 블록마다 아래 관점으로 주석을 작성합니다.

- 검증 단계
- 라우팅 판단 단계
- 외부 연동 호출 단계
- 예외 처리/폴백 단계
- "무엇을 하는지"보다 "왜 이렇게 하는지"를 우선 설명

#### 8.2.4 언어 규칙

- 주석은 **한글 위주**로 작성합니다.
- 타입명/클래스명/메서드명/토픽명/테이블명 등 기술 식별자는 원문 그대로 유지합니다.

### 8.3 로그 작성 규칙 (info/debug 중심)

#### 8.3.1 `info` 로그

운영자가 추적해야 하는 주요 상태 변화/요약 이벤트에 사용합니다.

- 애플리케이션 기동/종료
- 파티션 할당 결과
- 주요 설정 로딩 완료
- CREATE/UPDATE/DELETE/START/END 성공
- 운영 정책 변화(예: ownership/할당 변경 결과)

#### 8.3.2 `debug` 로그

개발/장애 분석용 상세 흐름 추적에 사용합니다.

- 메시지 라우팅 상세 판단 (`eqpId`, `route_partition`, `record.partition`)
- skip 사유 (비대상 partition, disabled, 미로드 상태 등)
- 조건 분기 및 필터 결과
- 반복적으로 발생 가능한 상세 흐름

#### 8.3.3 `warn` 로그

비정상 데이터/설정/정합성 문제이지만 즉시 치명 실패는 아닌 상황에 사용합니다.

- `record.partition != route_partition`
- 설정 이상/운영 데이터 불일치
- 재시도 가능한 실패

#### 8.3.4 `error` 로그

운영 개입 또는 즉시 조치가 필요한 실패에 사용합니다.

- 복구 불가 예외
- 메시지 처리 실패
- 외부 연동 실패로 인한 요청 처리 실패

#### 8.3.5 로그 메시지 작성 원칙

- 한글 위주 문장으로 작성
- 핵심 식별자 포함: `eqpId`, `topic`, `partition`, `traceId` 등
- 동일 사건에 대한 중복 로그 남발 금지
- 운영자/개발자 모두 읽기 쉬운 문장형 메시지 사용

## 9. U0 부속 산출물 범위 정의 (실제 작성은 후속 단계)

### 9.1 DB 문서(`docs/db_table/tc-eqp.md`) 변경 예정 항목 (U2 이후 반영)

다음 항목은 U2 이후 실제 DB 스키마 설계/구현 단계에서 반영합니다.

- `tc_eqp.comm_mode` 컬럼 추가
- `tc_eqp.route_partition` 컬럼 추가
- 관련 인덱스/체크 제약
- `tc_eqp_hsms.connection_mode` 제거 계획 반영
- `tc_eqp_socket.connection_mode` 제거 계획 반영

### 9.2 운영 Runbook 문서 경로 후보 (U17 작성 예정)

- 권장 경로: `docs/architecture/gateway-partition-expansion-runbook.md`
- 내용 범위:
  - `6 -> 12` 파티션 증설
  - 신규 Gateway partition 할당
  - 신규 설비 `route_partition` 배정 절차
  - 롤백 포인트

## 10. U0 리뷰 체크리스트 (U1 착수 전 확인)

아래 항목이 모두 명확하면 U1을 착수할 수 있습니다.

- [ ] Gateway 대상 UI 토픽명과 Business 대상 UI 토픽명이 확정되었는가?
- [ ] `tc_eqp.route_partition`가 Gateway 대상 라우팅 SSOT로 명확히 정의되었는가?
- [ ] `connection_mode`를 `tc_eqp.comm_mode`로 통합하는 방향이 확정되었는가?
- [ ] `tc.eqp.commands`, `tc.ui.events.gateway`가 명시 partition 발행 대상으로 확정되었는가?
- [ ] PASSIVE listener-group 동일 `route_partition` 규칙이 명확히 정의되었는가?
- [ ] fan-out / `targetGatewayShardId`가 1차 비범위로 명확히 선언되었는가?
- [ ] 무중단 증설 원칙(기존 설비 불변, 신규 설비만 신규 파티션 배정)이 명확한가?
- [ ] UTF-8/한글 주석/로그 규칙이 구현/코드리뷰 기준으로 사용 가능하게 정의되었는가?

## 11. U0 완료 기준 (수용 기준)

1. 구현자가 이 문서만 읽고 `U1` 구현 범위를 오해 없이 설명할 수 있어야 합니다.
2. 이후 단계에서 반복적으로 발생할 수 있는 설계 질문(토픽명, 라우팅 기준, 비범위, 품질 기준)이 본 문서에서 해결되어야 합니다.
3. U1 이후 구현 단계의 코드 리뷰 기준(주석/로그/UTF-8)이 본 문서에 명시되어 있어야 합니다.

## 12. 명시적 가정 및 기본값

1. 문서/소스 파일 인코딩 기본값은 `UTF-8 (LF)`이며 레포 `.editorconfig`를 기준으로 합니다.
2. 콘솔 한글 깨짐은 파일 인코딩 문제와 분리해서 판단합니다.
3. `1 partition = 1 gateway owner`는 1차 운영 정책으로 유지합니다.
4. `targetGatewayShardId`는 1차 구현에서 사용하지 않습니다.
5. fan-out 소비는 1차 구현에서 사용하지 않습니다.
6. U0 단계에서는 코드/설정/DDL 변경을 수행하지 않습니다.
