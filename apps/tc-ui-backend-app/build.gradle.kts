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
     * Web MVC 스타터입니다.
     * - Spring Boot 4.0 기준 REST API 처리를 위한 DispatcherServlet, Jackson 직렬화 등을 제공합니다.
     * - Spring Boot 3.x 의 spring-boot-starter-web 에서 이름이 변경되었습니다.
     */
    implementation(libs.spring.boot.starter.webmvc)

    /*
     * Actuator 스타터입니다.
     * - /actuator/health 등 운영 모니터링 엔드포인트를 제공합니다.
     */
    implementation(libs.spring.boot.starter.actuator)

    /*
     * Validation 스타터입니다.
     * - @Valid, @NotNull 등 Bean Validation(Jakarta Validation) 기능을 제공합니다.
     */
    implementation(libs.spring.boot.starter.validation)

    /*
     * Spring Security 스타터입니다.
     * - 인증(Authentication) 및 인가(Authorization) 기능을 제공합니다.
     * - 기본적으로 모든 엔드포인트에 인증이 활성화되므로 SecurityConfig 작성이 필요합니다.
     */
    implementation(libs.spring.boot.starter.security)

    /*
     * 공통 로그 스타터 의존성입니다.
     * - 프로젝트 공통 로깅 포맷과 MDC 구성을 재사용합니다.
     */
    implementation(project(":libs:log:starter:tc-log-starter"))

    /*
     * UI Backend 핵심 스타터입니다.
     * - tc-ui-domain/core + 어댑터 4종(web/kafka/db/redis)을 한 번에 조립합니다.
     * - TcUiBackendAutoConfiguration이 @ComponentScan("com.nori.tc.ui")로
     *   UI Backend 전체 빈을 Spring 컨텍스트에 자동 등록합니다.
     * - Kafka Boot auto-configuration(KafkaTemplate 등)은 starter 내부에서 포함합니다.
     */
    implementation(project(":libs:ui:starter:tc-ui-backend-starter"))

    /*
     * DB 스타터 의존성입니다.
     * - 초기 정책( DB only )에 따라 PostgreSQL + JPA 조합을 기본값으로 사용합니다.
     */
    implementation(project(":libs:db:starter:tc-db-postgres-jpa-starter"))

    /*
     * Spring Security 테스트 의존성입니다.
     * - @WithMockUser 등 Security 관련 테스트 유틸리티를 제공합니다.
     */
    testImplementation(libs.spring.boot.starter.security.test)

    /*
     * Spring Boot 4.x Web MVC 테스트 의존성입니다.
     * - Spring Boot 4.x에서 @WebMvcTest가 spring-boot-webmvc-test 모듈로 분리되었습니다.
     * - 기존 org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest가
     *   org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest로 이동되었습니다.
     */
    testImplementation(libs.spring.boot.starter.webmvc.test)

    /*
     * Jackson 2.x 테스트 컴파일 의존성입니다.
     * - UiTokenAuthenticationFilter가 com.fasterxml.jackson.databind.ObjectMapper를
     *   생성자 주입으로 요구하므로, 테스트 컨텍스트에서 해당 빈을 제공하기 위해 필요합니다.
     * - 런타임에는 tc-ui-web-adapter의 implementation 의존성으로 자동 제공되지만,
     *   테스트 컴파일 시에는 명시적으로 선언해야 합니다.
     */
    testImplementation(libs.jackson.databind)

    /*
     * Spring Boot 통합 테스트 의존성입니다.
     * - JUnit 5, Mockito 등 기본 테스트 환경을 제공합니다.
     */
    testImplementation(libs.spring.boot.starter.test)
}

tasks.test {
    useJUnitPlatform()
}

springBoot {
    mainClass.set("com.nori.tc.apps.uibackend.TcUiBackendApplication")
}
