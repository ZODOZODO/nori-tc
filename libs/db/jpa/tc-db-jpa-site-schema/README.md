

# tc-db-jpa-site-schema (Layer 2 / JPA) - FIX

## 목적
- 사이트(현장)별로 달라지는 DB 스키마 확장을 이 모듈에서 수용합니다.
- 현재는 비어있지만, 언제든 Entity/Repository를 추가할 수 있도록 “연결만” 유지합니다.

## 현재 상태
- Entity: 0개
- Repository: 0개
- Marker 클래스만 존재

## 포함 대상(추후)
- 사이트 전용 테이블/컬럼을 위한 `@Entity`
- 사이트 전용 Spring Data JPA Repository

## 조립 규칙(FIX)
- Starter가 **common-schema + site-schema를 함께 스캔**합니다.
- 따라서 site가 비어 있어도 정상 동작해야 합니다.
