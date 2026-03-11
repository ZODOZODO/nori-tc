# 08. DB 스타터 선택 (DB Starter Selection)

## 개요

nori-tc의 모든 앱은 데이터베이스 연동을 위해 **DB 스타터를 선택**하는 방식을 사용합니다.

DB 스타터는 데이터베이스 종류(PostgreSQL, MySQL, MSSQL, Oracle)와
ORM/Query 방식(JPA, MyBatis)의 조합을 미리 패키징한 라이브러리입니다.
앱은 원하는 조합의 스타터 하나를 `build.gradle.kts`에 추가하기만 하면 됩니다.

---

## 왜 스타터 방식인가?

### 문제: 앱에 직접 DB 설정을 넣으면?

```kotlin
// 안 좋은 예시 — 앱의 build.gradle.kts에 DB 의존성 직접 추가
dependencies {
    implementation("org.postgresql:postgresql:42.6.0")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    // DataSource, TransactionManager, EntityManagerFactory 설정도 직접 작성해야 함...
}
```

- 새 앱을 만들 때마다 같은 DB 설정을 반복해야 합니다
- DB를 PostgreSQL에서 MySQL로 바꾸면 여러 설정 파일을 수정해야 합니다
- 각 앱마다 DataSource 설정이 달라질 수 있어 일관성이 깨집니다

### 해결: 스타터 하나만 선택

```kotlin
// 좋은 예시 — 스타터 하나만 추가하면 됨
dependencies {
    implementation(project(":libs:common:starter:tc-db-postgres-jpa-starter"))
}
// DataSource, TransactionManager, Connection Pool 등이 자동으로 구성됨
```

---

## 사용 가능한 DB 스타터 목록

| 스타터 | DB | ORM |
|--------|-----|-----|
| `tc-db-postgres-jpa-starter` | PostgreSQL | Spring Data JPA (Hibernate) |
| `tc-db-postgres-mybatis-starter` | PostgreSQL | MyBatis |
| `tc-db-mysql-jpa-starter` | MySQL | Spring Data JPA |
| `tc-db-mysql-mybatis-starter` | MySQL | MyBatis |
| `tc-db-mssql-jpa-starter` | Microsoft SQL Server | Spring Data JPA |
| `tc-db-mssql-mybatis-starter` | Microsoft SQL Server | MyBatis |
| `tc-db-oracle-jpa-starter` | Oracle | Spring Data JPA |
| `tc-db-oracle-mybatis-starter` | Oracle | MyBatis |

> **기본 권장:** `tc-db-postgres-jpa-starter` (PostgreSQL + JPA)
>
> 특별한 이유 없으면 이 조합을 사용합니다.

---

## 선택 방법

`apps/{앱-이름}/build.gradle.kts` 에서 원하는 스타터 하나를 추가합니다.

### 예시: PostgreSQL + JPA 선택 (기본)

```kotlin
// apps/tc-comm-gateway-app/build.gradle.kts
dependencies {
    implementation(project(":libs:comm:starter:tc-comm-gateway-starter"))

    // DB 스타터 — 정확히 하나만 선택
    implementation(project(":libs:common:starter:tc-db-postgres-jpa-starter"))
}
```

### 예시: MySQL + MyBatis 선택

```kotlin
dependencies {
    implementation(project(":libs:comm:starter:tc-comm-gateway-starter"))

    // MySQL + MyBatis 조합으로 변경
    implementation(project(":libs:common:starter:tc-db-mysql-mybatis-starter"))
}
```

> 스타터를 두 개 이상 추가하면 DataSource Bean이 충돌할 수 있으므로,
> **반드시 하나만** 추가해야 합니다.

---

## DB 연결 설정

DB 연결 정보는 `config/tc-db.properties` 파일에서 관리합니다.

```properties
# config/tc-db.properties

# JDBC 연결 URL
spring.datasource.url=jdbc:postgresql://192.168.0.13:5432/tc_db

# 접속 정보
spring.datasource.username=tc_user
spring.datasource.password=tc_password

# Connection Pool (HikariCP)
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.connection-timeout=30000    # 30초
spring.datasource.hikari.idle-timeout=600000         # 10분
spring.datasource.hikari.max-lifetime=1800000        # 30분

# JPA 설정 (tc-db-postgres-jpa-starter 사용 시)
spring.jpa.hibernate.ddl-auto=validate               # 운영 환경: 스키마 검증만, 수정 안 함
spring.jpa.show-sql=false                            # 운영 환경: SQL 로그 비활성화
```

