# 01. Workflow Filter / Action Data Index 반영 작업 계획

## 참조 문서

- 설계: `docs/design/01-workflow-filter-and-action-data-index-redesign.md`
- 현행 app 표준: `docs/Architecture/01-mdf-action-data-index-standard.md`
- 루트 아키텍처 문서: `../../../docs/Architecture/business/03-workflow-matching.md`
- 루트 액션 문서: `../../../docs/Architecture/business/04-workflow-action-types.md`

---

## 진행 원칙

- 본 문서는 `workflow_filter`와 `action_data_index` 재설계를 실제 구현과 문서 반영으로 옮기기 위한 작업 계획입니다.
- 실제 구현 순서는 `계약 모델 정리 → runtime 반영 → action/MDF 반영 → UI preview/validation 반영 → 문서 동기화 → 테스트` 순서를 유지합니다.
- DB 스키마는 변경하지 않습니다.
- 구현 완료 전까지는 체크박스를 완료 처리하지 않습니다.
- 문서와 코드가 어긋날 가능성이 높은 작업이므로, 각 단계마다 문서-코드 정합성 점검 항목을 포함합니다.
- `T1`~`T5`에서 계약, 런타임, 액션/MDF, preview, validation, 표준 용어가 변경되면 `apps/tc-business-core-app/docs`뿐 아니라 `nori-tc/docs` 하위 관련 문서도 반드시 함께 갱신합니다.
- `nori-tc/docs` 문서 갱신은 `T5`에만 한정된 후행 작업이 아니라, `T1`~`T5` 각 단계의 산출물이 바뀔 때마다 동기화 여부를 즉시 확인하는 상시 작업으로 간주합니다.

---

## 작업 범위

| 작업 ID | 작업 항목 | 주요 대상 |
|---|---|---|
| T1 | 계약 모델/파서 리팩터링 | business-core filter/action parser |
| T2 | `workflow_filter` 런타임 반영 | evaluator, payload 해석, 예외 처리 |
| T3 | `action_data_index` / MDF 반영 | action resolver, MDF composer |
| T4 | UI preview / 저장 검증 반영 | ui-web-adapter preview, controller/save path |
| T5 | app/docs 및 root docs 문서 반영 | tc-business-core-app/docs, root docs |
| T6 | 테스트 및 acceptance 검증 | business-core test, ui adapter test, 문서 정합성 |

---

## 공통 문서 동기화 원칙

### 목적

`workflow_filter`와 `action_data_index` 재설계는 계약, 런타임, preview, MDF 조립, 저장 검증, 표준 용어를 함께 바꾸는 작업이므로 구현 변경과 문서 변경을 분리해서 처리하면 쉽게 어긋납니다.
따라서 `T1`~`T5`에서 변경이 발생하면 항상 `nori-tc/docs` 하위 관련 문서까지 같은 턴에 동기화하는 것을 기본 원칙으로 둡니다.

### 적용 범위

- `apps/tc-business-core-app/docs/**`
- `nori-tc/docs/**`
- 그 외 `workflow_filter`, `action_data_index`, `workflow matching`, `workflow action`, `MDF` 표준을 직접 설명하는 문서

### 공통 체크 항목

- [ ] `T1`~`T5` 구현 변경으로 공개 계약 키/용어가 바뀌면 `nori-tc/docs` 하위 관련 문서를 즉시 갱신했는지 확인
- [ ] `T1`~`T5` 구현 변경으로 런타임 동작/실패 정책이 바뀌면 아키텍처 문서와 운영 문서 설명을 함께 갱신했는지 확인
- [ ] `T1`~`T5` 구현 변경으로 preview/validation/MDF 선택 규칙이 바뀌면 루트 문서 예시와 설명도 함께 갱신했는지 확인
- [ ] app 문서와 root 문서가 동일한 canonical 용어(`and`, `or`, `from=data|metadata`, `comparison`, `expected`, `transforms`, `mdfTemplateName`)를 사용하는지 확인
- [ ] 구현 완료 보고 시 코드 변경과 함께 어떤 `nori-tc/docs` 문서를 갱신했는지 명시적으로 정리했는지 확인

