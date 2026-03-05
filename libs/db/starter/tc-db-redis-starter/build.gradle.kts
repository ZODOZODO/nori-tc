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
    // Redis 연동을 위한 Spring Data Redis Starter
    implementation(libs.spring.boot.starter.data.redis)

    // Spring Data Redis 4.x에서 Jackson2 기반 JSON 직렬화기를 사용하기 위한 필수 의존성
    implementation(libs.jackson.databind)

    testImplementation(libs.spring.boot.starter.test)
}

tasks.test {
    useJUnitPlatform()
}
