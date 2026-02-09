/*
 * tc-messaging-domain
 *
 * 역할
 * - 메시징 공통 도메인 모델(기술 중립)을 정의
 */

plugins {
    `java-library`
}

group = "com.nori.tc"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    // 외부 의존성 없음
}

tasks.test {
    useJUnitPlatform()
}
