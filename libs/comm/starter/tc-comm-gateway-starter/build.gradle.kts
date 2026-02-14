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
    // 코어
    api(project(":libs:comm:tc-comm-gateway-core"))

    // 어댑터 묶음
    api(project(":libs:comm:adapter:tc-comm-gateway-netty-adapter"))
    api(project(":libs:comm:adapter:tc-comm-gateway-kafka-adapter"))
    api(project(":libs:comm:adapter:tc-comm-gateway-db-adapter"))
    api(project(":libs:comm:adapter:tc-comm-gateway-redis-adapter"))
    api(project(":libs:comm:adapter:tc-comm-gateway-plugin-adapter"))

    // AutoConfiguration 컴파일 의존성
    compileOnly(libs.spring.boot)
    compileOnly(libs.spring.boot.autoconfigure)
    compileOnly(libs.spring.context)
}

tasks.test {
    useJUnitPlatform()
}
