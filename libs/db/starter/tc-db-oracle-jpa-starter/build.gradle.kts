/*
 * tc-db-oracle-jpa-starter (FIX)
 *
 * 역할
 * - Oracle + JPA 조합을 "조립"하는 Starter 모듈
 *
 * 목표
 * - data-jpa / driver / test 좌표의 하드코딩 제거 → Version Catalog로 일원화
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
    // API exposure: core/domain types are used directly by apps.
    api(project(":libs:db:tc-db-core"))
    api(project(":libs:db:tc-db-domain"))
    // JPA 스키마(엔티티/리포지토리/Store 구현체)
    implementation(project(":libs:db:jpa:tc-db-jpa-common-schema"))
    implementation(project(":libs:db:jpa:tc-db-jpa-site-schema"))

    // ✅ 하드코딩 제거
    implementation(libs.spring.boot.starter.data.jpa)
    runtimeOnly(libs.oracle.ojdbc11)

    testImplementation(libs.spring.boot.starter.test)
}

tasks.test {
    useJUnitPlatform()
}
