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
     * 게이트웨이 Kafka 처리 흐름은 공통 실행 모듈과 소비 런타임을 사용합니다.
     */
    implementation(project(":libs:common:tc-common-task-execution"))
    implementation(project(":libs:common:tc-common-mailbox"))
    implementation(project(":libs:common:tc-common-kafka-consumer-runtime"))

    /*
     * Kafka 어댑터/계약 의존성입니다.
     */
    implementation(project(":libs:messaging:tc-messaging-domain"))
    implementation(project(":libs:messaging:starter:tc-messaging-kafka-starter"))
    implementation(libs.spring.kafka)
    implementation(libs.kafka.clients)
    implementation(libs.jackson.databind)

    /*
     * Spring 컴포넌트 생명주기(@Component/@SmartLifecycle) 컴파일 의존성입니다.
     */
    compileOnly(libs.spring.boot)
    compileOnly(libs.spring.context)
    compileOnly(libs.jakarta.annotation.api)
}

tasks.test {
    useJUnitPlatform()
}
