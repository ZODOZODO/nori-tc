package com.nori.tc.apps.commgateway.config;

import com.nori.tc.comm.hsms.config.HsmsSessionConfig;
import com.nori.tc.comm.hsms.config.HsmsTimerConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;

import jakarta.annotation.PostConstruct;

/**
 * HSMS default configuration.
 *
 * - Equipment-specific overrides can be loaded from DB/Redis at runtime.
 */
@ConfigurationProperties(prefix = "tc.comm.gateway.hsms")
public class GatewayHsmsProperties {

    private Integer deviceId;

    private final Timer timer = new Timer();

    private Boolean linktestEnabled;

    private Long linktestIntervalMs;

    private Integer maxFrameBytes;

    private Boolean requireSelectBeforeData;

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
    }

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
