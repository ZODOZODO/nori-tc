package com.nori.tc.ui.adapters.redis.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * UI Backend Redis 이중 연결 설정 클래스입니다.
 *
 * <p>Gateway Redis(6379)와 Business Redis(6380)에 대한 독립된 {@link RedisTemplate}
 * 빈을 각각 생성합니다. Spring Boot 기본 {@code spring.data.redis.*} 자동설정을
 * 사용하지 않으므로, {@link LettuceConnectionFactory}를 직접 생성합니다.</p>
 *
 * <p>직렬화 전략:</p>
 * <ul>
 *   <li>Key: {@link StringRedisSerializer} — 사람이 읽을 수 있는 키 형식 유지</li>
 *   <li>Value: {@link JdkSerializationRedisSerializer} — gateway/business 어댑터가
 *       저장한 DLQ 엔트리(RedisDlqEntry, RedisBusinessDlqEntry 등)와 동일한
 *       직렬화 방식을 사용하여 역직렬화 호환성을 보장합니다.</li>
 * </ul>
 *
 * <p>연결 수명 주기:</p>
 * <p>각 {@link LettuceConnectionFactory}는 Spring Bean으로 등록되어
 * {@code destroyMethod = "destroy"}를 통해 애플리케이션 종료 시 정상 해제됩니다.</p>
 */
@Configuration
@EnableConfigurationProperties(UiRedisProperties.class)
public class UiRedisConfiguration {

    private static final Logger log = LoggerFactory.getLogger(UiRedisConfiguration.class);

    // -------------------------------------------------------------------------
    // Gateway Redis (6379) — DLQ / Quarantine 조회·삭제 전용
    // -------------------------------------------------------------------------

    /**
     * Gateway Redis(6379) 연결 팩토리 빈입니다.
     *
     * <p>Bean으로 등록해야 Spring이 {@code destroy()} 를 호출하여
     * 애플리케이션 종료 시 Lettuce 연결을 정상 해제합니다.</p>
     *
     * @param props Gateway 노드 연결 정보가 담긴 설정 객체
     * @return 초기화가 완료된 {@link LettuceConnectionFactory}
     */
    @Bean(name = "gatewayLettuceConnectionFactory", destroyMethod = "destroy")
    public LettuceConnectionFactory gatewayLettuceConnectionFactory(final UiRedisProperties props) {
        final LettuceConnectionFactory factory = buildConnectionFactory(props.getGateway());
        log.debug("Gateway LettuceConnectionFactory 생성. host={}:{}",
                props.getGateway().getHost(), props.getGateway().getPort());
        return factory;
    }

    /**
     * Gateway Redis용 {@link RedisTemplate} 빈입니다.
     *
     * <p>사용처:</p>
     * <ul>
     *   <li>{@code GatewayDlqRedisService} — {@code tc:comm:gateway:dlq:*} 읽기/삭제</li>
     *   <li>{@code GatewayDlqRedisService} — {@code tc:comm:gateway:quarantine:*} 읽기</li>
     * </ul>
     *
     * @param factory gatewayLettuceConnectionFactory
     * @return JDK 직렬화 기반 {@link RedisTemplate}
     */
    @Bean(name = "gatewayRedisTemplate")
    public RedisTemplate<String, Object> gatewayRedisTemplate(
            @Qualifier("gatewayLettuceConnectionFactory") final LettuceConnectionFactory factory
    ) {
        final RedisTemplate<String, Object> template = buildTemplate(factory);
        log.info("gatewayRedisTemplate 초기화 완료.");
        return template;
    }

    // -------------------------------------------------------------------------
    // Business Redis (6380) — DLQ 조회 + 토큰 캐시 + 비동기 결과 저장
    // -------------------------------------------------------------------------

    /**
     * Business Redis(6380) 연결 팩토리 빈입니다.
     *
     * @param props Business 노드 연결 정보가 담긴 설정 객체
     * @return 초기화가 완료된 {@link LettuceConnectionFactory}
     */
    @Bean(name = "businessLettuceConnectionFactory", destroyMethod = "destroy")
    public LettuceConnectionFactory businessLettuceConnectionFactory(final UiRedisProperties props) {
        final LettuceConnectionFactory factory = buildConnectionFactory(props.getBusiness());
        log.debug("Business LettuceConnectionFactory 생성. host={}:{}",
                props.getBusiness().getHost(), props.getBusiness().getPort());
        return factory;
    }

    /**
     * Business Redis용 {@link RedisTemplate} 빈입니다.
     *
     * <p>사용처:</p>
     * <ul>
     *   <li>{@code BusinessDlqRedisService} — {@code tc:business:core:dlq:*} 읽기/삭제</li>
     *   <li>{@code UiSessionCacheService} — {@code tc:ui:backend:session:{token}} 캐시 저장/조회</li>
     *   <li>{@code AsyncResultStoreService} — {@code tc:ui:backend:async:{traceId}} 결과 저장/조회</li>
     * </ul>
     *
     * @param factory businessLettuceConnectionFactory
     * @return JDK 직렬화 기반 {@link RedisTemplate}
     */
    @Bean(name = "businessRedisTemplate")
    public RedisTemplate<String, Object> businessRedisTemplate(
            @Qualifier("businessLettuceConnectionFactory") final LettuceConnectionFactory factory
    ) {
        final RedisTemplate<String, Object> template = buildTemplate(factory);
        log.info("businessRedisTemplate 초기화 완료.");
        return template;
    }

    // -------------------------------------------------------------------------
    // 공통 빌더 메서드
    // -------------------------------------------------------------------------

    /**
     * Redis 노드 설정을 기반으로 {@link LettuceConnectionFactory}를 생성합니다.
     *
     * <p>password 가 비어 있으면 인증 없이 연결을 시도합니다.
     * {@code afterPropertiesSet()} 을 호출하여 연결 팩토리를 즉시 초기화합니다.</p>
     *
     * @param node 연결할 Redis 노드 정보
     * @return 초기화된 {@link LettuceConnectionFactory}
     */
    private LettuceConnectionFactory buildConnectionFactory(
            final UiRedisProperties.RedisNodeProperties node
    ) {
        final RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(node.getHost());
        config.setPort(node.getPort());

        // 비밀번호가 설정된 경우에만 인증 정보 추가
        if (node.getPassword() != null && !node.getPassword().isBlank()) {
            config.setPassword(RedisPassword.of(node.getPassword()));
        }

        final LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
        factory.afterPropertiesSet();
        return factory;
    }

    /**
     * 공통 직렬화 설정이 적용된 {@link RedisTemplate}을 생성합니다.
     *
     * <p>Key/HashKey: {@link StringRedisSerializer} — 문자열 키</p>
     * <p>Value/HashValue: {@link JdkSerializationRedisSerializer} — JDK 직렬화</p>
     * <p>{@code afterPropertiesSet()} 을 호출하여 템플릿을 즉시 초기화합니다.</p>
     *
     * @param factory 연결 팩토리
     * @return 설정이 완료된 {@link RedisTemplate}
     */
    private RedisTemplate<String, Object> buildTemplate(final LettuceConnectionFactory factory) {
        final RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // 키는 사람이 읽을 수 있는 문자열로 저장 (scan 패턴 매칭에도 유리)
        final StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // 값은 JDK 직렬화 — gateway/business 어댑터의 DLQ 엔트리와 역직렬화 호환
        final JdkSerializationRedisSerializer jdkSerializer = new JdkSerializationRedisSerializer();
        template.setValueSerializer(jdkSerializer);
        template.setHashValueSerializer(jdkSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
