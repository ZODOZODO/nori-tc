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
    implementation(libs.spring.kafka)
    implementation(libs.kafka.clients)

    testImplementation(libs.spring.boot.starter.test)
}

tasks.test {
    useJUnitPlatform()
}
