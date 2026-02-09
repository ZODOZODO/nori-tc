/*
 * tc-messaging-kafka (Adapter)
 *
 * 역할
 * - MessagePublisherPort를 Kafka로 구현
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

    implementation(libs.spring.kafka)
    implementation(libs.kafka.clients)
}

tasks.test {
    useJUnitPlatform()
}
