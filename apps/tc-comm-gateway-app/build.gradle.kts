/*
 * tc-comm-gateway-app 빌드 스크립트
 *
 * 목적
 * - 게이트웨이 실행 애플리케이션을 조립하는 모듈입니다.
 * - DB/Kafka/Redis 구현은 개별 스타터 모듈에서 제공받고, 이 모듈은 조합만 담당합니다.
 * - 관계형 DB 스타터는 반드시 1개만 활성화해야 합니다.
 *
 * 참고
 * - Spring Boot 플러그인 + starter 의존성이 있어야 bootJar 실행 패키징이 가능합니다.
 * - DB 스타터를 2개 이상 활성화하면 빈 이름 충돌로 기동 단계에서 실패합니다.
 */

plugins {
    // Spring Boot 실행 패키징/런타임 플러그인
    alias(libs.plugins.spring.boot)

    // 의존성 버전 정렬(BOM) 플러그인
    alias(libs.plugins.spring.dependency.management)

    // Java 컴파일 플러그인
    java
}

group = "com.nori.tc"
version = "0.0.1-SNAPSHOT"

java {
    // Java 21 툴체인 고정
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    /*
     * Spring Boot 기본 의존성
     *
     * - SpringApplication, @SpringBootApplication 컴파일에 필요합니다.
     * - 웹 서버를 자동으로 포함한다는 의미는 아닙니다.
     */
    implementation(libs.spring.boot.starter)

    /*
     * 공통 로깅 모듈
     *
     * - 서비스 공통 로그 패턴(MDC 포함), 롤링 정책, 컨텍스트 전파를 제공합니다.
     */
    implementation(project(":libs:common:tc-common-logging"))

    /*
     * Comm Gateway 핵심 스타터
     *
     * - 게이트웨이 코어 기능과 Netty/Kafka/DB/Redis 연동 구성을 함께 제공합니다.
     * - 앱 모듈은 해당 스타터를 조합하고 상세 동작은 properties/yaml로 제어합니다.
     */
    implementation(project(":libs:comm:starter:tc-comm-gateway-starter"))

    /*
     * Kafka infra assembly starter.
     *
     * Why this lives in the app layer:
     * - `tc-comm-gateway-kafka-adapter` depends on Kafka APIs only (`spring-kafka`).
     * - Boot Kafka auto-configuration (`KafkaTemplate`, `ProducerFactory`, etc.)
     *   is owned by the messaging starter.
     * - The app explicitly selects the messaging infra starter to keep
     *   composition responsibility at the application boundary.
     */
    implementation(project(":libs:messaging:starter:tc-messaging-kafka-starter"))

    /*
     * DB 스타터 (정확히 1개 선택)
     *
     * - 기술 스택(JPA/MyBatis)과 벤더(Postgres/MySQL/MSSQL/Oracle)를 선택합니다.
     * - 전환 시 기존 활성화 항목을 주석 처리하고 대상 항목 1개만 활성화하세요.
     */

    // 기본값: PostgreSQL + JPA
    implementation(project(":libs:db:starter:tc-db-postgres-jpa-starter"))

    // PostgreSQL + MyBatis
    // implementation(project(":libs:db:starter:tc-db-postgres-mybatis-starter"))

    // MySQL + JPA
    // implementation(project(":libs:db:starter:tc-db-mysql-jpa-starter"))

    // MySQL + MyBatis
    // implementation(project(":libs:db:starter:tc-db-mysql-mybatis-starter"))

    // MSSQL + JPA
    // implementation(project(":libs:db:starter:tc-db-mssql-jpa-starter"))

    // MSSQL + MyBatis
    // implementation(project(":libs:db:starter:tc-db-mssql-mybatis-starter"))

    // Oracle + JPA
    // implementation(project(":libs:db:starter:tc-db-oracle-jpa-starter"))

    // Oracle + MyBatis
    // implementation(project(":libs:db:starter:tc-db-oracle-mybatis-starter"))

    // Redis/Netty/Comm are assembled by comm-gateway-starter.
    // Kafka Boot auto-configuration is selected explicitly via tc-messaging-kafka-starter.

    /*
     * 테스트 의존성
     */
    testImplementation(libs.spring.boot.starter.test)
}

tasks.test {
    useJUnitPlatform()
}

/*
 * 메인 클래스 명시
 * - bootJar 패키징 시 진입점을 명확히 고정합니다.
 */
springBoot {
    mainClass.set("com.nori.tc.apps.commgateway.TcCommGatewayApplication")
}
