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

    // 상세 설명:
    // - @ConfigurationProperties 어노테이션은 spring-boot 모듈에 포함되어 있습니다.
    // - Adapter 모듈은 Boot Starter를 직접 쓰지 않으므로, 컴파일 시에만 필요한 의존성을
    //   compileOnly로 추가해서 불필요한 런타임 전이 의존성을 줄였습니다.
    // - 설정 메타데이터 생성을 위해 configuration-processor도 annotationProcessor로 등록합니다.
    compileOnly(libs.spring.boot)
    annotationProcessor(libs.spring.boot.configuration.processor)
}

tasks.test {
    useJUnitPlatform()
}
