/*
 * tc-messaging-kafka-starter
 *
 * 역할
 * - 애플리케이션에서 Kafka 메시징을 쉽게 조립하도록 AutoConfiguration을 제공합니다.
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
     * Starter의 역할은 "조립"입니다.
     * - 실제 Kafka 발행 구현체는 adapter 모듈에서 제공
     * - 공통 runtime/contract 재사용 코드는 별도 모듈로 분리되어 Starter에 포함하지 않음
     */
    implementation(project(":libs:messaging:adapter:tc-messaging-kafka"))

    implementation(libs.spring.boot.starter)
    // KafkaTemplate, ProducerFactory, ConsumerFactory 등 Kafka 핵심 Bean 구성을 제공합니다.
    implementation(libs.spring.boot.starter.kafka)
    // Starter 내부 구현용 의존성으로만 사용합니다.

    testImplementation(libs.spring.boot.starter.test)
    /*
     * 일부 실행 환경에서 JUnit Platform launcher가 starter test 런타임에 누락될 수 있으므로
     * 아키텍처 가드 테스트의 안정적인 실행을 위해 명시적으로 추가합니다.
     */
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
