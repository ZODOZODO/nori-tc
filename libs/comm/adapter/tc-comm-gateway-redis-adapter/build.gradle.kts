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

    // Redis Starter (TcRedisCrudRepository 제공)
    implementation(project(":libs:db:starter:tc-db-redis-starter"))

    // Spring 컴포넌트 컴파일 의존성 (@Component/@Service)
    compileOnly(libs.spring.boot)
    compileOnly(libs.spring.context)
}

tasks.test {
    useJUnitPlatform()
}