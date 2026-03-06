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
     * 공통 로깅 모듈 의존성입니다.
     * - 프로젝트 공통 로깅 포맷/필터/MDC 유틸리티를 재사용합니다.
     */
    implementation(project(":libs:common:tc-common-logging"))

    /*
     * Business Core 스타터 의존성입니다.
     * - business domain/core/adapters를 앱에 한 번에 조립합니다.
     * - app 모듈은 실행 진입점과 외부 설정 결합 역할만 담당합니다.
     */
    implementation(project(":libs:business:starter:tc-business-core-starter"))

    /*
     * Kafka 인프라 조립 스타터 의존성입니다.
     * - 앱 경계에서 메시징 인프라 선택 책임을 명시적으로 갖도록 선언합니다.
     */
    implementation(project(":libs:messaging:starter:tc-messaging-kafka-starter"))

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
    mainClass.set("com.nori.tc.apps.businesscore.TcBusinessCoreApplication")
}
