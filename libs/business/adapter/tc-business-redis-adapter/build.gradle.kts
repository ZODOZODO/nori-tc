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
     * Redis 어댑터는 business core 포트를 구현합니다.
     */
    api(project(":libs:business:tc-business-core"))

    /*
     * UI 중복제거 저장소 계약(KafkaTaskDeduplicationStore)은 공통 실행 모듈에 있습니다.
     */
    implementation(project(":libs:common:tc-common-task-execution"))

    /*
     * Redis 공통 CRUD 레이어를 제공합니다.
     */
    implementation(project(":libs:db:starter:tc-db-redis-starter"))

    /*
     * Spring 컴포넌트/프로퍼티 바인딩 의존성입니다.
     */
    compileOnly(libs.spring.boot)
    compileOnly(libs.spring.context)
    compileOnly(libs.jakarta.annotation.api)
    annotationProcessor(libs.spring.boot.configuration.processor)
}

tasks.test {
    useJUnitPlatform()
}
