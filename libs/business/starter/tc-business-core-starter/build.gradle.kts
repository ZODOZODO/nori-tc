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
     * Starter는 business 계층(core + adapters)을 앱에 한 번에 조립하기 위한 진입점입니다.
     */
    api(project(":libs:business:tc-business-core"))
    api(project(":libs:business:adapter:tc-business-db-adapter"))
    api(project(":libs:business:adapter:tc-business-kafka-adapter"))
    api(project(":libs:business:adapter:tc-business-plugin-adapter"))
    api(project(":libs:business:adapter:tc-business-redis-adapter"))

    /*
     * Starter 설정 클래스에서 직접 참조하는 공통 타입 의존성입니다.
     * - UI Task Pipeline 타입
     * - RetryPolicy 타입
     * - Kafka UI 메시지 계약 타입
     * - ObjectMapper 타입
     */
    implementation(project(":libs:common:tc-common-kafka-processing"))
    implementation(project(":libs:common:tc-common-ui-task-pipeline"))
    implementation(project(":libs:messaging:starter:tc-messaging-kafka-starter"))
    implementation(libs.jackson.databind)

    /*
     * AutoConfiguration 클래스 컴파일 의존성입니다.
     */
    compileOnly(libs.spring.boot)
    compileOnly(libs.spring.boot.autoconfigure)
    compileOnly(libs.spring.context)
}

tasks.test {
    useJUnitPlatform()
}
