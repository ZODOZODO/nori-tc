# 01. Workflow Filter / Action Data Index 반영 작업 계획

## 참조 문서

- 설계: `docs/design/01-workflow-filter-and-action-data-index-redesign.md`
- 현행 app 표준: `docs/Architecture/01-mdf-action-data-index-standard.md`
- 루트 아키텍처 문서: `../../../docs/Architecture/business/03-workflow-matching.md`
- 루트 액션 문서: `../../../docs/Architecture/business/04-workflow-action-types.md`

---

## 진행 원칙

- 본 문서는 `workflow_filter`와 `action_data_index` 재설계를 실제 구현과 문서 반영으로 옮기기 위한 작업 계획입니다.
- 실제 구현 순서는 `계약 모델 정리 → runtime 반영 → action/MDF 반영 → UI backend preview/validation 반영 → nori-tc-ui editor 반영 → 문서 동기화 → 테스트` 순서를 유지합니다.
- DB 스키마는 변경하지 않습니다.
- 구현 완료 전까지는 체크박스를 완료 처리하지 않습니다.
- 문서와 코드가 어긋날 가능성이 높은 작업이므로, 각 단계마다 문서-코드 정합성 점검 항목을 포함합니다.
- `T1`~`T5`에서 계약, 런타임, 액션/MDF, preview, validation, 표준 용어가 변경되면 `apps/tc-business-core-app/docs`뿐 아니라 `nori-tc/docs` 하위 관련 문서도 반드시 함께 갱신합니다.
- `nori-tc/docs` 문서 갱신은 `T5`에만 한정된 후행 작업이 아니라, `T1`~`T5` 각 단계의 산출물이 바뀔 때마다 동기화 여부를 즉시 확인하는 상시 작업으로 간주합니다.
- 서버 preview/validation이 완료되었더라도 `nori-tc-ui` structured editor가 예전 계약을 생성하면 실제 운영 입력이 다시 깨질 수 있으므로, frontend 반영을 별도 완료 조건으로 관리합니다.

---

## 작업 범위

| 작업 ID | 작업 항목 | 주요 대상 |
|---|---|---|
| T1 | 계약 모델/파서 리팩터링 | business-core filter/action parser |
| T2 | `workflow_filter` 런타임 반영 | evaluator, payload 해석, 예외 처리 |
| T3 | `action_data_index` / MDF 반영 | action resolver, MDF composer |
| T4 | UI preview / 저장 검증 / frontend editor 반영 | ui-web-adapter preview, controller/save path, nori-tc-ui model detail editor |
| T5 | app/docs 및 root docs 문서 반영 | tc-business-core-app/docs, root docs |
| T6 | 테스트 및 acceptance 검증 | business-core test, ui adapter test, nori-tc-ui test, 문서 정합성 |

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

- [x] `BusinessActionDataIndexHybridResolver`를 새 계약 기준으로 전환
- [x] `mdfTemplateName` 루트 키 처리 추가
- [x] `fields` 하위 `from`, `path`, `transforms` 처리 추가
- [x] 문자열 shorthand를 `from=data` 기본값으로 처리
- [x] 값 누락 시 빈 문자열 반환 정책 반영

#### T3-2. MDF 템플릿 선택 정책 전환

- [x] `BusinessMdfMessageComposer`에서 `actionName + target` 자동 템플릿 선택 제거
- [x] `mdfTemplateName` explicit 선택만 허용
- [x] `mdfTemplateName` 누락 시 실패 처리
- [x] 템플릿 미존재 / target mismatch 실패 처리
- [x] `action_data_index` 비어 있을 때 raw message fallback 유지

#### T3-3. MDF field fallback 충돌 점검

- [x] MDF `<field>` 정의와 새 `action_data_index.fields` 우선순위 재확인
- [x] 새 계약에서 `fixed` / `required` 제거에 따른 fallback 영향 점검
- [x] `metadata` 블록 값을 MDF 필드에 매핑하는 경로 확인

### T3 검증

- [x] `mdfTemplateName` 기반으로만 MDF 템플릿이 선택되는지 확인
- [x] 자동 템플릿 선택 코드가 제거되었는지 확인
- [x] `action_data_index`가 비교 없는 값 조회 전용으로 동작하는지 확인

---

## T4. UI preview / 저장 검증 반영

### 목적

새 계약이 모델 상세 화면 preview와 저장 경로, 그리고 `nori-tc-ui` structured editor에서도 동일하게 보이도록 맞춥니다.

### 작업 내용

#### T4-1. workflow preview 교체

- [x] `ModelDetailPreviewSupport`의 workflow preview를 새 계약 기준으로 재작성
- [x] 첫 번째 조건만 요약하는 방식에서 전체 식 요약 방식으로 전환
- [x] 새 preview 문구가 `data` / `metadata` 용어를 쓰는지 확인
- [x] `and`, `or`, `comparison`, `expected`, `transforms`가 드러나는 preview 규칙 정의

