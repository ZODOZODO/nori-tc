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
     * 공통 실행 파이프라인은 소비 런타임의 ACK/재시도 계약을 사용합니다.
     * Kafka SDK 비종속 공용 계약으로 정리했으므로 중립 consumer-runtime 모듈을 API로 노출합니다.
     */
    api(project(":libs:common:tc-common-consumer-runtime"))
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
