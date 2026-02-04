/*
 * tc-db-mybatis-site-schema (FIX)
 *
 * 역할
 * - 사이트(현장) 확장 전용 MyBatis 스키마 모듈
 * - 현재는 비어있지만, 언제든 site 전용 Mapper/XML을 추가할 수 있도록 "연결"만 고정한다.
 *
 * 목표
 * - 하드코딩된 mybatis 버전을 제거하고 version catalog를 사용한다.
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
    /*
     * site schema에서도 domain 타입(enum 등)을 Mapper/DTO에서 사용할 수 있도록 유지
     */
    implementation(project(":libs:db:tc-db-domain"))

    /*
     * site schema에서도 @Param 등 MyBatis 타입이 컴파일에 필요할 수 있다.
     * - ✅ libs.mybatis.core로 통일
     */
    compileOnly(libs.mybatis.core)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.mybatis.core)
}

tasks.test {
    useJUnitPlatform()
}
