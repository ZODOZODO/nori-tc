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
    // API exposure: core/domain types are used directly by apps.
    api(project(":libs:db:tc-db-core"))
    api(project(":libs:db:tc-db-domain"))
    // MyBatis 스키마(Mapper 인터페이스 + XML + Store 구현체)
    implementation(project(":libs:db:mybatis:tc-db-mybatis-common-schema"))
    implementation(project(":libs:db:mybatis:tc-db-mybatis-site-schema"))

    // MyBatis 조립(기본 AutoConfiguration 사용)
    implementation(libs.mybatis.spring.boot.starter)

    // ✅ 하드코딩 제거
    runtimeOnly(libs.oracle.ojdbc11)

    testImplementation(libs.spring.boot.starter.test)
}

tasks.test {
    useJUnitPlatform()
}
