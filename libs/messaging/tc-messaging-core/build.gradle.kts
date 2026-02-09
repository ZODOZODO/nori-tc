/*
 * tc-messaging-core
 *
 * 역할
 * - 메시징 포트/요청 모델 정의(기술 중립)
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
    api(project(":libs:messaging:tc-messaging-domain"))
}

tasks.test {
    useJUnitPlatform()
}