---

## T1. 계약 모델/파서 리팩터링

### 목적

예전 계약(`rows/left/op/right`, `MSG/CTX/AUTO`, `var/source/xform`)을 제거하고, 새 canonical 계약(`and`, `or`, `from`, `data`, `metadata`, `path`, `comparison`, `expected`, `transforms`, `mdfTemplateName`, `fields`) 기준으로 파서와 내부 모델을 재정의합니다.

### 작업 내용

#### T1-1. `workflow_filter` 계약 모델 정리

- [x] `workflow_filter` 새 JSON AST 구조 정의
- [x] 그룹 노드 `and`, `or` 재귀 구조 정의
- [x] 조건 노드 `from`, `path`, `comparison`, `expected`, `transforms` 구조 정의
- [x] `workflow_filter` 예전 계약 제거 여부 확인
- [x] `from=all` 제거 확인
- [x] `path` 상대 경로 강제 검증 규칙 정의
- [x] `metadata`를 Kafka payload 최상위 `metadata`로 해석하도록 고정
- [x] Kafka payload 밖 런타임 메타값 접근 차단 규칙 정의

#### T1-2. `action_data_index` 계약 모델 정리

- [x] 루트 키를 `mdfTemplateName`, `fields`로 고정
- [x] `action_data_index`의 `messageName` → `mdfTemplateName` 변경 반영
- [x] 필드 정의를 `from`, `path`, `transforms` 전용 구조로 정리
- [x] 문자열 shorthand의 기본값을 `from=data`로 정의
- [x] `comparison`, `expected`, `fixed`, `required` 제거 확인

#### T1-3. 공통 validation 규칙 정리

- [x] `workflow_filter` 구조 검증 규칙 정의
- [x] `action_data_index` 구조 검증 규칙 정의
- [x] 절대 경로(`data.status`, `metadata.eventType`) 금지 규칙 정의
- [x] invalid `from` 값 처리 규칙 정의
- [x] transform 체인 파싱 실패 정책 정리

### T1 검증

- [x] 새 계약 키만으로 `workflow_filter` / `action_data_index`를 표현할 수 있는지 확인
- [x] 예전 계약 키가 새 parser에 남아 있지 않은지 확인
- [x] `data` / `metadata` 기준 상대 경로만 허용되는지 확인

---

## T2. `workflow_filter` 런타임 반영

### 목적

기존 flat row evaluator를 새 `and` / `or` 재귀 평가 방식으로 교체하고, Kafka payload envelope의 `data` / `metadata` 블록 기준으로 값을 조회하도록 런타임 동작을 정렬합니다.

### 작업 내용

#### T2-1. evaluator 재귀 평가 구현

- [x] `BusinessWorkflowFilterEvaluator`를 flat row 순회에서 AST 재귀 평가로 전환
- [x] `and` 그룹 평가 구현
- [x] `or` 그룹 평가 구현
- [x] 조건 노드 평가 구현

#### T2-2. payload envelope 해석 구현

- [x] payload 루트에서 `data` 블록 조회 구현
- [x] payload 루트에서 `metadata` 블록 조회 구현
- [x] `from=data`, `from=metadata` 기준 상대 경로 조회 구현
- [x] `path=data.status`, `path=metadata.eventType` 입력 시 validation failure 처리

#### T2-3. 예외 / 실패 정책 정비

- [x] JSON 파싱 실패 시 기존 filter evaluation 예외 흐름 유지
- [x] 구조 검증 실패 시 filter evaluation 예외 분류 유지
- [x] 값 누락 시 비교 결과 false 처리 규칙 확인
- [x] transform 실패 시 이전 값 유지 + warn 로그 확인

### T2 검증

