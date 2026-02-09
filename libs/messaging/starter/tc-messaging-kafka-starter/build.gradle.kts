/*
 * tc-messaging-kafka-starter
 *
 * 역할
 * - 앱에서 Kafka 메시징을 쉽게 조립하도록 AutoConfiguration 제공
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

    implementation(libs.spring.boot.starter)
    // API exposure: app code uses KafkaListener/KafkaTemplate/ProducerRecord directly.
    // Keep those on the consumer compile classpath via api.
    api(libs.spring.kafka)
    api(libs.kafka.clients)

    testImplementation(libs.spring.boot.starter.test)
}

tasks.test {
    useJUnitPlatform()
}
