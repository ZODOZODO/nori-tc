package com.nori.tc.logging;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * tc-log-starter 자동 설정.
 *
 * - 로그 압축 스케줄러를 등록한다
 * - 기본값: enabled=true
 */
@AutoConfiguration
@EnableConfigurationProperties(LogCompressionProperties.class)
@ConditionalOnProperty(prefix = "tc.logging.compress", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TcLogAutoConfiguration {

    @Bean
    public LogCompressionScheduler logCompressionScheduler(
            final LogCompressionProperties properties,
            final Environment environment
    ) {
        return new LogCompressionScheduler(properties, environment);
    }
}
