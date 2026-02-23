package com.nori.tc.comm.gateway.config.props;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;

/**
 * SOCKET 기본 설정
 */
@ConfigurationProperties(prefix = "tc.comm.gateway.socket")
public class GatewaySocketProperties {

    private static final Logger log = LoggerFactory.getLogger(GatewaySocketProperties.class);

    private String defaultSocketType;

    private Integer maxFrameBytes;

    private Boolean allowEmptyFrame;

    /**
     * REGEX_DELIMITED 핸들러의 종료 패턴(ASCII 기반 권장)
     */
    private String regexEndPattern;

    
    /**
     * 게이트웨이 코어 모듈 입력/설정 유효성을 검증합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     */
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
        log.info("GatewaySocketProperties validated. defaultSocketType={}, maxFrameBytes={}, allowEmptyFrame={}",
                defaultSocketType, maxFrameBytes, allowEmptyFrame);
    }

    
    /**
     * 게이트웨이 코어 모듈의 현재 값을 조회합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @return 게이트웨이 코어 모듈 처리 결과
     */
    public String getDefaultSocketType() {
        return defaultSocketType;
    }

    
    /**
     * 게이트웨이 코어 모듈 설정 값을 반영합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @param defaultSocketType 통신 채널/세션 정보
     */
    public void setDefaultSocketType(final String defaultSocketType) {
        this.defaultSocketType = defaultSocketType;
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
    public boolean isAllowEmptyFrame() {
        return allowEmptyFrame;
    }

    
    /**
     * 게이트웨이 코어 모듈 설정 값을 반영합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @param allowEmptyFrame 게이트웨이 코어 모듈 처리에 사용하는 입력 값
     */
    public void setAllowEmptyFrame(final boolean allowEmptyFrame) {
        this.allowEmptyFrame = allowEmptyFrame;
    }

    
    /**
     * 게이트웨이 코어 모듈의 현재 값을 조회합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @return 게이트웨이 코어 모듈 처리 결과
     */
    public String getRegexEndPattern() {
        return regexEndPattern;
    }

    
    /**
     * 게이트웨이 코어 모듈 설정 값을 반영합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @param regexEndPattern 게이트웨이 코어 모듈 처리에 사용하는 입력 값
     */
    public void setRegexEndPattern(final String regexEndPattern) {
        this.regexEndPattern = regexEndPattern;
    }
}
