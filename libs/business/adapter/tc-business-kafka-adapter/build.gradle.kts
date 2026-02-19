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
     * Kafka 어댑터는 business core 포트를 구현하고 코어 유스케이스를 호출합니다.
     */
    api(project(":libs:business:tc-business-core"))

    /*
     * 공통 실행 파이프라인/소비 런타임을 사용하여
     * Kafka 메시지 처리 흐름(라우팅, 재시도, 응답 발행)을 일관되게 구성합니다.
     */
    implementation(project(":libs:common:tc-common-task-execution"))
    implementation(project(":libs:common:tc-common-kafka-consumer-runtime"))

    /*
     * Kafka 메시지 계약 및 클라이언트 의존성입니다.
     */
    implementation(project(":libs:messaging:starter:tc-messaging-kafka-starter"))
    implementation(libs.spring.kafka)
    implementation(libs.kafka.clients)
    implementation(libs.jackson.databind)

    /*
     * Spring 컴포넌트/프로퍼티 바인딩 의존성입니다.
     */
    compileOnly(libs.spring.boot)
    compileOnly(libs.spring.boot.autoconfigure)
    compileOnly(libs.spring.context)
    compileOnly(libs.jakarta.annotation.api)
    annotationProcessor(libs.spring.boot.configuration.processor)

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
