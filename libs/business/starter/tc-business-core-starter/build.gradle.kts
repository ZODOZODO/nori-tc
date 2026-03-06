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
     * Starter는 business 코어와 핵심 어댑터를 앱에 한 번에 조립합니다.
     */
    api(project(":libs:business:tc-business-core"))
    api(project(":libs:business:adapter:tc-business-db-adapter"))
    api(project(":libs:business:adapter:tc-business-kafka-adapter"))
    api(project(":libs:business:adapter:tc-business-plugin-adapter"))
    api(project(":libs:business:adapter:tc-business-redis-adapter"))

    /*
     * AutoConfiguration에서 직접 사용하는 공통 계약/구현입니다.
     */
    /*
     * Starter 구성 코드에서 직접 사용하는 공통 실행/재시도/계약 타입 의존성입니다.
     * - FixedRetryPolicy 등 재시도 계약은 Kafka SDK 비종속 중립 consumer-runtime 모듈에서 제공합니다.
     * - KafkaUiTaskMessage는 분리된 tc-messaging-kafka-contract 를 직접 참조합니다.
     */
    implementation(project(":libs:common:tc-common-consumer-runtime"))
    implementation(project(":libs:common:tc-common-task-execution"))
    implementation(project(":libs:messaging:kafka:tc-messaging-kafka-contract"))
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
