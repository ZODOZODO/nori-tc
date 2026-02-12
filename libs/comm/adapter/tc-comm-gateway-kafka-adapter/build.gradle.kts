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

    // Kafka adapter/contract
    implementation(project(":libs:messaging:starter:tc-messaging-kafka-starter"))
    implementation(libs.spring.kafka)
    implementation(libs.kafka.clients)
    implementation(libs.jackson.databind)

    // Spring 컴포넌트 컴파일 의존성 (@Component/@SmartLifecycle)
    compileOnly(libs.spring.boot)
    compileOnly(libs.spring.context)
    compileOnly(libs.jakarta.annotation.api)
}

tasks.test {
    useJUnitPlatform()
}