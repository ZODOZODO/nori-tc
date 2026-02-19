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
     * 공통 실행 파이프라인은 소비 런타임의 Ack/Retry 계약을 사용합니다.
     * 여러 앱에서 동일 계약을 재사용하므로 API로 노출합니다.
     */
    api(project(":libs:common:tc-common-mailbox"))
    api(project(":libs:common:tc-common-kafka-consumer-runtime"))
    api(libs.slf4j.api)

    /*
     * 단위 테스트 의존성입니다.
     */
    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
