/*
 * tc-log-starter
 *
 * 역할
 * - 모든 앱에서 동일한 로그 패턴/출력 정책을 적용
 * - logback-spring.xml 을 제공해 표준 로그 포맷을 강제
 *
 * 사용
 * - 앱 모듈에서 implementation(project(":libs:log:starter:tc-log-starter"))
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
    // 로그 설정 파일(logback-spring.xml)만 제공하므로 별도 런타임 의존은 필요 없음
    // (spring-boot-starter가 logback을 포함하고 있음)
    compileOnly(libs.spring.boot)
    compileOnly(libs.spring.boot.autoconfigure)
    compileOnly(libs.slf4j.api)
}

tasks.test {
    useJUnitPlatform()
}
