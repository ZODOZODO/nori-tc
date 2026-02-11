/*
 * tc-comm-gateway-app
 *
 * Intent
 * - Assembly module only. No app-specific DB/Kafka/Redis implementations.
 * - Enable exactly one relational DB starter at a time.
 *
 * Notes
 * - Spring Boot plugin + starter are required for compile/runtime.
 * - Multiple DB starters will conflict on bean names and fail fast.
 */

plugins {
    // Spring Boot packaging/runtime plugin
    alias(libs.plugins.spring.boot)

    // BOM/version alignment plugin
    alias(libs.plugins.spring.dependency.management)

    // Java compiler plugin
    java
}

group = "com.nori.tc"
version = "0.0.1-SNAPSHOT"

java {
    // Java 21 toolchain
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    /*
     * =========================
     * Spring Boot baseline
     * =========================
     *
     * - Required to compile SpringApplication/@SpringBootApplication.
     * - Does not imply a web server.
     */
    implementation(libs.spring.boot.starter)

    /*
     * =========================
     * Log Starter
     * =========================
     *
     * - 공통 로그 패턴/정책을 모든 앱에 동일 적용
     */
    implementation(project(":libs:log:starter:tc-log-starter"))

    /*
     * =========================
     * Comm Gateway Starter
     * =========================
     *
     * - 게이트웨이 코어 + Netty/Kafka/DB/Redis 어댑터를 한 번에 제공
     * - 앱은 이 스타터만 의존하고 나머지 구성은 프로퍼티로 제어
     */
    implementation(project(":libs:comm-gateway:starter:tc-comm-gateway-starter"))

    /*
     * =========================
     * DB Starter (pick exactly one)
     * =========================
     *
     * - Choose DB technology (JPA/MyBatis) and vendor (Postgres/MySQL/etc).
     * - To switch, comment out the current one and enable another.
     */

    // DEFAULT: PostgreSQL + JPA
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

    // Redis/Kafka/Netty/Comm 의존은 comm-gateway-starter가 전부 제공한다.

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
 * main class
 * - Explicitly set Spring Boot main class for packaging.
 */
springBoot {
    mainClass.set("com.nori.tc.apps.commgateway.TcCommGatewayApplication")
}
