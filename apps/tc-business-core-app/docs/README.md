# tc-business-core-app MDF/action_data_index 문서

## 문서 목적
- `tc-business-core-app`에서 사용하는 MDF(XML)와 `action_data_index` 작성 규칙을 운영 표준으로 고정합니다.
- 모델 등록자/운영자가 동일한 형식으로 정의를 작성하도록 가이드를 제공합니다.
- 코드 동작과 문서 규칙을 일치시켜 배포 전/장애 대응 시 혼선을 줄입니다.

## 문서 대상
- 모델링 담당자(MDF/XML 작성)
- 운영 담당자(action_data_index 등록/검증)
- 개발 담당자(코드-스펙 정합성 점검)

## 빠른 시작
1. [01-mdf-action-data-index-standard.md](./01-mdf-action-data-index-standard.md)를 먼저 읽습니다.
2. 표준 JSON 예시(최소형/권장형/장애 유도형)를 기반으로 실제 `action_data_index`를 작성합니다.
3. 운영 체크리스트로 등록 전 검증을 수행합니다.

## 표준 문서
- [01-mdf-action-data-index-standard.md](./01-mdf-action-data-index-standard.md)

## 관련 코드 참조
- [BusinessMdfRuntimeParser.java](../../../libs/business/adapter/tc-business-db-adapter/src/main/java/com/nori/tc/business/adapters/db/modelcache/BusinessMdfRuntimeParser.java)
- [BusinessMdfMessageComposer.java](../../../libs/business/tc-business-core/src/main/java/com/nori/tc/business/core/workflow/internal/support/BusinessMdfMessageComposer.java)
- [BusinessActionDataIndexHybridResolver.java](../../../libs/business/tc-business-core/src/main/java/com/nori/tc/business/core/workflow/internal/support/BusinessActionDataIndexHybridResolver.java)

## 범위
- 본 문서는 운영 표준 문서화 범위입니다.
- 코드 API/DB 스키마/런타임 동작 자체를 변경하지 않습니다.