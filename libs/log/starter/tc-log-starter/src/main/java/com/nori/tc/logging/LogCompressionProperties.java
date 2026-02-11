package com.nori.tc.logging;

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
     * 로그 압축 기능 활성화 여부.
     */
    private boolean enabled = true;

    /**
     * 최근 N일치 로그는 .log 유지 (기본 2일).
     */
    private int afterDays = 2;

    /**
     * 압축 스캔 주기 (분).
     */
    private int scanIntervalMinutes = 60;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    public int getAfterDays() {
        return afterDays;
    }

    public void setAfterDays(final int afterDays) {
        this.afterDays = afterDays;
    }

    public int getScanIntervalMinutes() {
        return scanIntervalMinutes;
    }

    public void setScanIntervalMinutes(final int scanIntervalMinutes) {
        this.scanIntervalMinutes = scanIntervalMinutes;
    }
}
