package com.nori.tc.db.starter.redis;

import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis Starter 자동 구성 클래스입니다.
 *
 * <p>이 클래스는 Redis를 사용하는 공통 인프라 빈을 제공합니다.</p>
 * <p>주요 역할은 아래와 같습니다.</p>
 * <p>1) Spring Boot 기본 자동 구성에서 제공하는 {@link RedisConnectionFactory}를 기반으로
 * 공통 {@code tcRedisTemplate} 빈을 생성합니다.</p>
 * <p>2) {@link TcRedisCrudRepository} 구현체를 기본 등록하여 모듈 간 재사용 포인트를 제공합니다.</p>
 * <p>3) 관계형 DB starter와 함께 사용할 때도 Bean 이름 충돌 없이 동작하도록
 * Redis 전용 식별 Bean을 별도로 제공합니다.</p>
 */
@AutoConfiguration(afterName = {
        "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration",
        "org.springframework.boot.data.redis.autoconfigure.LettuceConnectionConfiguration"
})
public class TcDbRedisAutoConfiguration {

    /**
     * Redis starter 전용 식별 Bean입니다.
     *
     * <p>관계형 DB starter가 사용하는 식별 Bean 이름({@code tcDbStarterExclusiveLock})과 분리하여,
     * DB starter + Redis starter를 함께 사용할 때 이름 충돌을 방지합니다.</p>
     */
    @Bean(name = "tcDbRedisStarterExclusiveLock")
    public Object tcDbRedisStarterExclusiveLock() {
        return "tc-db-redis-starter";
    }

    /**
     * 공통 RedisTemplate({@code tcRedisTemplate}) 빈을 등록합니다.
     *
     * <p>동작 조건은 아래와 같습니다.</p>
     * <p>- {@link RedisConnectionFactory}가 존재할 때만 생성됩니다.</p>
     * <p>- 동일 이름 Bean이 이미 있으면 중복 생성하지 않습니다.</p>
     *
     * @param connectionFactory Redis 연결 팩토리
     * @param valueSerializerProvider 외부에서 주입 가능한 값 직렬화기 제공자
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
        return template;
    }

    /**
     * {@link TcRedisCrudRepository} 기본 구현체를 등록합니다.
     *
     * <p>동작 조건은 아래와 같습니다.</p>
     * <p>- {@code tcRedisTemplate} Bean이 존재할 때만 생성됩니다.</p>
     * <p>- 이미 다른 구현체가 있으면 중복 생성하지 않습니다.</p>
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
     * <p>Spring Data Redis 4.x의 권장 팩토리 메서드 {@link RedisSerializer#json()}을 사용해
     * 제거 예정 API 직접 참조를 피하고 컴파일 경고를 줄입니다.</p>
     *
     * @return Object 타입 대상 JSON 직렬화기
     */
    private static RedisSerializer<Object> createDefaultJsonSerializer() {
        return RedisSerializer.json();
    }
}
