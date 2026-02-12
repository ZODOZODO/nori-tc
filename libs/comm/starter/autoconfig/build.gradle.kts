/*
 * tc-comm-gateway-starter
 *
 * 역할
 * - 게이트웨이 구성(코어 + 어댑터)을 한 번에 가져오는 스타터
 * - 앱 모듈은 이 스타터만 의존하면 구동 가능
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
    // 코어
    api(project(":libs:comm-gateway:tc-comm-gateway-core"))

    // 어댑터 묶음
    api(project(":libs:comm-gateway:adapter:tc-comm-gateway-netty-adapter"))
    api(project(":libs:comm-gateway:adapter:tc-comm-gateway-kafka-adapter"))
    api(project(":libs:comm-gateway:adapter:tc-comm-gateway-db-adapter"))
    api(project(":libs:comm-gateway:adapter:tc-comm-gateway-redis-adapter"))

    // AutoConfiguration 동작을 위한 spring-boot 의존
    compileOnly(libs.spring.boot)
    compileOnly(libs.spring.boot.autoconfigure)
    compileOnly(libs.spring.context)
}

tasks.test {
    useJUnitPlatform()
}
