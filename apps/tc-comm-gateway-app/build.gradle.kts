/*
 * tc-comm-gateway-app (FIX)
 *
 * 목표
 * - 이 앱은 "웹 애플리케이션"이 아니다. (REST 서버가 필수 아님)
 * - DB 벤더/기술(JPA/MyBatis)은 앱 코드가 알 필요 없다.
 * - 앱은 "DB starter 1개"만 선택해서 의존한다. (DB 기술 교체는 starter 교체로만)
 *
 * 이번 컴파일 에러 원인
 * - SpringApplication / @SpringBootApplication 클래스는 "플러그인"만으로는 제공되지 않는다.
 * - 즉, spring-boot 플러그인을 걸어도 spring-boot-starter 의존성이 없으면
 *   org.springframework.boot.* 패키지를 컴파일 시점에 찾을 수 없다.
 *
 * 해결(FIX)
 * - implementation(libs.spring.boot.starter) 추가
 *   → 웹 기능 없이도 Spring Boot 애플리케이션으로 부팅 가능(기본 스타터)
 *
 * 매우 중요(FIX)
 * - 아래 DB starter dependencies 중 "딱 1개만" 활성화해야 한다.
 * - 2개 이상을 켜면 starter들이 동일 Bean 이름(tcDbStarterExclusiveLock)을 등록하므로
 *   Spring Boot 부팅이 즉시 실패한다(fail-fast).
 */

plugins {
    // Spring Boot 실행/패키징 관련 플러그인
    alias(libs.plugins.spring.boot)

    // BOM/버전 정합성(의존성 버전 통일) 플러그인
    alias(libs.plugins.spring.dependency.management)

    // Java 컴파일 플러그인
    java
}

group = "com.nori.tc"
version = "0.0.1-SNAPSHOT"

java {
    // Java 21 고정
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    /*
     * =========================
     * Spring Boot 기본 스타터 (FIX)
     * =========================
     *
     * - SpringApplication, @SpringBootApplication 등
     *   org.springframework.boot.* 클래스를 컴파일/실행 시점에 제공한다.
     * - web starter가 아니므로 기본적으로 Tomcat/웹 서버를 강제로 끌어오지 않는다.
     *   (단, 다른 starter가 web을 끌고 오면 그건 별도 이슈)
     */
    implementation(libs.spring.boot.starter)

    /*
     * =========================
     * DB Starter (딱 1개만 선택)
     * =========================
     *
     * - 앱은 DB 접근 기술(JPA/MyBatis)과 벤더(Postgres/MySQL/...)를 몰라도 된다.
     * - 교체는 "starter 한 줄 변경"으로만 수행한다.
     */

    // DEFAULT(FIX): PostgreSQL + JPA
    implementation(project(":libs:db:starter:tc-db-postgres-jpa-starter"))

    // PostgreSQL + MyBatis
    // implementation(project(":libs:db:starter:tc-db-postgres-mybatis-starter"))

    // MySQL + JPA
    // implementation(project(":libs:db:starter:tc-db-mysql-jpa-starter"))

    // MySQL + MyBatis
    // implementation(project(":libs:db:starter:tc-db-mysql-mybatis-starter"))

    // MSSQL + JPA
    // implementation(project(":libs:db:starter:tc-db-mssql-jpa-starter"))

    // MSSQL + MyBatis
    // implementation(project(":libs:db:starter:tc-db-mssql-mybatis-starter"))

    // Oracle + JPA
    // implementation(project(":libs:db:starter:tc-db-oracle-jpa-starter"))

    // Oracle + MyBatis
    // implementation(project(":libs:db:starter:tc-db-oracle-mybatis-starter"))

    /*
     * =========================
     * Test
     * =========================
     */
    testImplementation(libs.spring.boot.starter.test)
}

tasks.test {
    useJUnitPlatform()
}

/*
 * main class 지정 (FIX)
 * - Gradle이 Spring Boot 실행/패키징 시 Main 클래스를 명확히 알도록 고정한다.
 * - 패키지/클래스명이 다르면 여기 문자열만 수정하면 된다.
 */
springBoot {
    mainClass.set("com.nori.tc.apps.commgateway.TcCommGatewayApplication")
}
