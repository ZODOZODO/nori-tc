# tc-db-oracle-jpa-starter (FIX)

## 목적
- Oracle + JPA 조합을 앱에서 쉽게 선택할 수 있게 하는 Starter 입니다.
- 앱은 이 starter만 의존하면 됩니다.

## 포함
- spring-boot-starter-data-jpa
- Oracle JDBC Driver(runtime)
- JPA common/site schema 스캔(auto-configuration)
- starter 배타 락(fail-fast)

## 설정 방식(FIX)
- DataSource/JPA 설정은 Spring 표준 프로퍼티를 사용합니다.
- `config/tc-db.properties`에 `spring.datasource.*`, `spring.jpa.*`를 넣고
  app의 `application.yaml`에서 `spring.config.import`로 해당 파일을 불러옵니다.

## Fail-fast
- 실수로 starter를 2개 이상 의존하면 부팅 시 즉시 실패하도록
  동일한 Bean 이름(`tcDbStarterExclusiveLock`)을 등록합니다.

## Oracle 실전 주의(설계 확인용)
- Oracle은 BOOLEAN 컬럼 타입이 표준 테이블 컬럼으로는 일반적으로 사용되지 않습니다.
  (보통 NUMBER(1)/CHAR(1)로 모델링)
- 현재 common-schema 엔티티에 boolean 필드가 있다면,
  실제 Oracle DDL에서 해당 컬럼을 어떻게 만들지(0/1, Y/N 등)를 별도로 고정해야 합니다.
  이 부분은 “DB 모듈”만으로 해결되는 영역이 아니라 실제 스키마(DDL) 정책 영역입니다.
