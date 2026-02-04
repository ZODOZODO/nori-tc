/*
 * tc-db-postgres-mybatis-starter (FIX)
 *
 * 역할
 * - PostgreSQL + MyBatis 조합을 "조립"하는 Starter 모듈
 *
 * 목표
 * - driver / test 좌표의 하드코딩 제거 → Version Catalog로 일원화
 *
 * 포함
 * - mybatis-spring-boot-starter(버전은 catalog에서 관리)
 * - PostgreSQL JDBC Driver(runtimeOnly)
 * - MyBatis common/site schema(Mapper + XML) 연결(auto-configuration)
 * - starter 배타 락(fail-fast): 동일 Bean 이름 등록
 *
 * 설정 방식(FIX)
 * - DataSource: spring.datasource.* (표준)
 * - MyBatis: mybatis.* (표준)
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
    // MyBatis 스키마(Mapper 인터페이스 + XML + Store 구현체)
    implementation(project(":libs:db:mybatis:tc-db-mybatis-common-schema"))
    implementation(project(":libs:db:mybatis:tc-db-mybatis-site-schema"))

    // MyBatis 조립(기본 AutoConfiguration 사용)
    implementation(libs.mybatis.spring.boot.starter)

    // ✅ 하드코딩 제거
    runtimeOnly(libs.postgresql)

    testImplementation(libs.spring.boot.starter.test)
}

tasks.test {
    useJUnitPlatform()
}
