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
     * 코어/도메인 계약
     * - 게이트웨이 핵심 로직이 의존하는 공통 모델과 파이프라인 계약
     */
    api(project(":libs:comm:tc-comm-core"))
    api(project(":libs:comm:tc-comm-domain"))
    api(project(":libs:comm:tc-comm-hsms"))
    api(project(":libs:comm:tc-comm-socket"))

    /*
     * 공통 로깅 유틸리티
     * - 코어 계층은 스타터가 아닌 공통 유틸리티 모듈에만 의존하여 결합도를 낮춥니다.
     */
    implementation(project(":libs:common:tc-common-logging"))
    api(libs.micrometer.core)

    /*
     * 공통 메일박스 스케줄러
     * - eqpId 단위 순차 실행(in-flight=1) 알고리즘을 gateway/business-core가 함께 재사용합니다.
     */
    implementation(project(":libs:common:tc-common-mailbox"))

    /*
     * 코어 계층의 샤드 계산은 Kafka 호환 알고리즘을 순수 Java로 유지하므로
     * Kafka SDK 의존성을 직접 갖지 않습니다.
     */

    /*
     * Spring 컴파일 의존성
     * - @Component, @ConfigurationProperties, @PostConstruct 컴파일 목적
     * - 실제 런타임 의존성은 상위 starter/app에서 제공합니다.
     */
    compileOnly(libs.spring.boot)
    compileOnly(libs.spring.context)
    compileOnly(libs.jakarta.annotation.api)
    annotationProcessor(libs.spring.boot.configuration.processor)

    /*
     * 아키텍처 가드 테스트(코어 계층 Kafka import 금지 검증)용 JUnit 의존성입니다.
     * 런타임 산출물에는 포함되지 않으며 테스트 스코프에만 제한됩니다.
     */
    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