#### T4-2. action_data_index preview 교체

- [x] `mdfTemplateName` 기반 preview 문자열로 변경
- [x] 첫 필드 요약이 `from`, `path`, `transforms` 기준으로 출력되는지 확인
- [x] 예전 `messageName`, `MSG`, `CTX`, `AUTO`, `var`, `source`, `xform` 표기가 제거되는지 확인

#### T4-3. 저장 API validation 반영

- [x] 모델 상세 저장 경로에서 `workflow_filter` 구조 검증 추가
- [x] 모델 상세 저장 경로에서 `action_data_index` 구조 검증 추가
- [x] validation 실패 시 400 응답과 원인 메시지 정리

#### T4-4. `nori-tc-ui` structured editor 반영

- [x] `nori-tc-ui/src/features/model/lib/model-detail-editor.ts`의 parse / build / summarize 로직을 새 canonical 계약 기준으로 전환
- [x] `workflow_filter` structured editor를 flat AND row 편집기에서 `and` / `or` 그룹 + 조건 노드(`from`, `path`, `comparison`, `expected`, `transforms`) 편집 방식으로 전환
- [x] `workflow_filter` 새 canonical JSON을 열었을 때 blank structured editor로 깨지지 않도록 lossless parse 또는 raw fallback 정책 반영
- [x] `action_data_index` structured editor를 `mdfTemplateName`, `fields`, `from`, `path`, `transforms` 기준으로 전환
- [x] `action_data_index.fields` 문자열 shorthand를 `from=data`, `path=<value>` 의미로 정상 로드하거나 raw mode에서 의미 손실 없이 유지
- [x] `messageName`, `mdf`, `var`, `source`, `xform`, `fixed`, `required`, `MSG`, `CTX`, `AUTO` 중심 UI 제거
- [x] `nori-tc-ui/src/features/model/components/ModelDetailPanel.tsx`의 modal 설명/placeholder/컬럼명을 새 canonical 용어로 교체
- [x] `nori-tc-ui/src/features/model/components/ModelPage.tsx`에서 local preview fallback이 새 canonical 요약을 사용하도록 반영

#### T4-5. frontend 저장 오류 노출 정비

- [x] 저장 API 400 응답 메시지가 `nori-tc-ui`에서 사용자에게 그대로 노출되는지 확인
- [x] 사용자가 어떤 row의 `workflow_filter` / `action_data_index`에서 오류가 났는지 다시 확인할 수 있는 재수정 동선 정리
- [x] 저장 실패 후 modal 재오픈/재편집 시 직전 입력값이 유지되는지 확인

#### T4-6. 공통 page sidebar 가변 폭 반영

- [x] `nori-tc-ui`의 공통 page(`EqpPage`, `ModelPage`) sidebar를 단순 open/close 토글이 아니라 좌우 drag resize 가능한 구조로 전환
- [x] `eqp-ui.store.ts`, `model-ui.store.ts`의 `sidebarOpen` boolean 중심 상태를 `sidebarWidth` + collapsed/open 정책까지 표현할 수 있도록 확장
- [x] `EqpSidebar.tsx`, `ModelSidebar.tsx`에 resize handle과 min/max width 제약을 추가
- [x] `EqpPage.tsx`, `ModelPage.tsx`의 상단 navigation offset 계산이 현재 sidebar width를 기준으로 동작하도록 수정
- [x] 축소/확대 버튼과 drag resize가 함께 있어도 interaction 충돌이 없도록 정리

#### T4-7. branch create / checkout undo 정합성 보정

- [x] `nori-tc` / `nori-tc-ui`를 함께 확인해 branch create 직후 version lifecycle과 explicit checkout / undo 의미를 재정의
- [x] 현재 `branch create -> checkout -> undo` 시 checkout undo가 아니라 branch model 삭제처럼 보이는 원인 분석
- [x] `ModelPage.tsx`의 `handleUndoCheckIn` 경로와 실제 backend delete 동작을 점검해 checkout undo가 branch 전체 삭제와 동일해지지 않도록 수정
- [x] 필요 시 `ModelController`, `JpaModelManagementPort`의 branch create / checkout / delete 정책도 함께 수정
- [x] `branch create -> checkout -> undo` 후 branch model은 유지되고 checkout으로 만든 편집 상태만 원복되는지 검증

#### T4-8. workflow data index modal UX 보정

