# tc-db-mssql-jpa-starter (FIX)

## 목적
- MSSQL + JPA 조합을 앱에서 쉽게 선택할 수 있게 하는 Starter 입니다.
- 앱은 이 starter만 의존하면 됩니다.

## 포함
- spring-boot-starter-data-jpa
- MSSQL JDBC Driver(runtime)
- JPA common/site schema 스캔(auto-configuration)
- starter 배타 락(fail-fast)

## 설정 방식(FIX)
- DataSource/JPA 설정은 Spring 표준 프로퍼티를 사용합니다.
- `config/tc-db.properties`에 `spring.datasource.*`, `spring.jpa.*`를 넣고
  app의 `application.yaml`에서 `spring.config.import`로 해당 파일을 불러오는 방식이 가장 안정적입니다.

## Fail-fast (중요)
- 실수로 starter를 2개 이상 의존하면 부팅 시 즉시 실패하도록 락 Bean을 등록합니다.
