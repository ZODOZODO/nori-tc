/*
 * tc-db-postgres-jpa-starter (FIX)
 *
 * 역할
 * - PostgreSQL + JPA 조합을 "조립"하는 Starter 모듈
 *
 * 목표
 * - data-jpa / driver / test 좌표의 하드코딩 제거 → Version Catalog로 일원화
 *
 * 포함
 * - spring-boot-starter-data-jpa
 * - PostgreSQL JDBC Driver(runtimeOnly)
 * - JPA common/site schema 스캔(auto-configuration)
 * - starter 배타 락(fail-fast): 동일 Bean 이름 등록
 *
 * 설정 방식(FIX)
 * - DataSource/JPA 설정은 Spring 표준 프로퍼티(spring.datasource.*, spring.jpa.*) 사용
 * - config/tc-db.properties를 app에서 import하여 주입
 */

plugins {
    `java-library`
    alias(libs.plugins.spring.dependency.management)
}

group = "com.nori.tc"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    // API exposure for apps that only depend on this starter.
    // - tc-db-core: store interfaces, PageRequest, exceptions
    // - tc-db-domain: domain records used by gateway app
    api(project(":libs:db:tc-db-core"))
    api(project(":libs:db:tc-db-domain"))
    // JPA 스키마(엔티티/리포지토리/Store 구현체)
    implementation(project(":libs:db:jpa:tc-db-jpa-common-schema"))
    implementation(project(":libs:db:jpa:tc-db-jpa-site-schema"))

    // ✅ 하드코딩 제거
    implementation(libs.spring.boot.starter.data.jpa)
    runtimeOnly(libs.postgresql)

    testImplementation(libs.spring.boot.starter.test)
}

tasks.test {
    useJUnitPlatform()
}
