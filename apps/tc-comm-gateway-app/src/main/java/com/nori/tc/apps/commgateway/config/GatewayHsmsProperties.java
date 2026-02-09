package com.nori.tc.apps.commgateway.config;

import com.nori.tc.comm.hsms.config.HsmsSessionConfig;
import com.nori.tc.comm.hsms.config.HsmsTimerConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * HSMS 기본 설정
 *
 * 설비별로 다른 값이 필요한 경우
 * - DB/Redis에서 eqp별 설정을 가져와 이 값을 override 하십시오.
 */
@ConfigurationProperties(prefix = "tc.comm.gateway.hsms")
public class GatewayHsmsProperties {

    private int deviceId = 0;

    private final Timer timer = new Timer();

    private boolean linktestEnabled = true;

    private long linktestIntervalMs = 30_000L;

    private int maxFrameBytes = 256 * 1024;

    private boolean requireSelectBeforeData = true;

    public int getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(final int deviceId) {
        this.deviceId = deviceId;
    }

    public Timer getTimer() {
        return timer;
    }

    public boolean isLinktestEnabled() {
        return linktestEnabled;
    }

    public void setLinktestEnabled(final boolean linktestEnabled) {
        this.linktestEnabled = linktestEnabled;
    }

    public long getLinktestIntervalMs() {
        return linktestIntervalMs;
    }

    public void setLinktestIntervalMs(final long linktestIntervalMs) {
        this.linktestIntervalMs = linktestIntervalMs;
    }

    public int getMaxFrameBytes() {
        return maxFrameBytes;
    }

    public void setMaxFrameBytes(final int maxFrameBytes) {
        this.maxFrameBytes = maxFrameBytes;
    }

    public boolean isRequireSelectBeforeData() {
        return requireSelectBeforeData;
    }

    public void setRequireSelectBeforeData(final boolean requireSelectBeforeData) {
        this.requireSelectBeforeData = requireSelectBeforeData;
    }

    /**
     * HSMS 세션 설정으로 변환합니다.
     * - deviceId만 eqp별 override 가능하므로 파라미터로 받습니다.
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
        private long t3ReplyTimeoutMs = 45_000L;
        private long t5ConnectTimeoutMs = 10_000L;
        private long t6ControlTimeoutMs = 5_000L;
        private long t7NotSelectedTimeoutMs = 10_000L;
        private long t8NetworkInterleaveTimeoutMs = 5_000L;

        public long getT3ReplyTimeoutMs() {
            return t3ReplyTimeoutMs;
        }

        public void setT3ReplyTimeoutMs(final long t3ReplyTimeoutMs) {
            this.t3ReplyTimeoutMs = t3ReplyTimeoutMs;
        }

        public long getT5ConnectTimeoutMs() {
            return t5ConnectTimeoutMs;
        }

        public void setT5ConnectTimeoutMs(final long t5ConnectTimeoutMs) {
            this.t5ConnectTimeoutMs = t5ConnectTimeoutMs;
        }

        public long getT6ControlTimeoutMs() {
            return t6ControlTimeoutMs;
        }

        public void setT6ControlTimeoutMs(final long t6ControlTimeoutMs) {
            this.t6ControlTimeoutMs = t6ControlTimeoutMs;
        }

        public long getT7NotSelectedTimeoutMs() {
            return t7NotSelectedTimeoutMs;
        }

        public void setT7NotSelectedTimeoutMs(final long t7NotSelectedTimeoutMs) {
            this.t7NotSelectedTimeoutMs = t7NotSelectedTimeoutMs;
        }

        public long getT8NetworkInterleaveTimeoutMs() {
            return t8NetworkInterleaveTimeoutMs;
        }

        public void setT8NetworkInterleaveTimeoutMs(final long t8NetworkInterleaveTimeoutMs) {
            this.t8NetworkInterleaveTimeoutMs = t8NetworkInterleaveTimeoutMs;
        }
    }
}