- [x] `workflow_filter`가 `and` / `or` 중첩식을 정상 평가하는지 확인
- [x] `from=data`, `from=metadata`가 Kafka payload 블록 기준으로 동작하는지 확인
- [x] payload 밖 런타임 값이 더 이상 조회되지 않는지 확인

---

## T3. `action_data_index` / MDF 반영

### 목적

`action_data_index`를 값 조회 전용 구조로 단순화하고, MDF 템플릿 선택을 `mdfTemplateName` 명시 선택 방식으로 고정합니다.

### 작업 내용

#### T3-1. action_data_index 해석기 전환

- [ ] `BusinessActionDataIndexHybridResolver`를 새 계약 기준으로 전환
- [ ] `mdfTemplateName` 루트 키 처리 추가
- [ ] `fields` 하위 `from`, `path`, `transforms` 처리 추가
- [ ] 문자열 shorthand를 `from=data` 기본값으로 처리
- [ ] 값 누락 시 빈 문자열 반환 정책 반영

#### T3-2. MDF 템플릿 선택 정책 전환

- [ ] `BusinessMdfMessageComposer`에서 `actionName + target` 자동 템플릿 선택 제거
- [ ] `mdfTemplateName` explicit 선택만 허용
- [ ] `mdfTemplateName` 누락 시 실패 처리
- [ ] 템플릿 미존재 / target mismatch 실패 처리
- [ ] `action_data_index` 비어 있을 때 raw message fallback 유지

#### T3-3. MDF field fallback 충돌 점검

- [ ] MDF `<field>` 정의와 새 `action_data_index.fields` 우선순위 재확인
- [ ] 새 계약에서 `fixed` / `required` 제거에 따른 fallback 영향 점검
- [ ] `metadata` 블록 값을 MDF 필드에 매핑하는 경로 확인

### T3 검증

- [ ] `mdfTemplateName` 기반으로만 MDF 템플릿이 선택되는지 확인
- [ ] 자동 템플릿 선택 코드가 제거되었는지 확인
- [ ] `action_data_index`가 비교 없는 값 조회 전용으로 동작하는지 확인

---

## T4. UI preview / 저장 검증 반영

### 목적

새 계약이 모델 상세 화면 preview와 저장 경로에서도 동일하게 보이도록 맞춥니다.

### 작업 내용

#### T4-1. workflow preview 교체

- [ ] `ModelDetailPreviewSupport`의 workflow preview를 새 계약 기준으로 재작성
- [ ] 첫 번째 조건만 요약하는 방식에서 전체 식 요약 방식으로 전환
- [ ] 새 preview 문구가 `data` / `metadata` 용어를 쓰는지 확인
- [ ] `and`, `or`, `comparison`, `expected`, `transforms`가 드러나는 preview 규칙 정의

#### T4-2. action_data_index preview 교체

- [ ] `mdfTemplateName` 기반 preview 문자열로 변경
- [ ] 첫 필드 요약이 `from`, `path`, `transforms` 기준으로 출력되는지 확인
- [ ] 예전 `messageName`, `MSG`, `CTX`, `AUTO`, `var`, `source`, `xform` 표기가 제거되는지 확인

#### T4-3. 저장 API validation 반영

- [ ] 모델 상세 저장 경로에서 `workflow_filter` 구조 검증 추가
- [ ] 모델 상세 저장 경로에서 `action_data_index` 구조 검증 추가
- [ ] validation 실패 시 400 응답과 원인 메시지 정리

### T4 검증

- [ ] workflow preview가 새 계약 용어로 출력되는지 확인
- [ ] action data index preview가 새 계약 용어로 출력되는지 확인
- [ ] 잘못된 JSON 저장 시 400이 반환되는지 확인

---

## T5. app/docs 및 root docs 문서 반영

### 목적

새 계약을 공식 문서와 app 문서 진입점에 반영해 문서-코드 기준을 통일합니다.

### 작업 내용

#### T5-1. app docs 신규 작성

