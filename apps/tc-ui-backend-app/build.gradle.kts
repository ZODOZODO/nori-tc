plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    java
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
     * Spring Boot 기본 실행 의존성입니다.
     * - SpringApplication 실행과 AutoConfiguration 활성화에 필요합니다.
     */
    implementation(libs.spring.boot.starter)

    /*
     * 공통 로그 스타터 의존성입니다.
     * - 프로젝트 공통 로깅 포맷과 MDC 구성을 재사용합니다.
     */
    implementation(project(":libs:log:starter:tc-log-starter"))

    /*
     * DB 스타터 의존성입니다.
     * - 초기 정책( DB only )에 따라 PostgreSQL + JPA 조합을 기본값으로 사용합니다.
     */
    implementation(project(":libs:db:starter:tc-db-postgres-jpa-starter"))

    testImplementation(libs.spring.boot.starter.test)
}

tasks.test {
    useJUnitPlatform()
}

springBoot {
    mainClass.set("com.nori.tc.apps.uibackend.TcUiBackendApplication")
}
