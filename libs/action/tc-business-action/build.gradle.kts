plugins {
    `java-library`
}

group = "com.nori.tc"
version = "0.0.1-SNAPSHOT"

java {
    // Business 플러그인 JAR 개발자를 위한 경량 SDK 모듈입니다.
    // 순수 Java이므로 Spring/Kafka/Jackson 의존성이 없습니다.
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

// 의존성 없음: 순수 Java 표준 라이브러리만 사용합니다.

tasks.test {
    useJUnitPlatform()
}
