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
    /*
     * 공통 retry 정책 인터페이스를 재사용합니다.
     * - RetryPolicy/RetryDecision 타입이 public API에 노출되므로 api 의존성을 사용합니다.
     */
    api(project(":libs:common:tc-common-kafka-processing"))
    api(libs.slf4j.api)

    /*
     * 테스트 의존성입니다.
     */
    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
