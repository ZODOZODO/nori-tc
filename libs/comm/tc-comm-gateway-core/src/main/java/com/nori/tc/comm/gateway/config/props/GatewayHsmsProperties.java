package com.nori.tc.comm.gateway.config.props;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nori.tc.comm.gateway.hsms.config.HsmsSessionConfig;
import com.nori.tc.comm.gateway.hsms.config.HsmsTimerConfig;

import jakarta.annotation.PostConstruct;

/**
 * 게이트웨이 HSMS 기본 설정 프로퍼티입니다.
 *
 * <p>prefix: {@code tc.comm.gateway.hsms}</p>
 *
 * <p>역할:</p>
 * <p>1) HSMS 세션 기본 deviceId 및 타이머(T3/T5/T6/T7/T8) 설정 제공</p>
 * <p>2) 링크테스트(linktest) 동작 여부/주기 설정 제공</p>
 * <p>3) 최대 프레임 크기, SELECT 전 DATA 허용 정책 제공</p>
 *
 * <p>장비별 개별값은 DB/Redis 등에서 override 될 수 있으며,
 * 본 클래스는 게이트웨이 기본값(default) 역할을 수행합니다.</p>
 */
@ConfigurationProperties(prefix = "tc.comm.gateway.hsms")
public class GatewayHsmsProperties {

    private static final Logger log = LoggerFactory.getLogger(GatewayHsmsProperties.class);

    /**
     * 게이트웨이 기본 HSMS deviceId 입니다.
     *
     * <p>장비별 설정이 없을 때 fallback 값으로 사용됩니다.</p>
     */
    private Integer deviceId;

    /**
     * HSMS 타이머 묶음(T3/T5/T6/T7/T8) 설정입니다.
     */
    private final Timer timer = new Timer();

    /**
     * 링크테스트 활성화 여부입니다.
     */
    private Boolean linktestEnabled;

    /**
     * 링크테스트 주기(ms)입니다.
     */
    private Long linktestIntervalMs;

    /**
     * 허용할 최대 HSMS 프레임 크기(bytes)입니다.
     */
    private Integer maxFrameBytes;

    /**
     * DATA 송신 전에 SELECT 절차를 강제할지 여부입니다.
     */
    private Boolean requireSelectBeforeData;

