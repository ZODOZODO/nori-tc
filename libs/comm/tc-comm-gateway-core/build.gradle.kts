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
     * 공통 로깅 스타터
     */
    implementation(project(":libs:log:starter:tc-log-starter"))

    /*
     * 공통 메일박스 스케줄러
     * - eqpId 단위 순차 실행(in-flight=1) 알고리즘을 gateway/business-core가 함께 재사용합니다.
     */
    implementation(project(":libs:common:tc-common-mailbox"))

    /*
     * Kafka 유틸리티
     * - 파티션/샤드 계산 및 메타데이터 조회 시 사용
     */
    implementation(libs.kafka.clients)

    /*
     * Spring 컴파일 의존성
     * - @Component, @ConfigurationProperties, @PostConstruct 컴파일 목적
     * - 실제 런타임 의존성은 상위 starter/app에서 제공합니다.
     */
    compileOnly(libs.spring.boot)
    compileOnly(libs.spring.context)
    compileOnly(libs.jakarta.annotation.api)
    annotationProcessor(libs.spring.boot.configuration.processor)
}

tasks.test {
    useJUnitPlatform()
}
