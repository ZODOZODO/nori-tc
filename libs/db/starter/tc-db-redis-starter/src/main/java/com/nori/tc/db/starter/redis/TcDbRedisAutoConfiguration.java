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
 * Redis Starter AutoConfiguration
 *
 * 하는 일
 * 1) Redis 관련 의존성을 starter 하나로 묶어서 앱에서 쉽게 선택하도록 한다.
 * 2) Starter 배타 락 Bean 등록(중복 starter 의존 시 fail-fast)
 *
 * 하지 않는 일(중요)
 * - RedisConnectionFactory, RedisTemplate 등을 직접 생성하지 않는다.
 *   → Spring Boot 기본 AutoConfiguration에 위임한다.
 *   → 설정은 표준 spring.data.redis.* 프로퍼티를 사용한다.
 */
@AutoConfiguration
public class TcDbRedisAutoConfiguration {

    /**
     * Starter 배타 락 Bean
     *
     * 모든 tc-db-*-*-starter가 동일한 Bean 이름을 사용해야 한다.
     * - 그러면 starter를 2개 이상 의존 시 Bean name collision로 부팅이 즉시 실패한다.
     *
     * 타입은 중요하지 않으므로 Object/String으로 단순화한다.
     */
    @Bean(name = "tcDbStarterExclusiveLock")
    public Object tcDbStarterExclusiveLock() {
        return "tc-db-redis-starter";
    }

    
    /**
     * DB 스타터 구성 도메인 처리 로직을 수행합니다.
     *
     * <p>데이터소스 및 저장소 빈 자동 구성 조건을 기준으로 처리합니다.</p>
     * @param connectionFactory 통신 채널/세션 정보
     * @param valueSerializerProvider DB 스타터 구성 처리에 사용하는 입력 값
     * @return DB 스타터 구성 처리 결과
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
     * DB 스타터 구성 도메인 처리 로직을 수행합니다.
     *
     * <p>데이터소스 및 저장소 빈 자동 구성 조건을 기준으로 처리합니다.</p>
     * @param tcRedisTemplate DB 스타터 구성 처리에 사용하는 입력 값
     * @return DB 스타터 구성 처리 결과
     */
    @Bean
    @ConditionalOnBean(name = "tcRedisTemplate")
    @ConditionalOnMissingBean
    public TcRedisCrudRepository tcRedisCrudRepository(RedisTemplate<String, Object> tcRedisTemplate) {
        return new TcRedisTemplateCrudRepository(tcRedisTemplate);
    }
}
