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
     * 비즈니스 코어는 도메인 모델을 중심으로 유스케이스를 구성합니다.
     */
    api(project(":libs:business:tc-business-domain"))

    /*
     * 공통 런타임 알고리즘(mailbox/kafka-processing/task-policy)을 코어에서 조합합니다.
     */
    implementation(project(":libs:common:tc-common-mailbox"))
    implementation(project(":libs:common:tc-common-kafka-processing"))
    implementation(project(":libs:common:tc-common-task-policy"))
    implementation(project(":libs:common:tc-common-ui-task-pipeline"))

    /*
     * 코어 엔진에서 사용하는 Kafka 타입/JSON 파서를 명시합니다.
     * - 기존 동작 호환을 위해 현재는 코어 내부 의존으로 유지합니다.
     */
    implementation(libs.kafka.clients)
    implementation(libs.jackson.databind)

    /*
     * Spring 어노테이션/라이프사이클 타입은 컴파일 시점 의존으로만 사용합니다.
     */
    compileOnly(libs.spring.boot)
    compileOnly(libs.spring.context)
    compileOnly(libs.jakarta.annotation.api)
    annotationProcessor(libs.spring.boot.configuration.processor)

    /*
     * 코어 테스트 의존성입니다.
     */
    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(libs.spring.context)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
