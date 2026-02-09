package com.nori.tc.apps.commgateway.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;

/**
 * JPA/Redis Repository 스캔 설정
 *
 * - 동일 패키지 루트에서 JPA/Redis repository가 함께 존재하므로
 *   명시적으로 basePackages를 분리하여 충돌을 방지합니다.
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.nori.tc.apps.commgateway.db")
@EnableRedisRepositories(basePackages = "com.nori.tc.apps.commgateway.redis")
public class GatewayRepositoryConfiguration {
}
