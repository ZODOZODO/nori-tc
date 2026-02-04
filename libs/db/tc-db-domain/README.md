# tc-db-domain (Layer 0) - FIX

## 목적
`tc-db-domain`은 DB 모듈의 **순수 데이터 계층(Layer 0)** 입니다.  
앱/스타터/어댑터가 어떤 DB 벤더(Postgres/MySQL/MSSQL/Oracle)와 어떤 퍼시스턴스 기술(JPA/MyBatis)을 쓰더라도
공통으로 재사용 가능한 **데이터 모델(테이블 1행 표현)** 을 제공합니다.

## 포함 대상(허용)
- 테이블 1행에 대응하는 순수 DTO (`record` 권장)
- Enum (프로토콜/상태/로그레벨 등)
- 단순 상수/값 객체(필요 시)

## 제외 대상(금지)
아래 항목은 **절대 추가하지 않습니다.**
- Spring 프레임워크 의존 코드
  - 예: `@Component`, `@Configuration`, `Environment`, `ApplicationContext` 등
- JPA/Hibernate 의존 코드
  - 예: `@Entity`, `@Table`, `EntityManager`, Hibernate 어노테이션 등
- MyBatis 의존 코드
  - 예: `@Mapper`, MyBatis 설정/세션/타입핸들러 등
- JDBC/DB 드라이버 의존 코드
  - 예: `java.sql.*`, 드라이버 클래스 직접 참조 등
- Repository/Mapper/Query/SQL/트랜잭션 등 “DB 접근” 로직 전부
- AutoConfiguration/Starter 관련 코드 전부

## 설계 원칙(FIX)
- 이 모듈은 “기술 중립”이어야 합니다.
- Validation(입력 검증) 같은 로직은 보통 상위 계층에서 처리합니다.
  - 단, 값 객체 수준의 불변성 보장(Null 금지 등)은 필요하면 논의 후 추가합니다.
- 시간 타입은 `timestamp with time zone`에 대응하여 **`OffsetDateTime`/`Instant` 계열**을 사용합니다.
  - 시간 기준은 `tc-db.properties`에서 `UTC`를 기본으로 가져갑니다.
  - 화면/로깅 표시는 필요에 따라 KST로 변환합니다.

## 패키지 구조 가이드
- 공통 Enum: `com.nori.tc.db.domain.common`
- 모델: `com.nori.tc.db.domain.model`
- 설비: `com.nori.tc.db.domain.eqp`
