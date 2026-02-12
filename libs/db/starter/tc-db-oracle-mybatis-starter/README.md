

# tc-db-oracle-mybatis-starter (FIX)

## 목적
- Oracle + MyBatis 조합을 앱에서 쉽게 선택할 수 있게 하는 Starter 입니다.
- 앱은 이 starter만 의존하면 됩니다.

## 포함
- mybatis-spring-boot-starter
- Oracle JDBC Driver(runtime)
- MyBatis common/site schema 스캔 + mapper XML 위치 연결
- starter 배타 락(fail-fast)

## 설정 방식(FIX)
- DataSource: spring.datasource.* (표준)
- MyBatis: mybatis.* (표준)
- `config/tc-db.properties`를 app에서 import하여 사용합니다.

## mapper XML 위치(권장)
- common: classpath*:mybatis/common/*.xml
- site:   classpath*:mybatis/site/*.xml  (현재는 비어 있어도 무해)

예)
mybatis.mapper-locations=classpath*:mybatis/common/*.xml,classpath*:mybatis/site/*.xml

## Fail-fast
- 실수로 starter를 2개 이상 의존하면 부팅 시 즉시 실패하도록
  동일한 Bean 이름(`tcDbStarterExclusiveLock`)을 등록합니다.

## Oracle 실전 주의(설계 확인용)
- Oracle은 페이징/업서트 등 방언 차이가 커서,
  공통 CRUD 외 확장 SQL이 필요하면 site-schema에 분리하거나
  vendor별 mapper를 별도 모듈로 두는 방식을 고려해야 합니다.
