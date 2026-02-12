package com.nori.tc.apps.commgateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import jakarta.annotation.PostConstruct;

/**
 * SOCKET 기본 설정
 */
@ConfigurationProperties(prefix = "tc.comm.gateway.socket")
public class GatewaySocketProperties {

    private String defaultSocketType;

    private Integer maxFrameBytes;

    private Boolean allowEmptyFrame;

    /**
     * REGEX_DELIMITED 핸들러의 종료 패턴(ASCII 기반 권장)
     */
    private String regexEndPattern;

    @PostConstruct
    public void validate() {
        if (defaultSocketType == null || defaultSocketType.isBlank()) {
            throw new IllegalStateException("tc.comm.gateway.socket.default-socket-type is required");
        }
        if (maxFrameBytes == null || maxFrameBytes <= 0) {
            throw new IllegalStateException("tc.comm.gateway.socket.max-frame-bytes must be > 0");
        }
        if (allowEmptyFrame == null) {
            throw new IllegalStateException("tc.comm.gateway.socket.allow-empty-frame is required");
        }
        if (regexEndPattern == null || regexEndPattern.isBlank()) {
            throw new IllegalStateException("tc.comm.gateway.socket.regex-end-pattern is required");
        }
    }

    public String getDefaultSocketType() {
        return defaultSocketType;
    }

    public void setDefaultSocketType(final String defaultSocketType) {
        this.defaultSocketType = defaultSocketType;
    }

    public int getMaxFrameBytes() {
        return maxFrameBytes;
    }

    public void setMaxFrameBytes(final int maxFrameBytes) {
        this.maxFrameBytes = maxFrameBytes;
    }

    public boolean isAllowEmptyFrame() {
        return allowEmptyFrame;
    }

    public void setAllowEmptyFrame(final boolean allowEmptyFrame) {
        this.allowEmptyFrame = allowEmptyFrame;
    }

    public String getRegexEndPattern() {
        return regexEndPattern;
    }

    public void setRegexEndPattern(final String regexEndPattern) {
        this.regexEndPattern = regexEndPattern;
    }
}
