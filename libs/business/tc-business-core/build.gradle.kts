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
    // Business 플러그인 SDK: AbstractXxxActionExecutor, @TcAction, TcActionContext SPI 계약
    api("com.nori.tc:nori-tc-business-action:0.0.1-SNAPSHOT")

    /*
     * 비즈니스 코어는 도메인 모델을 중립 계층으로 노출합니다.
     */
    api(project(":libs:business:tc-business-domain"))

    /*
     * 공통 로깅 유틸리티 의존성입니다.
     * - BusinessLogContext를 통해 MDC(eqpId/traceId) 스코프를 제어할 때 사용합니다.
     * - 코어 계층은 스타터가 아닌 공통 유틸리티 모듈에만 의존하여 결합도를 낮춥니다.
     */
    implementation(project(":libs:common:tc-common-logging"))

    /*
     * 공통 실행 알고리즘(mailbox/consumer-runtime/task-execution)을 조합합니다.
     */
    implementation(project(":libs:common:tc-common-mailbox"))
    /*
     * ACK/커밋 추적 알고리즘은 Kafka SDK 비종속 중립 모듈을 직접 사용합니다.
     * 재시도 정책(FixedRetryPolicy/RetryPolicy)도 동일 중립 모듈 계약으로 통일합니다.
     */
    implementation(project(":libs:common:tc-common-consumer-runtime"))
    implementation(project(":libs:common:tc-common-task-execution"))

    /*
     * 코어 엔진은 Kafka SDK 타입을 직접 사용하지 않으므로 Kafka 클라이언트 의존성을 두지 않습니다.
     * 브로커 전용 타입 변환/커밋 적용 책임은 Kafka adapter/runtime 계층으로 이동합니다.
     */
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
