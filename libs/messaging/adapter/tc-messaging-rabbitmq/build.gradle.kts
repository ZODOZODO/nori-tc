/*
 * tc-messaging-rabbitmq (Adapter)
 *
 * 역할
 * - RabbitMQ 메시징 어댑터 (추후 구현)
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
    api(project(":libs:messaging:tc-messaging-core"))
}

tasks.test {
    useJUnitPlatform()
}