- [ ] `apps/tc-business-core-app/docs/design/01-workflow-filter-and-action-data-index-redesign.md` 작성
- [ ] `apps/tc-business-core-app/docs/tasks/01-workflow-filter-and-action-data-index-build-plan.md` 작성

#### T5-2. 기존 app 문서 재작성

- [ ] `apps/tc-business-core-app/docs/Architecture/01-mdf-action-data-index-standard.md` 전면 재작성
- [ ] `apps/tc-business-core-app/docs/README.md` 링크/설명 업데이트
- [ ] app README가 새 design/tasks 문서를 가리키는지 확인

#### T5-3. root docs 갱신

- [ ] `docs/Architecture/business/03-workflow-matching.md` 갱신 체크
- [ ] `docs/Architecture/business/04-workflow-action-types.md` 갱신 체크
- [ ] root docs가 `data`, `metadata`, `mdfTemplateName` 기준으로 설명하는지 확인

### T5 검증

- [ ] app docs와 root docs에서 같은 canonical 용어를 사용하는지 확인
- [ ] 예전 용어가 표준 문서 본문에서 제거되었는지 확인
- [ ] design/tasks/architecture/readme 간 상호 링크가 맞는지 확인

---

## T6. 테스트 및 acceptance 검증

### 목적

새 계약이 runtime, action/MDF, UI preview, 저장 검증, 문서까지 일관되게 반영되었는지 확인합니다.

### 작업 내용

#### T6-1. business-core 단위 테스트

- [ ] `workflow_filter` 단일 조건 성공/실패 테스트
- [ ] `and` 그룹 테스트
- [ ] `or` 그룹 테스트
- [ ] `and` 안의 `or` 중첩 테스트
- [ ] 사용자 예시 복합식 테스트
- [ ] `from=data`, `from=metadata` 테스트
- [ ] 절대 경로 금지 validation 테스트
- [ ] transform 실패 fallback 테스트

#### T6-2. action/MDF 테스트

- [ ] `action_data_index`의 `mdfTemplateName` 파싱 테스트
- [ ] `fields` shorthand / object 식 테스트
- [ ] 값 누락 시 빈 문자열 테스트
- [ ] `metadata` 조회 테스트
- [ ] explicit template selection 성공/실패 테스트
- [ ] 자동 MDF 선택 제거 확인 테스트

#### T6-3. UI adapter 테스트

- [ ] workflow preview 테스트 갱신
- [ ] action data index preview 테스트 갱신
- [ ] 모델 상세 저장 validation 테스트 추가/수정
- [ ] controller 응답 preview 문자열 기대값 갱신

#### T6-4. 문서-코드 정합성 확인

- [ ] canonical 키 이름이 설계 문서와 코드에 동일한지 확인
- [ ] `from=data|metadata`, `mdfTemplateName`, `transforms` 용어가 문서/코드/preview에 일치하는지 확인
- [ ] 예전 계약 키가 테스트 fixture와 문서 예시에 남아 있지 않은지 확인

### T6 acceptance 기준

- [ ] `workflow_filter`가 `and` / `or` 중첩 구조를 실제 런타임에서 평가할 수 있어야 함
- [ ] `action_data_index`가 `data` / `metadata`에서 값만 조회해야 함
- [ ] `mdfTemplateName`이 없으면 MDF 조립이 명시적으로 실패해야 함
- [ ] preview와 문서가 같은 용어를 사용해야 함
- [ ] app docs와 root docs가 같은 표준 계약을 설명해야 함

---

## 추가 확인 필요 사항

- `action_data_index`에서 문자열 shorthand를 어느 범위까지 허용할지 구현 중 재확인 필요
- MDF `<field>` 정의와 새 `action_data_index.fields`의 우선순위가 실제 운영 요구와 맞는지 후속 점검 필요
- `workflow_filter` / `action_data_index`가 4000자를 넘지 않는지 운영 샘플 기준으로 최종 확인 필요
- 문서 반영 후 기존 운영 예시가 모두 새 canonical 계약으로 치환되었는지 최종 점검 필요
