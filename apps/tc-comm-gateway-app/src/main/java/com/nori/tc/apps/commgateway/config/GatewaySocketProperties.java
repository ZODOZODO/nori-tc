package com.nori.tc.apps.commgateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SOCKET 기본 설정
 */
@ConfigurationProperties(prefix = "tc.comm.gateway.socket")
public class GatewaySocketProperties {

    private String defaultSocketType = "LINE_DELIMITED";

    private int maxFrameBytes = 256 * 1024;

    private boolean allowEmptyFrame = false;

    /**
     * REGEX_DELIMITED 핸들러의 종료 패턴(ASCII 기반 권장)
     */
    private String regexEndPattern = "END\\n";

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
