package com.nori.tc.ui.core.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * UI 인증 관련 설정 바인딩 클래스입니다.
 *
 * <p>바인딩 대상 (config/tc-ui-backend.properties):</p>
 * <pre>
 * tc.ui.backend.auth.session-ttl-hours=8
 * tc.ui.backend.auth.token-cache-ttl-seconds=300
 * </pre>
 *
 * <p>사용처:</p>
 * <ul>
 *   <li>{@code sessionTtlHours}: LoginUseCase에서 세션 만료 시각(expiresAt) 계산</li>
 *   <li>{@code tokenCacheTtlSeconds}: tc-ui-redis-adapter의 UiSessionCacheService에서
 *       Redis TTL 설정</li>
 * </ul>
 *
 * @param sessionTtlHours        세션 유효 시간 (단위: 시간, 기본값 8)
 * @param tokenCacheTtlSeconds   Redis 토큰 캐시 TTL (단위: 초, 기본값 300)
 */
@ConfigurationProperties(prefix = "tc.ui.backend.auth")
public record UiAuthProperties(
        int sessionTtlHours,
        int tokenCacheTtlSeconds
) {
    /**
     * 기본값을 적용하는 compact constructor입니다.
     *
     * <p>설정 파일에 값이 없을 경우 0이 바인딩되는 것을 방지하기 위해
     * 양수 기본값을 강제합니다.</p>
     */
    public UiAuthProperties {
        if (sessionTtlHours <= 0) {
            sessionTtlHours = 8;
        }
        if (tokenCacheTtlSeconds <= 0) {
            tokenCacheTtlSeconds = 300;
        }
    }
}
