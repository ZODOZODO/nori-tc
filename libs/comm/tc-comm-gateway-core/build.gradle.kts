/*
 * tc-comm-gateway-core
 *
 * 역할
 * - 게이트웨이 런타임 코어 로직(큐/스케줄/처리/상태)
 * - Kafka 파티션 계산/샤드 소유 판단
 * - 공통 설정 프로퍼티(@ConfigurationProperties)
 *
 * 주의
 * - 인프라(Netty/Kafka Consumer/Redis/DB) 직접 구현은 포함하지 않음
 * - Spring Boot는 @Component/@ConfigurationProperties 컴파일을 위한 최소 의존만 둔다
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
     * =========================
     * Core/Domain (공통 계약)
     * =========================
     */
    api(project(":libs:comm:tc-comm-core"))
    api(project(":libs:comm:tc-comm-domain"))
    api(project(":libs:comm:tc-comm-hsms"))
    api(project(":libs:comm:tc-comm-socket"))

    /*
     * =========================
     * Kafka Utils (파티션 계산)
     * =========================
     */
    implementation(libs.kafka.clients)

    /*
     * =========================
     * Spring 컴파일 의존
     * =========================
     * - @Component/@ConfigurationProperties 컴파일을 위한 최소 의존
     * - 런타임은 앱(starter)에서 제공
     */
    compileOnly(libs.spring.boot)
    compileOnly(libs.spring.context)
    compileOnly(libs.slf4j.api)
    compileOnly(libs.jakarta.annotation.api)
    annotationProcessor(libs.spring.boot.configuration.processor)
}

tasks.test {
    useJUnitPlatform()
}
