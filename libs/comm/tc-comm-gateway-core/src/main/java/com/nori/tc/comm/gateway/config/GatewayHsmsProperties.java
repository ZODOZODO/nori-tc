package com.nori.tc.comm.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nori.tc.comm.gateway.hsms.config.HsmsSessionConfig;
import com.nori.tc.comm.gateway.hsms.config.HsmsTimerConfig;

import jakarta.annotation.PostConstruct;

/**
 * HSMS default configuration.
 *
 * - Equipment-specific overrides can be loaded from DB/Redis at runtime.
 */
@ConfigurationProperties(prefix = "tc.comm.gateway.hsms")
public class GatewayHsmsProperties {

    private static final Logger log = LoggerFactory.getLogger(GatewayHsmsProperties.class);

    private Integer deviceId;

    private final Timer timer = new Timer();

    private Boolean linktestEnabled;

    private Long linktestIntervalMs;

    private Integer maxFrameBytes;

    private Boolean requireSelectBeforeData;

    
    /**
     * 게이트웨이 코어 모듈 입력/설정 유효성을 검증합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     */
    @PostConstruct
    public void validate() {
        if (deviceId == null) {
            throw new IllegalStateException("tc.comm.gateway.hsms.device-id is required");
        }
        if (timer.t3ReplyTimeoutMs == null || timer.t3ReplyTimeoutMs <= 0) {
            throw new IllegalStateException("tc.comm.gateway.hsms.timer.t3-reply-timeout-ms must be > 0");
        }
        if (timer.t5ConnectTimeoutMs == null || timer.t5ConnectTimeoutMs <= 0) {
            throw new IllegalStateException("tc.comm.gateway.hsms.timer.t5-connect-timeout-ms must be > 0");
        }
        if (timer.t6ControlTimeoutMs == null || timer.t6ControlTimeoutMs <= 0) {
            throw new IllegalStateException("tc.comm.gateway.hsms.timer.t6-control-timeout-ms must be > 0");
        }
        if (timer.t7NotSelectedTimeoutMs == null || timer.t7NotSelectedTimeoutMs <= 0) {
            throw new IllegalStateException("tc.comm.gateway.hsms.timer.t7-not-selected-timeout-ms must be > 0");
        }
        if (timer.t8NetworkInterleaveTimeoutMs == null || timer.t8NetworkInterleaveTimeoutMs <= 0) {
            throw new IllegalStateException("tc.comm.gateway.hsms.timer.t8-network-interleave-timeout-ms must be > 0");
        }
        if (linktestEnabled == null) {
            throw new IllegalStateException("tc.comm.gateway.hsms.linktest-enabled is required");
        }
        if (linktestIntervalMs == null || linktestIntervalMs <= 0) {
            throw new IllegalStateException("tc.comm.gateway.hsms.linktest-interval-ms must be > 0");
        }
        if (maxFrameBytes == null || maxFrameBytes <= 0) {
            throw new IllegalStateException("tc.comm.gateway.hsms.max-frame-bytes must be > 0");
        }
        if (requireSelectBeforeData == null) {
            throw new IllegalStateException("tc.comm.gateway.hsms.require-select-before-data is required");
        }
        log.info("GatewayHsmsProperties validated. deviceId={}, linktestEnabled={}, maxFrameBytes={}",
                deviceId, linktestEnabled, maxFrameBytes);
    }

    
    /**
     * 게이트웨이 코어 모듈의 현재 값을 조회합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @return 게이트웨이 코어 모듈 처리 결과
     */
    public int getDeviceId() {
        return deviceId;
    }

    
    /**
     * 게이트웨이 코어 모듈 설정 값을 반영합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @param deviceId 게이트웨이 코어 모듈 처리에 사용하는 입력 값
     */
    public void setDeviceId(final int deviceId) {
        this.deviceId = deviceId;
    }

    
    /**
     * 게이트웨이 코어 모듈의 현재 값을 조회합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @return 게이트웨이 코어 모듈 처리 결과
     */
    public Timer getTimer() {
        return timer;
    }

    
    /**
     * 게이트웨이 코어 모듈의 현재 값을 조회합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @return 처리 성공 여부
     */
    public boolean isLinktestEnabled() {
        return linktestEnabled;
    }

    
    /**
     * 게이트웨이 코어 모듈 설정 값을 반영합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @param linktestEnabled 게이트웨이 코어 모듈 처리에 사용하는 입력 값
     */
    public void setLinktestEnabled(final boolean linktestEnabled) {
        this.linktestEnabled = linktestEnabled;
    }

    
    /**
     * 게이트웨이 코어 모듈의 현재 값을 조회합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @return 게이트웨이 코어 모듈 처리 결과
     */
    public long getLinktestIntervalMs() {
        return linktestIntervalMs;
    }

    
    /**
     * 게이트웨이 코어 모듈 설정 값을 반영합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @param linktestIntervalMs 시간 관련 설정 값
     */
    public void setLinktestIntervalMs(final long linktestIntervalMs) {
        this.linktestIntervalMs = linktestIntervalMs;
    }

    
    /**
     * 게이트웨이 코어 모듈의 현재 값을 조회합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @return 게이트웨이 코어 모듈 처리 결과
     */
    public int getMaxFrameBytes() {
        return maxFrameBytes;
    }

    
    /**
     * 게이트웨이 코어 모듈 설정 값을 반영합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @param maxFrameBytes 처리할 원본 데이터
     */
    public void setMaxFrameBytes(final int maxFrameBytes) {
        this.maxFrameBytes = maxFrameBytes;
    }

    
    /**
     * 게이트웨이 코어 모듈의 현재 값을 조회합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @return 처리 성공 여부
     */
    public boolean isRequireSelectBeforeData() {
        return requireSelectBeforeData;
    }

    
    /**
     * 게이트웨이 코어 모듈 설정 값을 반영합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @param requireSelectBeforeData 처리할 원본 데이터
     */
    public void setRequireSelectBeforeData(final boolean requireSelectBeforeData) {
        this.requireSelectBeforeData = requireSelectBeforeData;
    }

