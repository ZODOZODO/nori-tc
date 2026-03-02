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
     * - TokenCachePort, AsyncResultStorePort 구현체가 위치합니다.
     * - UiAuthProperties(tokenCacheTtlSeconds) 도 이 모듈을 통해 사용합니다.
     */
    api(project(":libs:ui:tc-ui-core"))

    /*
     * Gateway Redis DLQ 엔트리 역직렬화 의존성.
     * - GatewayDlqRedisService가 tc:comm:gateway:dlq:* 키에 저장된
     *   RedisDlqEntry, RedisQuarantineEntry 를 JDK 역직렬화로 읽어야 합니다.
     * - JDK Serialization 특성상 직렬화 시 사용한 원본 클래스가 런타임 classpath에
     *   있어야 ClassNotFoundException 없이 역직렬화됩니다.
     */
    implementation(project(":libs:comm:adapter:tc-comm-gateway-redis-adapter"))

    /*
     * Business Redis DLQ 엔트리 역직렬화 의존성.
     * - BusinessDlqRedisService가 tc:business:core:dlq:* 키에 저장된
     *   RedisBusinessDlqEntry 를 JDK 역직렬화로 읽어야 합니다.
     */
    implementation(project(":libs:business:adapter:tc-business-redis-adapter"))

    /*
     * Spring Data Redis: RedisTemplate, LettuceConnectionFactory, RedisSerializer 등
     * - spring.data.redis.* 자동설정은 비활성화하고, 직접 빈을 구성합니다.
     * - gatewayRedisTemplate, businessRedisTemplate 두 개를 독립 생성합니다.
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
