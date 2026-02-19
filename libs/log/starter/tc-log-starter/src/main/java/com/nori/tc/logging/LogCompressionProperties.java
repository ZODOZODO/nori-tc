package com.nori.tc.logging;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 로그 압축 스케줄 설정.
 *
 * - afterDays: 최근 N일 로그는 .log 그대로 유지
 * - 그보다 오래된 .log는 .gz로 압축
 */
@ConfigurationProperties(prefix = "tc.logging.compress")
public class LogCompressionProperties {

    /**
     * 설정 검증 및 바인딩 상태를 기록하는 로거입니다.
     */
    private static final Logger log = LoggerFactory.getLogger(LogCompressionProperties.class);

    /**
     * 로그 압축 기능 활성화 여부.
     */
    private Boolean enabled;

    /**
     * 최근 N일치 로그는 .log 유지 (기본 2일).
     */
    private Integer afterDays;

    /**
     * 압축 스캔 주기 (분).
     */
    private Integer scanIntervalMinutes;

    /**
     * 로그 압축 설정의 필수값/범위를 검증합니다.
     *
     * <p>운영 정책은 config 파일에서만 관리하고, 코드 기본값은 두지 않습니다.</p>
     */
    @PostConstruct
    public void validate() {
        if (enabled == null) {
            throw new IllegalStateException("tc.logging.compress.enabled is required");
        }
        if (afterDays == null || afterDays < 0) {
            throw new IllegalStateException("tc.logging.compress.after-days must be >= 0");
        }
        if (scanIntervalMinutes == null || scanIntervalMinutes <= 0) {
            throw new IllegalStateException("tc.logging.compress.scan-interval-minutes must be > 0");
        }
        log.info("LogCompressionProperties validated. enabled={}, afterDays={}, scanIntervalMinutes={}",
                enabled,
                afterDays,
                scanIntervalMinutes);
    }

    /**
     * 로깅 모듈 현재 상태를 확인합니다.
     *
     * <p>MDC 컨텍스트 전파, 로그 압축/보관 정책, 자동 구성 규칙을 기준으로 처리합니다.</p>
     * @return 처리 성공 여부
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 로깅 모듈 상태/설정 값을 반영합니다.
     *
     * <p>MDC 컨텍스트 전파, 로그 압축/보관 정책, 자동 구성 규칙을 기준으로 처리합니다.</p>
     * @param enabled 로깅 모듈 처리에 사용하는 입력 값
     */
    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 로깅 모듈에서 필요한 값을 조회합니다.
     *
     * <p>MDC 컨텍스트 전파, 로그 압축/보관 정책, 자동 구성 규칙을 기준으로 처리합니다.</p>
     * @return 로깅 모듈 처리 결과
     */
    public int getAfterDays() {
        return afterDays;
    }

    /**
     * 로깅 모듈 상태/설정 값을 반영합니다.
     *
     * <p>MDC 컨텍스트 전파, 로그 압축/보관 정책, 자동 구성 규칙을 기준으로 처리합니다.</p>
     * @param afterDays 로깅 모듈 처리에 사용하는 입력 값
     */
    public void setAfterDays(final int afterDays) {
        this.afterDays = afterDays;
    }

    /**
     * 로깅 모듈에서 필요한 값을 조회합니다.
     *
     * <p>MDC 컨텍스트 전파, 로그 압축/보관 정책, 자동 구성 규칙을 기준으로 처리합니다.</p>
     * @return 로깅 모듈 처리 결과
     */
    public int getScanIntervalMinutes() {
        return scanIntervalMinutes;
    }

    /**
     * 로깅 모듈 상태/설정 값을 반영합니다.
     *
     * <p>MDC 컨텍스트 전파, 로그 압축/보관 정책, 자동 구성 규칙을 기준으로 처리합니다.</p>
     * @param scanIntervalMinutes 로깅 모듈 처리에 사용하는 입력 값
     */
    public void setScanIntervalMinutes(final int scanIntervalMinutes) {
        this.scanIntervalMinutes = scanIntervalMinutes;
    }
}
