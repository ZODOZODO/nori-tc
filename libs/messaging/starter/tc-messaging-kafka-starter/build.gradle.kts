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
    implementation(project(":libs:messaging:adapter:tc-messaging-kafka"))
    implementation(project(":libs:common:tc-common-kafka-consumer-runtime"))

    implementation(libs.spring.boot.starter)
    // KafkaTemplate, ProducerFactory, ConsumerFactory 등 Kafka 핵심 Bean 구성을 제공합니다.
    implementation(libs.spring.boot.starter.kafka)
    // Starter 내부 구현용 의존성으로만 사용합니다.
    implementation(libs.spring.kafka)
    implementation(libs.kafka.clients)

    testImplementation(libs.spring.boot.starter.test)
}

tasks.test {
    useJUnitPlatform()
}
