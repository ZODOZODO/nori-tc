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
     * Business DLQ 엔트리 직렬화 계약입니다.
     * - RedisBusinessDlqEntry 클래스가 tc-db-domain에 위치합니다.
     * - tc-ui-redis-adapter가 동일 클래스를 역직렬화할 수 있도록 공유 도메인에서 참조합니다.
     */
    implementation(project(":libs:db:tc-db-domain"))

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
