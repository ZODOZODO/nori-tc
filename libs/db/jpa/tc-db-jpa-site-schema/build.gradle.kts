/*
 * tc-db-jpa-site-schema (FIX)
 *
 * 역할
 * - "사이트(현장) 확장" 전용 JPA 스키마 모듈
 * - 현재는 비어있지만, 추후 사이트별 테이블/컬럼/요구사항이 생기면
 *   Entity/Repository를 이 모듈에 추가한다.
 *
 * 목표
 * - 하드코딩된 starter-test 좌표 제거 → Version Catalog로 일원화
 *
 * 원칙
 * - common-schema와 책임을 섞지 않는다.
 * - starter에서 common + site 둘 다 스캔하도록 조립한다.
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
    // site-schema에서도 domain 타입(enum 등)을 엔티티에서 사용할 수 있도록 유지
    implementation(project(":libs:db:tc-db-domain"))

    // 추후 엔티티/리포지토리를 추가할 때 바로 사용할 수 있도록 기본 의존성은 포함
    implementation(libs.spring.boot.starter.data.jpa)

    // ✅ 하드코딩 제거 → catalog alias 사용
    testImplementation(libs.spring.boot.starter.test)
}

tasks.test {
    useJUnitPlatform()
}