    /**
     * Build an HSMS session config for a specific equipment.
     * - deviceId can be overridden per equipment at runtime.
     */
    public HsmsSessionConfig toSessionConfig(final int equipmentDeviceId) {
        final HsmsTimerConfig timerConfig = new HsmsTimerConfig(
                timer.t3ReplyTimeoutMs,
                timer.t5ConnectTimeoutMs,
                timer.t6ControlTimeoutMs,
                timer.t7NotSelectedTimeoutMs,
                timer.t8NetworkInterleaveTimeoutMs
        );

        return new HsmsSessionConfig(
                equipmentDeviceId,
                timerConfig,
                linktestEnabled,
                linktestIntervalMs,
                maxFrameBytes,
                requireSelectBeforeData
        );
    }

    public static final class Timer {
        private Long t3ReplyTimeoutMs;
        private Long t5ConnectTimeoutMs;
        private Long t6ControlTimeoutMs;
        private Long t7NotSelectedTimeoutMs;
        private Long t8NetworkInterleaveTimeoutMs;

        
        /**
         * 게이트웨이 코어 모듈의 현재 값을 조회합니다.
         *
         * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
         * @return 게이트웨이 코어 모듈 처리 결과
         */
        public long getT3ReplyTimeoutMs() {
            return t3ReplyTimeoutMs;
        }

        
        /**
         * 게이트웨이 코어 모듈 설정 값을 반영합니다.
         *
         * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
         * @param t3ReplyTimeoutMs 시간 관련 설정 값
         */
        public void setT3ReplyTimeoutMs(final long t3ReplyTimeoutMs) {
            this.t3ReplyTimeoutMs = t3ReplyTimeoutMs;
        }

        
        /**
         * 게이트웨이 코어 모듈의 현재 값을 조회합니다.
         *
         * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
         * @return 게이트웨이 코어 모듈 처리 결과
         */
        public long getT5ConnectTimeoutMs() {
            return t5ConnectTimeoutMs;
        }

        
        /**
         * 게이트웨이 코어 모듈 설정 값을 반영합니다.
         *
         * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
         * @param t5ConnectTimeoutMs 시간 관련 설정 값
         */
        public void setT5ConnectTimeoutMs(final long t5ConnectTimeoutMs) {
            this.t5ConnectTimeoutMs = t5ConnectTimeoutMs;
        }

        
        /**
         * 게이트웨이 코어 모듈의 현재 값을 조회합니다.
         *
         * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
         * @return 게이트웨이 코어 모듈 처리 결과
         */
        public long getT6ControlTimeoutMs() {
            return t6ControlTimeoutMs;
        }

        
        /**
         * 게이트웨이 코어 모듈 설정 값을 반영합니다.
         *
         * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
         * @param t6ControlTimeoutMs 시간 관련 설정 값
         */
        public void setT6ControlTimeoutMs(final long t6ControlTimeoutMs) {
            this.t6ControlTimeoutMs = t6ControlTimeoutMs;
        }

        
        /**
         * 게이트웨이 코어 모듈의 현재 값을 조회합니다.
         *
         * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
         * @return 게이트웨이 코어 모듈 처리 결과
         */
        public long getT7NotSelectedTimeoutMs() {
            return t7NotSelectedTimeoutMs;
        }

        
        /**
         * 게이트웨이 코어 모듈 설정 값을 반영합니다.
         *
         * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
         * @param t7NotSelectedTimeoutMs 시간 관련 설정 값
         */
        public void setT7NotSelectedTimeoutMs(final long t7NotSelectedTimeoutMs) {
            this.t7NotSelectedTimeoutMs = t7NotSelectedTimeoutMs;
        }

        
        /**
         * 게이트웨이 코어 모듈의 현재 값을 조회합니다.
         *
         * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
         * @return 게이트웨이 코어 모듈 처리 결과
         */
        public long getT8NetworkInterleaveTimeoutMs() {
            return t8NetworkInterleaveTimeoutMs;
        }

        
        /**
         * 게이트웨이 코어 모듈 설정 값을 반영합니다.
         *
         * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
         * @param t8NetworkInterleaveTimeoutMs 시간 관련 설정 값
         */
        public void setT8NetworkInterleaveTimeoutMs(final long t8NetworkInterleaveTimeoutMs) {
            this.t8NetworkInterleaveTimeoutMs = t8NetworkInterleaveTimeoutMs;
        }
    }
}
