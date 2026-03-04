package com.nori.tc.comm.adapters.plugin.socket;

/**
 * 플러그인 보안 정책 위반 시 발생하는 런타임 예외입니다.
 *
 * <p>사용 목적:</p>
 * <ul>
 *   <li>SHA-256 allowlist 미일치</li>
 *   <li>서명 검증 실패(향후 확장)</li>
 *   <li>신뢰 발행자 검증 실패(향후 확장)</li>
 * </ul>
 *
 * <p>일반 로딩 실패 예외와 분리해 운영 로그/알람에서
 * 보안성 실패를 별도 코드로 식별할 수 있게 합니다.</p>
 */
public class PluginSecurityException extends RuntimeException {

    /**
     * 메시지만 포함하는 보안 예외를 생성합니다.
     *
     * @param message 예외 메시지
     */
    public PluginSecurityException(final String message) {
        super(message);
    }

    /**
     * 원인 예외를 포함하는 보안 예외를 생성합니다.
     *
     * @param message 예외 메시지
     * @param cause   원인 예외
     */
    public PluginSecurityException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