    /**
     * 애플리케이션 기동 시 HSMS 기본 설정의 유효성을 검증합니다.
     *
     * <p>검증 대상:</p>
     * <p>1) 필수 스칼라 설정(deviceId, linktestEnabled, requireSelectBeforeData)</p>
     * <p>2) 양수 조건이 필요한 시간/크기 설정</p>
     * <p>3) 타이머 하위 설정(T3/T5/T6/T7/T8)</p>
     */
    @PostConstruct
    public void validate() {
        // deviceId는 장비별 override가 없을 때 기본값으로 사용되므로 필수입니다.
        if (deviceId == null) {
            throw new IllegalStateException("tc.comm.gateway.hsms.device-id is required");
        }

        // HSMS 타이머는 모두 양수여야 상태머신 timeout 계산이 정상 동작합니다.
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
        if (log.isDebugEnabled()) {
            log.debug(
                    "Gateway HSMS timer policy validated. t3={}, t5={}, t6={}, t7={}, t8={}, linktestIntervalMs={}, requireSelectBeforeData={}",
                    timer.t3ReplyTimeoutMs,
                    timer.t5ConnectTimeoutMs,
                    timer.t6ControlTimeoutMs,
                    timer.t7NotSelectedTimeoutMs,
                    timer.t8NetworkInterleaveTimeoutMs,
                    linktestIntervalMs,
                    requireSelectBeforeData
            );
        }
    }

    
    /**
     * getDeviceId 프로퍼티 값을 반환합니다.
     *
     * @return 프로퍼티 값
     */
    public int getDeviceId() {
        return deviceId;
    }

    
    /**
     * setDeviceId 프로퍼티 값을 설정합니다.
     *
     * @param deviceId 설정할 프로퍼티 값
     */
    public void setDeviceId(final int deviceId) {
        this.deviceId = deviceId;
    }

    
    /**
     * getTimer 프로퍼티 값을 반환합니다.
     *
     * @return 프로퍼티 값
     */
    public Timer getTimer() {
        return timer;
    }

    
    /**
     * isLinktestEnabled 프로퍼티 값을 반환합니다.
     *
     * @return 프로퍼티 값
     */
    public boolean isLinktestEnabled() {
        return linktestEnabled;
    }

    
    /**
     * setLinktestEnabled 프로퍼티 값을 설정합니다.
     *
     * @param linktestEnabled 설정할 프로퍼티 값
     */
    public void setLinktestEnabled(final boolean linktestEnabled) {
        this.linktestEnabled = linktestEnabled;
    }

    
    /**
     * getLinktestIntervalMs 프로퍼티 값을 반환합니다.
     *
     * @return 프로퍼티 값
     */
    public long getLinktestIntervalMs() {
        return linktestIntervalMs;
    }

    
    /**
     * setLinktestIntervalMs 프로퍼티 값을 설정합니다.
     *
     * @param linktestIntervalMs 설정할 프로퍼티 값
     */
    public void setLinktestIntervalMs(final long linktestIntervalMs) {
        this.linktestIntervalMs = linktestIntervalMs;
    }

    
    /**
     * getMaxFrameBytes 프로퍼티 값을 반환합니다.
     *
     * @return 프로퍼티 값
     */
    public int getMaxFrameBytes() {
        return maxFrameBytes;
    }

    
    /**
     * setMaxFrameBytes 프로퍼티 값을 설정합니다.
     *
     * @param maxFrameBytes 설정할 프로퍼티 값
     */
    public void setMaxFrameBytes(final int maxFrameBytes) {
        this.maxFrameBytes = maxFrameBytes;
    }

    
    /**
     * isRequireSelectBeforeData 프로퍼티 값을 반환합니다.
     *
     * @return 프로퍼티 값
     */
    public boolean isRequireSelectBeforeData() {
        return requireSelectBeforeData;
    }

    
    /**
     * setRequireSelectBeforeData 프로퍼티 값을 설정합니다.
     *
     * @param requireSelectBeforeData 설정할 프로퍼티 값
     */
    public void setRequireSelectBeforeData(final boolean requireSelectBeforeData) {
        this.requireSelectBeforeData = requireSelectBeforeData;
    }

    /**
     * 설비별 HSMS 세션 설정 객체를 생성합니다.
     *
     * <p>입력으로 받은 {@code equipmentDeviceId}는 장비별 override가 반영된 값이며,
     * 타이머/링크테스트/프레임 정책은 본 프로퍼티의 기본값을 사용합니다.</p>
     *
     * @param equipmentDeviceId 설비별 최종 deviceId
     * @return HSMS 세션 상태머신 생성에 사용할 설정 객체
     */
    public HsmsSessionConfig toSessionConfig(final int equipmentDeviceId) {
        // 타이머 설정은 불변 값 객체로 변환해 세션 상태머신에 전달합니다.
        final HsmsTimerConfig timerConfig = new HsmsTimerConfig(
                timer.t3ReplyTimeoutMs,
                timer.t5ConnectTimeoutMs,
                timer.t6ControlTimeoutMs,
                timer.t7NotSelectedTimeoutMs,
                timer.t8NetworkInterleaveTimeoutMs
        );

        if (log.isDebugEnabled()) {
            log.debug(
                    "HSMS session config created from defaults. equipmentDeviceId={}, linktestEnabled={}, linktestIntervalMs={}, maxFrameBytes={}",
                    equipmentDeviceId,
                    linktestEnabled,
                    linktestIntervalMs,
                    maxFrameBytes
            );
        }

        return new HsmsSessionConfig(
                equipmentDeviceId,
                timerConfig,
                linktestEnabled,
                linktestIntervalMs,
                maxFrameBytes,
                requireSelectBeforeData
        );
    }

