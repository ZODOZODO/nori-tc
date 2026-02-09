package com.nori.tc.db.starter.redis;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

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
}
