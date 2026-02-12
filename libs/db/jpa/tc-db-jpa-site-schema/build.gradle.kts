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
