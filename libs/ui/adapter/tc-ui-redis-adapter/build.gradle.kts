/*
 * tc-ui-redis-adapter
 *
 * 역할
 * - Gateway Redis(6379)와 Business Redis(6380)에 동시 접속하는 어댑터입니다.
 * - spring.data.redis.* 미사용: 직접 LettuceConnectionFactory를 생성하여 2개 인스턴스를 독립 관리합니다.
 *
 * 주요 구성 (Phase 4에서 구현)
 * Redis 설정:
 *   - UiRedisProperties         : @ConfigurationProperties 바인딩 (gateway/business 각각)
 *   - UiRedisConfiguration      : @Bean("gatewayRedisTemplate"), @Bean("businessRedisTemplate")
 * Service:
 *   - GatewayDlqRedisService    : tc:comm:gateway:dlq:* 읽기/삭제,
 *                                 tc:comm:gateway:quarantine:* 읽기 (gatewayRedisTemplate)
 *   - BusinessDlqRedisService   : tc:business:core:dlq:* 읽기/삭제 (businessRedisTemplate)
 *   - UiSessionCacheService     : tc:ui:backend:session:{token} TTL=300s (businessRedisTemplate)
 *   - AsyncResultStoreService   : tc:ui:backend:async:{traceId} TTL=600s (businessRedisTemplate)
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
     * Redis 어댑터는 코어 Port를 구현합니다.
     */
    api(project(":libs:ui:tc-ui-core"))

    /*
     * Spring Data Redis: RedisTemplate, LettuceConnectionFactory, RedisSerializer 등
     * - spring.data.redis.* 자동설정은 비활성화하고, 직접 빈을 구성합니다.
     */
    implementation(libs.spring.boot.starter.data.redis)
    implementation(libs.jackson.databind)

    /*
     * Spring 컴포넌트/설정 바인딩 컴파일 의존성
     */
    compileOnly(libs.spring.boot)
    compileOnly(libs.spring.context)
    compileOnly(libs.jakarta.annotation.api)
    annotationProcessor(libs.spring.boot.configuration.processor)

    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