- [x] workflow > data index modal에서 `MDF Message` label과 선택 control 사이의 vertical spacing을 늘려 가독성을 보정
- [x] `MDF Message`를 자유 입력 text field가 아니라 dropdown/select 기반 선택 UI로 전환
- [x] dropdown option source를 현재 model의 MDF 목록(`mdfContents.name`) 또는 backend 제공 목록 중 하나로 확정
- [x] 선택된 MDF template 값이 깔끔하게 보이도록 select trigger 또는 동등한 read-only surface 스타일로 정리
- [x] workflow > data index modal의 table 영역에 고정 높이와 내부 scroll을 적용
- [x] workflow > filter modal도 동일하게 table 영역에 고정 높이와 내부 scroll을 적용
- [x] field/condition이 계속 추가되어도 modal 전체 높이가 무한히 커지지 않도록 고정 높이 + 내부 scroll 구조를 반영

#### T4-9. 공통 modal close policy 정비

- [x] `nori-tc-ui` 공통 `Dialog` / `ConfirmDialog` wrapper를 점검해 바깥 영역 클릭으로 모달이 닫히지 않도록 정책을 통일
- [x] model page workflow editor modal에서 취소/적용 같은 명시 버튼으로만 닫히도록 수정
- [x] branch create, root update, parent commit, check in, eqp 관리 modal 등 다른 공통 modal에도 같은 close policy를 적용
- [ ] modal이 열린 상태에서 모델 목록, 상세 패널, 배경 영역 등 다른 곳을 클릭해도 모달 state가 사라지지 않는지 확인

### T4 검증

- [x] workflow preview가 새 계약 용어로 출력되는지 확인
- [x] action data index preview가 새 계약 용어로 출력되는지 확인
- [x] 잘못된 JSON 저장 시 400이 반환되는지 확인
- [ ] `nori-tc-ui` structured editor가 새 canonical JSON을 열고 다시 저장해도 의미가 유지되는지 확인
- [ ] `nori-tc-ui`에서 수정한 값이 예전 계약 키 없이 canonical JSON으로 저장되는지 확인
- [ ] local preview fallback과 저장 후 서버 preview가 같은 용어 체계를 사용하는지 확인
- [ ] `EqpPage`, `ModelPage` sidebar가 drag resize 후에도 layout이 깨지지 않고 width가 정상 반영되는지 확인
- [ ] `branch create -> checkout -> undo`가 branch delete가 아니라 checkout undo 의미로 동작하는지 확인
- [ ] workflow data index modal의 `MDF Message` dropdown, spacing, fixed-height table scroll이 정상 동작하는지 확인
- [ ] workflow filter modal의 fixed-height table scroll이 정상 동작하는지 확인
- [ ] 공통 modal이 외부 클릭으로 닫히지 않고 명시 버튼으로만 닫히는지 확인

---

## T5. app/docs 및 root docs 문서 반영

### 목적

새 계약을 공식 문서와 app 문서 진입점에 반영해 문서-코드 기준을 통일합니다.

### 작업 내용

#### T5-1. app docs 신규 작성

- [x] `apps/tc-business-core-app/docs/design/01-workflow-filter-and-action-data-index-redesign.md` 작성
- [x] `apps/tc-business-core-app/docs/tasks/01-workflow-filter-and-action-data-index-build-plan.md` 작성

#### T5-2. 기존 app 문서 재작성

- [x] `apps/tc-business-core-app/docs/Architecture/01-mdf-action-data-index-standard.md` 전면 재작성
- [x] `apps/tc-business-core-app/docs/README.md` 링크/설명 업데이트
- [x] app README가 새 design/tasks 문서를 가리키는지 확인

#### T5-3. root docs 갱신

- [x] `docs/Architecture/business/03-workflow-matching.md` 갱신 체크
- [x] `docs/Architecture/business/04-workflow-action-types.md` 갱신 체크
- [x] root docs가 `data`, `metadata`, `mdfTemplateName` 기준으로 설명하는지 확인

### T5 검증

- [x] app docs와 root docs에서 같은 canonical 용어를 사용하는지 확인
- [x] 예전 용어가 표준 문서 본문에서 제거되었는지 확인
- [x] design/tasks/architecture/readme 간 상호 링크가 맞는지 확인

---

## T6. 테스트 및 acceptance 검증

### 목적

새 계약이 runtime, action/MDF, UI preview, 저장 검증, 문서까지 일관되게 반영되었는지 확인합니다.

### 작업 내용

#### T6-1. business-core 단위 테스트

- [x] `workflow_filter` 단일 조건 성공/실패 테스트
- [x] `and` 그룹 테스트
- [x] `or` 그룹 테스트
- [x] `and` 안의 `or` 중첩 테스트
- [x] 사용자 예시 복합식 테스트
- [x] `from=data`, `from=metadata` 테스트
- [x] 절대 경로 금지 validation 테스트
- [x] transform 실패 fallback 테스트

#### T6-2. action/MDF 테스트

