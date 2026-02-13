package com.nori.tc.db.starter.redis;

import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis Starter 자동 구성입니다.
 *
 * <p>역할</p>
 * <p>1) Redis 관련 의존성을 starter 하나로 묶어서 애플리케이션에서 쉽게 선택하도록 합니다.</p>
 * <p>2) Redis starter 전용 배타 락 Bean을 등록합니다.</p>
 *
 * <p>참고</p>
 * <p>- RedisConnectionFactory, RedisTemplate 생성은 Spring Boot 기본 AutoConfiguration에 위임합니다.</p>
 * <p>- 설정은 {@code spring.data.redis.*} 프로퍼티를 사용합니다.</p>
 */
@AutoConfiguration
public class TcDbRedisAutoConfiguration {

    /**
     * Redis starter 전용 배타 락 Bean입니다.
     *
     * <p>관계형 DB starter의 락 Bean({@code tcDbStarterExclusiveLock})과 이름을 분리해서,
     * Redis starter와 관계형 DB starter를 함께 사용할 때 Bean 이름 충돌을 방지합니다.</p>
     */
    @Bean(name = "tcDbRedisStarterExclusiveLock")
    public Object tcDbRedisStarterExclusiveLock() {
        return "tc-db-redis-starter";
    }

    /**
     * 공통 RedisTemplate({@code tcRedisTemplate})을 등록합니다.
     *
     * @param connectionFactory Redis 연결 팩토리
     * @param valueSerializerProvider 값 직렬화기 제공자(없으면 JDK 직렬화기 사용)
     * @return 공통 RedisTemplate
     */
    @Bean(name = "tcRedisTemplate")
    @ConditionalOnBean(RedisConnectionFactory.class)
    @ConditionalOnMissingBean(name = "tcRedisTemplate")
    public RedisTemplate<String, Object> tcRedisTemplate(
            RedisConnectionFactory connectionFactory,
            ObjectProvider<RedisSerializer<Object>> valueSerializerProvider) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        RedisSerializer<String> keySerializer = new StringRedisSerializer();
        RedisSerializer<Object> valueSerializer = Optional.ofNullable(valueSerializerProvider.getIfAvailable())
                .orElseGet(JdkSerializationRedisSerializer::new);

        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);
        template.afterPropertiesSet();
        return template;
    }

    /**
     * TcRedisCrudRepository 구현체를 등록합니다.
     *
     * @param tcRedisTemplate 공통 RedisTemplate
     * @return Redis CRUD 저장소 구현체
     */
    @Bean
    @ConditionalOnBean(name = "tcRedisTemplate")
    @ConditionalOnMissingBean
    public TcRedisCrudRepository tcRedisCrudRepository(RedisTemplate<String, Object> tcRedisTemplate) {
        return new TcRedisTemplateCrudRepository(tcRedisTemplate);
    }
}