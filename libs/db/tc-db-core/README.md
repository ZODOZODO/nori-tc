# tc-db-core (Layer 1) - FIX

## 목적
`tc-db-core`는 DB 접근을 위한 **기술 중립 CRUD 인터페이스**를 제공합니다.

- App은 DB가 Postgres/MySQL/MSSQL/Oracle인지 몰라도 됩니다.
- App은 구현이 JPA/MyBatis인지 몰라도 됩니다.
- App은 오직 `tc-db-core`의 인터페이스만 사용합니다.
- 실제 구현체는 `tc-db-jpa-*-schema` 또는 `tc-db-mybatis-*-schema`가 제공하고,
  최종 조립은 `tc-db-*-*-starter`가 담당합니다.

## 포함 대상
- CRUD Store 인터페이스(예: TcModelStore, TcEqpStore 등)
- CRUD 입력용 Command/Criteria(record)
- 공통 예외/페이징 모델

## 금지 사항
- Spring / JPA / MyBatis / JDBC / Driver 의존 코드 전부 금지
- SQL/쿼리 문자열 직접 포함 금지
