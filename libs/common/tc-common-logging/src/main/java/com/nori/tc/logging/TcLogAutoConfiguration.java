package com.nori.tc.logging;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * tc-common-logging 자동 설정.
 *
 * - 로그 압축 스케줄러를 등록한다
 * - 기본값: enabled=true
 */
@AutoConfiguration
@EnableConfigurationProperties(LogCompressionProperties.class)
@ConditionalOnProperty(prefix = "tc.logging.compress", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TcLogAutoConfiguration {

    /**
     * 로깅 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>MDC 컨텍스트 전파, 로그 압축/보관 정책, 자동 구성 규칙을 기준으로 처리합니다.</p>
     * @param properties 모듈 설정 값 객체
     * @param environment 런타임 환경 설정 정보
     * @return 로깅 모듈 처리 결과
     */
    @Bean
    public LogCompressionScheduler logCompressionScheduler(
            final LogCompressionProperties properties,
            final Environment environment
    ) {
        return new LogCompressionScheduler(properties, environment);
    }
}