---

## JPA 방식 — 사용 패턴

JPA 스타터를 선택하면 Spring Data JPA의 `JpaRepository`를 활용합니다.

```java
// Repository 인터페이스 (Spring Data JPA)
public interface EquipmentJpaRepository extends JpaRepository<EquipmentEntity, String> {
    List<EquipmentEntity> findByEnabled(boolean enabled);
    Optional<EquipmentEntity> findByEqpId(String eqpId);
}

// Adapter 구현 (포트-어댑터 패턴)
@Component
public class JpaEquipmentProfileAdapter implements EquipmentProfileQueryPort {

    private final EquipmentJpaRepository repository;

    @Override
    public List<EquipmentProfile> findAllEnabled() {
        return repository.findByEnabled(true)
            .stream()
            .map(EquipmentEntity::toDomain)
            .collect(Collectors.toList());
    }
}
```

---

## MyBatis 방식 — 사용 패턴

MyBatis 스타터를 선택하면 `@Mapper` 인터페이스를 활용합니다.

```java
// Mapper 인터페이스
@Mapper
public interface EquipmentMapper {
    @Select("SELECT * FROM equipment WHERE enabled = #{enabled}")
    List<EquipmentRow> findByEnabled(@Param("enabled") boolean enabled);
}

// Adapter 구현
@Component
public class MyBatisEquipmentProfileAdapter implements EquipmentProfileQueryPort {

    private final EquipmentMapper mapper;

    @Override
    public List<EquipmentProfile> findAllEnabled() {
        return mapper.findByEnabled(true)
            .stream()
            .map(EquipmentRow::toDomain)
            .collect(Collectors.toList());
    }
}
```

---

## DB 스타터 선택 가이드

| 상황 | 권장 스타터 |
|------|-----------|
| 새 프로젝트 시작, DB 무관 | `tc-db-postgres-jpa-starter` |
| 이미 MySQL 운영 환경이 있음 | `tc-db-mysql-jpa-starter` |
| 복잡한 SQL 쿼리가 많음 | MyBatis 계열 (`*-mybatis-starter`) |
| 단순 CRUD + 자동 쿼리 생성 | JPA 계열 (`*-jpa-starter`) |
| Oracle을 사용 중인 기업 환경 | `tc-db-oracle-jpa-starter` |

---

## 초기 데이터 로딩

앱 기동 시 DB에서 필요한 데이터를 메모리로 로딩합니다.
이는 `@PostConstruct` 에서 수행됩니다.

```java
// Gateway — 설비 프로파일 로딩
@Component
public class EquipmentContextBootstrap {

    private static final int PAGE_SIZE = 500;

    @PostConstruct
    public void load() {
        int page = 0;
        List<EquipmentProfile> batch;

        do {
            // 500개씩 페이지 단위로 로드 (대량 데이터 시 OOM 방지)
            batch = profileQueryPort.findAllPaged(page++, PAGE_SIZE);
            batch.forEach(contextRegistry::register);
        } while (batch.size() == PAGE_SIZE);

        log.info("설비 프로파일 로딩 완료: {}개", contextRegistry.size());
    }
}
```

**페이지 단위 로딩 이유:**
- 설비가 수천 개일 경우 한 번에 모두 로드하면 메모리 부족(OOM)이 발생할 수 있습니다
- 500개씩 나눠서 로드하면 메모리 사용량을 제어할 수 있습니다

---

## 주의사항

| 항목 | 내용 |
|------|------|
| **스타터 1개만** | DB 스타터는 반드시 하나만 추가해야 합니다. 두 개 이상이면 Bean 충돌이 발생합니다 |
| **ddl-auto=validate** | 운영 환경에서는 `ddl-auto=validate`를 사용하세요. `create`나 `create-drop`은 데이터가 삭제됩니다 |
| **비밀번호 관리** | `tc-db.properties`에 DB 비밀번호가 평문으로 저장됩니다. 파일 권한을 `600`으로 제한하세요 |
| **Connection Pool 크기** | `maximum-pool-size`를 너무 크게 설정하면 DB 서버가 과부하됩니다. DB 서버의 `max_connections` 설정과 앱 인스턴스 수를 고려해서 설정하세요 |
| **Connection Timeout** | DB 서버가 응답이 없을 때 앱이 무한정 대기하지 않도록 `connection-timeout`을 반드시 설정하세요 |
