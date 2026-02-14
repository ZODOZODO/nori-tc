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
    /*
     * 비즈니스 도메인 모델은 DB 도메인 타입(ProtocolType, TcModel 등)을 사용합니다.
     * - 현재 단계에서는 기능 동등성 유지를 우선하므로 API 의존성으로 노출합니다.
     */
    api(project(":libs:db:tc-db-domain"))

    /*
     * 도메인 계층에서 로깅 인터페이스를 사용할 수 있도록 SLF4J API를 노출합니다.
     */
    api(libs.slf4j.api)

    /*
     * 도메인 테스트 기본 의존성입니다.
     */
    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
