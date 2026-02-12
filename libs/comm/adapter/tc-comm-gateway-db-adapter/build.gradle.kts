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

    // Spring 컴포넌트 컴파일 의존성 (@Service)
    compileOnly(libs.spring.boot)
    compileOnly(libs.spring.context)
}

tasks.test {
    useJUnitPlatform()
}