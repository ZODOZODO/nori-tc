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
    // API exposure for apps that only depend on this starter.
    // - tc-db-core: store interfaces, PageRequest, exceptions
    // - tc-db-domain: domain records used by gateway app
    api(project(":libs:db:tc-db-core"))
    api(project(":libs:db:tc-db-domain"))
    // JPA 스키마(엔티티/리포지토리/Store 구현체)
    implementation(project(":libs:db:jpa:tc-db-jpa-common-schema"))
    implementation(project(":libs:db:jpa:tc-db-jpa-site-schema"))

    // ✅ 하드코딩 제거
    implementation(libs.spring.boot.starter.data.jpa)
    runtimeOnly(libs.postgresql)

    testImplementation(libs.spring.boot.starter.test)
}

tasks.test {
    useJUnitPlatform()
}
