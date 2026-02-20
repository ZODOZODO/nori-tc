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
     * 비즈니스 코어는 도메인 모델을 중립 계층으로 노출합니다.
     */
    api(project(":libs:business:tc-business-domain"))

    /*
     * 공통 로깅 스타터 의존성입니다.
     * - BusinessLogContext를 통해 MDC(eqpId/traceId) 스코프를 제어할 때 사용합니다.
     * - 앱/어댑터 계층이 코어 API만 의존해도 동일한 로깅 유틸리티를 재사용할 수 있습니다.
     */
    implementation(project(":libs:log:starter:tc-log-starter"))

    /*
     * 공통 실행 알고리즘(mailbox/consumer-runtime/task-execution)을 조합합니다.
     */
    implementation(project(":libs:common:tc-common-mailbox"))
    implementation(project(":libs:common:tc-common-kafka-consumer-runtime"))
    implementation(project(":libs:common:tc-common-task-execution"))

    /*
     * 코어 엔진에서 사용하는 Kafka/JSON 의존성입니다.
     */
    implementation(libs.kafka.clients)
    implementation(libs.jackson.databind)

    /*
     * Spring 생명주기/설정 바인딩 컴파일 의존성입니다.
     */
    compileOnly(libs.spring.boot)
    compileOnly(libs.spring.context)
    compileOnly(libs.jakarta.annotation.api)
    annotationProcessor(libs.spring.boot.configuration.processor)

    /*
     * 단위 테스트 의존성입니다.
     */
    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(libs.spring.context)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
