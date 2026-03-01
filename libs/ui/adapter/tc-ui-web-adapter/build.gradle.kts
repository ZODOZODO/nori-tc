/*
 * tc-ui-web-adapter
 *
 * 역할
 * - Spring Security 설정과 REST 컨트롤러를 제공합니다.
 * - 인증 필터(UiTokenAuthenticationFilter)로 DB 세션 토큰 검증 후 SecurityContext 등록합니다.
 *
 * 주요 구성 (Phase 6에서 구현)
 * Spring Security:
 *   - UiTokenAuthenticationFilter : OncePerRequestFilter (token 추출 → 검증 → SecurityContext 등록)
 *   - UiSecurityConfig            : SecurityFilterChain (공개경로, CSRF/세션 stateless 설정)
 * REST 컨트롤러:
 *   - AuthController   → POST /auth/login, POST /auth/logout, GET /auth/me
 *   - EqpController    → POST /api/eqp, PUT /api/eqp/{id}, DELETE /api/eqp/{id},
 *                         POST /api/eqp/{id}/start, POST /api/eqp/{id}/end
 *   - AsyncResultController → GET /api/async/{traceId} (eqp_start/end Redis polling)
 *   - DlqController    → GET/DELETE /api/dlq/gateway/**, GET/DELETE /api/dlq/business/**
 */

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
     * Web 어댑터는 코어 UseCase를 호출합니다.
     */
    api(project(":libs:ui:tc-ui-core"))

    /*
     * Spring Web MVC: @RestController, DeferredResult, MockMvc 등
     * - Spring Boot 4.0 기준 spring-boot-starter-webmvc (구: spring-boot-starter-web)
     */
    implementation(libs.spring.boot.starter.webmvc)

    /*
     * Spring Security: OncePerRequestFilter, SecurityFilterChain, UsernamePasswordAuthenticationToken 등
     */
    implementation(libs.spring.boot.starter.security)

    /*
     * Actuator: /actuator/health 공개 경로 제공 (SecurityConfig에서 허용)
     */
    implementation(libs.spring.boot.starter.actuator)

    /*
     * Bean Validation: @Valid, @NotBlank 등 요청 파라미터 검증
     */
    implementation(libs.spring.boot.starter.validation)

    /*
     * Spring 설정 바인딩 컴파일 의존성
     */
    compileOnly(libs.spring.boot)
    compileOnly(libs.spring.context)
    compileOnly(libs.jakarta.annotation.api)
    annotationProcessor(libs.spring.boot.configuration.processor)

    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.security.test)
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
