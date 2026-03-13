# tc-business-core-app Workflow / MDF 문서

## 문서 목적

- `workflow_filter`, `action_data_index`, MDF(XML) 관련 설계/구현 계획/운영 표준 문서를 한 곳에서 찾을 수 있게 정리합니다.
- app 문서와 root 아키텍처 문서가 동일한 canonical 용어를 사용하도록 진입점을 제공합니다.
- 모델 등록자, 운영 담당자, 개발 담당자가 읽어야 할 순서를 명확히 안내합니다.

## 권장 읽기 순서

1. [재설계 문서](./design/01-workflow-filter-and-action-data-index-redesign.md)
2. [구현 계획 문서](./tasks/01-workflow-filter-and-action-data-index-build-plan.md)
3. [운영 표준 문서](./Architecture/01-mdf-action-data-index-standard.md)
4. [루트 아키텍처: 워크플로우 매칭](../../../docs/Architecture/business/03-workflow-matching.md)
5. [루트 아키텍처: 워크플로우 액션 타입](../../../docs/Architecture/business/04-workflow-action-types.md)

## app 문서

- [재설계 문서](./design/01-workflow-filter-and-action-data-index-redesign.md)
- [구현 계획 문서](./tasks/01-workflow-filter-and-action-data-index-build-plan.md)
- [운영 표준 문서](./Architecture/01-mdf-action-data-index-standard.md)

## 주요 설명 범위

- `workflow_filter`의 `and` / `or` AST 구조
- `from=data|metadata`, 상대 `path`, `comparison`, `expected`, `transforms`
- `action_data_index`의 `mdfTemplateName`, `fields`, `from`, `path`, `transforms`
- MDF 템플릿 explicit 선택 정책과 field fallback 우선순위
- 모델 상세 preview / 저장 검증 시 노출되는 공개 계약

## 관련 코드 참조

- [BusinessWorkflowFilterEvaluator.java](../../../libs/business/tc-business-core/src/main/java/com/nori/tc/business/core/workflow/internal/matching/BusinessWorkflowFilterEvaluator.java)
- [BusinessActionDataIndexHybridResolver.java](../../../libs/business/tc-business-core/src/main/java/com/nori/tc/business/core/workflow/internal/support/BusinessActionDataIndexHybridResolver.java)
- [BusinessMdfMessageComposer.java](../../../libs/business/tc-business-core/src/main/java/com/nori/tc/business/core/workflow/internal/support/BusinessMdfMessageComposer.java)
- [ModelDetailWorkflowJsonSupport.java](../../../libs/ui/adapter/tc-ui-web-adapter/src/main/java/com/nori/tc/ui/adapters/web/controller/support/ModelDetailWorkflowJsonSupport.java)

## 범위

- 본 README는 문서 진입점 역할만 수행합니다.
- 런타임 동작의 source of truth는 실제 구현 코드와 각 상세 문서입니다.