- [x] `action_data_index`의 `mdfTemplateName` 파싱 테스트
- [x] `fields` shorthand / object 식 테스트
- [x] 값 누락 시 빈 문자열 테스트
- [x] `metadata` 조회 테스트
- [x] explicit template selection 성공/실패 테스트
- [x] 자동 MDF 선택 제거 확인 테스트

#### T6-3. UI adapter 테스트

- [x] workflow preview 테스트 갱신
- [x] action data index preview 테스트 갱신
- [x] 모델 상세 저장 validation 테스트 추가/수정
- [x] controller 응답 preview 문자열 기대값 갱신

#### T6-4. `nori-tc-ui` 테스트/검증

- [x] `model-detail-editor.ts`의 canonical parse / build / summarize 단위 테스트 추가
- [x] `workflow_filter` structured editor가 `and` / `or` 중첩 구조를 표시/수정/저장하는 시나리오 검증
- [x] `action_data_index` structured editor가 `mdfTemplateName`, `fields`, `from`, `path`, `transforms`를 표시/수정/저장하는 시나리오 검증
- [x] 새 canonical JSON을 열었을 때 blank structured form 또는 구계약 재직렬화가 발생하지 않는지 검증
- [ ] 저장 400 오류 메시지가 화면에 노출되는지 테스트 또는 수동 검증
- [ ] `EqpPage`, `ModelPage` sidebar resize interaction 수동 검증 또는 컴포넌트 테스트 추가
- [ ] `branch create -> checkout -> undo` 회귀 시나리오를 `nori-tc` / `nori-tc-ui` 연동 기준으로 검증
- [ ] workflow data index modal의 `MDF Message` dropdown과 fixed-height table scroll UI를 수동 검증
- [ ] 공통 modal outside click dismiss 방지 동작을 수동 검증 또는 컴포넌트 테스트로 확인

#### T6-5. 문서-코드 정합성 확인

- [x] canonical 키 이름이 설계 문서와 코드에 동일한지 확인
- [x] `from=data|metadata`, `mdfTemplateName`, `transforms` 용어가 문서/코드/preview에 일치하는지 확인
- [x] 예전 계약 키가 허용 fixture/운영 예시에 남아 있지 않고, 금지 예시/거절 테스트에만 제한적으로 남아 있는지 확인

### T6 acceptance 기준

- [x] `workflow_filter`가 `and` / `or` 중첩 구조를 실제 런타임에서 평가할 수 있어야 함
- [x] `action_data_index`가 `data` / `metadata`에서 값만 조회해야 함
- [x] `mdfTemplateName`이 없으면 MDF 조립이 명시적으로 실패해야 함
- [x] preview와 문서가 같은 용어를 사용해야 함
- [x] app docs와 root docs가 같은 표준 계약을 설명해야 함
- [x] `nori-tc-ui`에서 새 canonical JSON을 열고 저장해도 계약이 손상되지 않아야 함
- [x] `nori-tc-ui` structured editor와 local preview가 예전 용어 없이 새 canonical 용어를 사용해야 함
- [ ] `EqpPage`, `ModelPage` sidebar가 공통 resize UX를 제공해야 함
- [ ] `branch create -> checkout -> undo`가 branch 삭제가 아니라 checkout undo 의미로 동작해야 함
- [ ] workflow data index modal이 dropdown 기반 MDF template 선택, 적절한 spacing, 고정 높이 table scroll을 제공해야 함
- [ ] 공통 modal이 외부 클릭으로 닫히지 않고 명시적 버튼 액션으로만 닫혀야 함

---

## 추가 확인 필요 사항

- `action_data_index`에서 문자열 shorthand를 어느 범위까지 허용할지 구현 중 재확인 필요
- `nori-tc-ui`에서 `workflow_filter` 트리 editor를 어느 수준까지 구조화 UI로 제공할지(완전 트리 편집 vs 안전한 raw fallback) 결정 필요
- `nori-tc-ui` structured editor가 `action_data_index.fields`를 항상 object 식으로 저장할지, shorthand를 재직렬화까지 유지할지 결정 필요
- `MDF Message` dropdown option source를 현재 model의 `mdfContents.name`으로 충분히 해결할지, 별도 backend API/metadata가 필요한지 결정 필요
- branch create가 처음부터 `EDIT` version을 생성하는 현재 정책을 유지할지, checkout/undo 의미 분리를 위해 version lifecycle을 조정할지 결정 필요
- MDF `<field>` 정의와 새 `action_data_index.fields`의 우선순위가 실제 운영 요구와 맞는지 후속 점검 필요
- `workflow_filter` / `action_data_index`가 4000자를 넘지 않는지 운영 샘플 기준으로 최종 확인 필요
- 문서 반영 후 기존 운영 예시가 모두 새 canonical 계약으로 치환되었는지 최종 점검 필요