    /**
     * HSMS 표준 타이머(T3/T5/T6/T7/T8) 설정 묶음입니다.
     *
     * <p>각 타이머 의미는 HSMS/SEMI 규격 정의를 따르며 단위는 모두 millisecond 입니다.</p>
     */
    public static final class Timer {
        /**
         * T3: Primary 메시지 송신 후 Reply 대기 timeout(ms)
         */
        private Long t3ReplyTimeoutMs;
        /**
         * T5: 연결(connect) 완료 대기 timeout(ms)
         */
        private Long t5ConnectTimeoutMs;
        /**
         * T6: Control transaction 응답 대기 timeout(ms)
         */
        private Long t6ControlTimeoutMs;
        /**
         * T7: Not Selected 상태 timeout(ms)
         */
        private Long t7NotSelectedTimeoutMs;
        /**
         * T8: Network interleave timeout(ms)
         */
        private Long t8NetworkInterleaveTimeoutMs;

        
        /**
         * getT3ReplyTimeoutMs 프로퍼티 값을 반환합니다.
         *
         * @return 프로퍼티 값
         */
        public long getT3ReplyTimeoutMs() {
            return t3ReplyTimeoutMs;
        }

        
        /**
         * setT3ReplyTimeoutMs 프로퍼티 값을 설정합니다.
         *
         * @param t3ReplyTimeoutMs 설정할 프로퍼티 값
         */
        public void setT3ReplyTimeoutMs(final long t3ReplyTimeoutMs) {
            this.t3ReplyTimeoutMs = t3ReplyTimeoutMs;
        }

        
        /**
         * getT5ConnectTimeoutMs 프로퍼티 값을 반환합니다.
         *
         * @return 프로퍼티 값
         */
        public long getT5ConnectTimeoutMs() {
            return t5ConnectTimeoutMs;
        }

        
        /**
         * setT5ConnectTimeoutMs 프로퍼티 값을 설정합니다.
         *
         * @param t5ConnectTimeoutMs 설정할 프로퍼티 값
         */
        public void setT5ConnectTimeoutMs(final long t5ConnectTimeoutMs) {
            this.t5ConnectTimeoutMs = t5ConnectTimeoutMs;
        }

        
        /**
         * getT6ControlTimeoutMs 프로퍼티 값을 반환합니다.
         *
         * @return 프로퍼티 값
         */
        public long getT6ControlTimeoutMs() {
            return t6ControlTimeoutMs;
        }

        
        /**
         * setT6ControlTimeoutMs 프로퍼티 값을 설정합니다.
         *
         * @param t6ControlTimeoutMs 설정할 프로퍼티 값
         */
        public void setT6ControlTimeoutMs(final long t6ControlTimeoutMs) {
            this.t6ControlTimeoutMs = t6ControlTimeoutMs;
        }

        
        /**
         * getT7NotSelectedTimeoutMs 프로퍼티 값을 반환합니다.
         *
         * @return 프로퍼티 값
         */
        public long getT7NotSelectedTimeoutMs() {
            return t7NotSelectedTimeoutMs;
        }

        
        /**
         * setT7NotSelectedTimeoutMs 프로퍼티 값을 설정합니다.
         *
         * @param t7NotSelectedTimeoutMs 설정할 프로퍼티 값
         */
        public void setT7NotSelectedTimeoutMs(final long t7NotSelectedTimeoutMs) {
            this.t7NotSelectedTimeoutMs = t7NotSelectedTimeoutMs;
        }

        
        /**
         * getT8NetworkInterleaveTimeoutMs 프로퍼티 값을 반환합니다.
         *
         * @return 프로퍼티 값
         */
        public long getT8NetworkInterleaveTimeoutMs() {
            return t8NetworkInterleaveTimeoutMs;
        }

        
        /**
         * setT8NetworkInterleaveTimeoutMs 프로퍼티 값을 설정합니다.
         *
         * @param t8NetworkInterleaveTimeoutMs 설정할 프로퍼티 값
         */
        public void setT8NetworkInterleaveTimeoutMs(final long t8NetworkInterleaveTimeoutMs) {
            this.t8NetworkInterleaveTimeoutMs = t8NetworkInterleaveTimeoutMs;
        }
    }
}
