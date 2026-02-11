/*
 * tc-comm-gateway-redis-adapter
 *
 * 역할
 * - DLQ/Quarantine Redis 저장
 * - 런타임 상태 저장(필요 시)
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

    // Redis Starter (TcRedisCrudRepository 제공)
    implementation(project(":libs:db:starter:tc-db-redis-starter"))

    // Spring 컴파일 의존 (@Component/@Service)
    compileOnly(libs.spring.boot)
    compileOnly(libs.spring.context)
}

tasks.test {
    useJUnitPlatform()
}
