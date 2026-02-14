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
     * Kafka 어댑터는 코어 포트를 구현하고, 코어 유스케이스를 호출합니다.
     */
    api(project(":libs:business:tc-business-core"))

    /*
     * UI task 공통 파이프라인과 재시도 정책 공통 모듈을 사용합니다.
     */
    implementation(project(":libs:common:tc-common-ui-task-pipeline"))
    implementation(project(":libs:common:tc-common-kafka-processing"))

    /*
     * Kafka 메시지 계약/클라이언트 의존성입니다.
     */
    implementation(project(":libs:messaging:starter:tc-messaging-kafka-starter"))
    implementation(libs.spring.kafka)
    implementation(libs.kafka.clients)
    implementation(libs.jackson.databind)

    /*
     * Spring 컴포넌트/프로퍼티 바인딩 어노테이션 컴파일 의존성입니다.
     */
    compileOnly(libs.spring.boot)
    compileOnly(libs.spring.boot.autoconfigure)
    compileOnly(libs.spring.context)
    compileOnly(libs.jakarta.annotation.api)
    annotationProcessor(libs.spring.boot.configuration.processor)

    /*
     * Kafka 어댑터 테스트 의존성입니다.
     */
    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
