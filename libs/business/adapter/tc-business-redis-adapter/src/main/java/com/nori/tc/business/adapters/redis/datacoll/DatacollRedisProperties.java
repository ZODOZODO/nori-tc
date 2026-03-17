package com.nori.tc.business.adapters.redis.datacoll;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * DATACOLL 수집 상태 Redis 관련 런타임 설정입니다.
 *
 * <p>prefix: {@code tc.business.core.redis}</p>
 *
 * <p>TTL은 lot 처리 최대 시간보다 여유롭게 설정해야 합니다.
 * DATACOLL 보고 후 즉시 삭제하는 것이 기본이지만, 삭제 실패 시에도 TTL이 자동으로 만료됩니다.</p>
 */
@ConfigurationProperties(prefix = "tc.business.core.redis")
public class DatacollRedisProperties {

    private static final Logger log = LoggerFactory.getLogger(DatacollRedisProperties.class);

    /**
     * DATACOLL 수집 상태 보관 TTL(초)입니다.
     *
     * <p>기본값 86400초(24시간)는 일반적인 lot 처리 시간보다 충분히 여유롭게 설정된 값입니다.
     * 0이면 만료 없이 보관합니다.</p>
     */
    private long datacollTtlSeconds = 86400L;

    /**
     * 설정값 유효성을 검증합니다.
     */
    @PostConstruct
    public void validate() {
        if (datacollTtlSeconds < 0L) {
            throw new IllegalStateException("tc.business.core.redis.datacoll-ttl-seconds must be >= 0");
        }
        log.info("DatacollRedisProperties validated. datacollTtlSeconds={}", datacollTtlSeconds);
    }

    /**
     * DATACOLL 수집 상태 Redis TTL(초)을 반환합니다.
     *
     * @return TTL 초 값. 0이면 만료 없음.
     */
    public long getDatacollTtlSeconds() {
        return datacollTtlSeconds;
    }

    /**
     * DATACOLL 수집 상태 Redis TTL(초)을 설정합니다.
     *
     * @param datacollTtlSeconds TTL 초 값
     */
    public void setDatacollTtlSeconds(final long datacollTtlSeconds) {
        this.datacollTtlSeconds = datacollTtlSeconds;
    }
}
