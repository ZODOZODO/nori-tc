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
     * UI dedup 저장소 계약 타입을 구현하기 위해 공통 파이프라인 모듈을 사용합니다.
     */
    implementation(project(":libs:common:tc-common-ui-task-pipeline"))

    /*
     * Redis CRUD 공통 리포지토리(TcRedisCrudRepository) 제공 스타터입니다.
     */
    implementation(project(":libs:db:starter:tc-db-redis-starter"))

    /*
     * Spring 컴포넌트/프로퍼티 바인딩 컴파일 의존성입니다.
     */
    compileOnly(libs.spring.boot)
    compileOnly(libs.spring.context)
    compileOnly(libs.jakarta.annotation.api)
    annotationProcessor(libs.spring.boot.configuration.processor)
}

tasks.test {
    useJUnitPlatform()
}
