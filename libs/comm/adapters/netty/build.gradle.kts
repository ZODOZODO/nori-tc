/*
 * tc-comm-gateway-netty-adapter
 *
 * 역할
 * - Netty TCP 서버/클라이언트 부팅
 * - 채널 핸들러, 바인딩, UNBOUND/BOUND 처리
 *
 * 의존
 * - core 모듈(런타임/설정/샤딩)
 * - Netty
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
    api(project(":libs:comm-gateway:tc-comm-gateway-core"))

    implementation(libs.netty.all)

    // Spring 컴파일 의존 (@Component/@Service)
    compileOnly(libs.spring.boot)
    compileOnly(libs.spring.context)
    compileOnly(libs.slf4j.api)
    compileOnly(libs.jakarta.annotation.api)
}

tasks.test {
    useJUnitPlatform()
}
