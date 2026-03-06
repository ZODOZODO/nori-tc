package com.nori.tc.db.starter.redis;

import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis starter 자동 구성 클래스입니다.
 *
 * <p>
 * 본 구성은 관계형 DB 스타터와 동시에 사용되더라도 빈 이름 충돌 없이
 * 공통 RedisTemplate 및 CRUD Repository를 일관되게 제공하는 것을 목적으로 합니다.
 * </p>
 */
@AutoConfiguration(afterName = {
        "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration",
        "org.springframework.boot.data.redis.autoconfigure.LettuceConnectionConfiguration"
})
public class TcDbRedisAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TcDbRedisAutoConfiguration.class);

    /**
     * Redis starter 전용 배타 락 빈입니다.
     *
     * <p>
     * 관계형 DB 스타터의 배타 락 빈과 분리하여,
     * DB starter + Redis starter 동시 사용 시 충돌을 방지합니다.
     * </p>
     */
    @Bean(name = "tcDbRedisStarterExclusiveLock")
    public Object tcDbRedisStarterExclusiveLock() {
        return "tc-db-redis-starter";
    }

    /**
     * 공통 RedisTemplate({@code tcRedisTemplate}) 빈을 등록합니다.
     *
     * <p>
     * 동작 규칙:
     * - {@link RedisConnectionFactory}가 있을 때만 생성
     * - 동일 이름 빈이 이미 있으면 생성하지 않음
     * - 값 직렬화기는 외부 주입값을 우선 사용, 없으면 기본 JSON 직렬화기를 사용
     * </p>
     *
     * @param connectionFactory Redis 연결 팩토리
     * @param valueSerializerProvider 외부 주입 가능 값 직렬화기
     * @return 공통 RedisTemplate
     */
    @Bean(name = "tcRedisTemplate")
    @ConditionalOnBean(RedisConnectionFactory.class)
    @ConditionalOnMissingBean(name = "tcRedisTemplate")
    public RedisTemplate<String, Object> tcRedisTemplate(
            final RedisConnectionFactory connectionFactory,
            final ObjectProvider<RedisSerializer<Object>> valueSerializerProvider
    ) {
        final RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        final RedisSerializer<String> keySerializer = new StringRedisSerializer();
        final RedisSerializer<Object> valueSerializer = Optional.ofNullable(valueSerializerProvider.getIfAvailable())
                .orElseGet(TcDbRedisAutoConfiguration::createDefaultJsonSerializer);

        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);
        template.afterPropertiesSet();

        if (log.isDebugEnabled()) {
            log.debug("tcRedisTemplate initialized. valueSerializer={}", valueSerializer.getClass().getName());
        }
        return template;
    }

    /**
     * {@link TcRedisCrudRepository} 기본 구현체를 등록합니다.
     *
     * <p>
     * 동작 규칙:
     * - {@code tcRedisTemplate} 빈이 있을 때만 생성
     * - 다른 구현체가 이미 존재하면 생성하지 않음
     * </p>
     *
     * @param tcRedisTemplate 공통 RedisTemplate
     * @return Redis CRUD 저장소 구현체
     */
    @Bean
    @ConditionalOnBean(name = "tcRedisTemplate")
    @ConditionalOnMissingBean
    public TcRedisCrudRepository tcRedisCrudRepository(final RedisTemplate<String, Object> tcRedisTemplate) {
        return new TcRedisTemplateCrudRepository(tcRedisTemplate);
    }

    /**
     * 기본 JSON 직렬화기를 생성합니다.
     *
     * <p>
     * Spring Data Redis 4.x의 {@link RedisSerializer#json()}은 Jackson 3(`tools.jackson`) 기반 구현입니다.
     * 현재 프로젝트 런타임은 Jackson 2(`com.fasterxml.jackson`) 생태계에 맞춰져 있으므로,
     * 기본 직렬화기는 {@link GenericJackson2JsonRedisSerializer}로 고정합니다.
     * </p>
     *
     * @return Object 값용 기본 JSON 직렬화기
     */
    @SuppressWarnings("removal")
    private static RedisSerializer<Object> createDefaultJsonSerializer() {
        final ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        if (log.isDebugEnabled()) {
            log.debug("tcRedisTemplate default serializer initialized as GenericJackson2JsonRedisSerializer");
        }
        // TODO(tc-db-redis-starter): Jackson 3 전환 시점에 GenericJacksonJsonRedisSerializer(tools.jackson)로 마이그레이션한다.
        // 현재는 프로젝트 전반의 Jackson 2(com.fasterxml.jackson) 기반 호환성을 위해 기존 serializer를 유지한다.
        return new GenericJackson2JsonRedisSerializer(objectMapper);
    }
}
