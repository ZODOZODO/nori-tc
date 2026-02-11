/*
 * tc-comm-gateway-db-adapter
 *
 * 역할
 * - tc_eqp 및 관련 테이블 조회
 * - 런타임 장비 정보 제공(EquipmentInfoProvider 구현)
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
    api(project(":libs:comm-gateway:tc-comm-gateway-core"))

    implementation(project(":libs:db:tc-db-core"))
    implementation(project(":libs:db:tc-db-domain"))

    // Spring 컴파일 의존 (@Service)
    compileOnly(libs.spring.boot)
    compileOnly(libs.spring.context)
}

tasks.test {
    useJUnitPlatform()
}
