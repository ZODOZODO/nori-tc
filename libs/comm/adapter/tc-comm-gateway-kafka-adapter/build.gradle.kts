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
    api(project(":libs:comm:tc-comm-gateway-core"))

    /*
     * SocketTypeHandler / SocketTypeEncodeResult / SocketFrame 등 gateway action SPI 계약을 직접 참조합니다.
     */
    implementation(project(":libs:action:tc-gateway-action"))

    /*
     * 게이트웨이 Kafka 처리 흐름은 공통 실행 모듈과 소비 런타임을 사용합니다.
     */
    implementation(project(":libs:common:tc-common-task-execution"))
    implementation(project(":libs:common:tc-common-mailbox"))
    /*
     * 공통 Task 실행 파이프라인/ACK/재시도 계약은 Kafka SDK 비종속 중립 consumer-runtime 모듈을 사용합니다.
     */
    implementation(project(":libs:common:tc-common-consumer-runtime"))

    /*
     * Kafka 어댑터/계약 의존성입니다.
     */
    implementation(project(":libs:messaging:tc-messaging-domain"))
    /*
     * Starter 내부 runtime/contract 패키지 의존을 제거하고,
     * 분리된 Kafka 계약/런타임 모듈을 직접 참조합니다.
     */
    implementation(project(":libs:messaging:kafka:tc-messaging-kafka-contract"))
    implementation(project(":libs:messaging:kafka:tc-messaging-kafka-runtime"))
    implementation(libs.spring.kafka)
    implementation(libs.jackson.databind)

    /*
     * Spring 컴포넌트 생명주기(@Component/@SmartLifecycle) 컴파일 의존성입니다.
     */
    compileOnly(libs.spring.boot)
    compileOnly(libs.spring.context)
    compileOnly(libs.jakarta.annotation.api)

    /*
     * 아키텍처 가드 테스트(starter 패키지 import 금지 검증)용 JUnit 의존성입니다.
     */
    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
