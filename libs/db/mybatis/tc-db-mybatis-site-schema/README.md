

# tc-db-mybatis-site-schema (Layer 2 / MyBatis) - FIX

## 목적
- 사이트(현장)별로 달라지는 스키마 확장을 이 모듈에서 수용합니다.
- 현재는 "연결만" 유지합니다.

## 현재 상태
- Mapper: 0개
- XML: 0개
- Marker 클래스만 존재

## 조립 규칙(FIX)
- starter가 common + site를 함께 스캔합니다.
- site가 비어 있어도 정상 동작해야 합니다.
