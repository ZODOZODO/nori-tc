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
     * 공통 mailbox 모듈은 알고리즘/자료구조 중심 모듈입니다.
     * - 특정 프레임워크 의존성을 최소화하기 위해 표준 라이브러리 위주로 구성합니다.
     */
    api(libs.slf4j.api)

    /*
     * 단위 테스트 의존성입니다.
     * - Spring 컨텍스트 없이 순수 알고리즘 테스트만 수행하기 위해 JUnit 5만 사용합니다.
     */
    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
