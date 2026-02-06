package com.nori.tc.comm.socket.config;

/**
 * socketType 설정(설비별)
 *
 * 의미
 * - tc_eqp_socket.socket_protocol_type 값(당신이 정의한 socket_protocol_type)을 코드에서 "socketType"으로 부릅니다.
 * - socketType별로 프레임 경계(시작/끝/정규식)와 payload 형식이 달라질 수 있습니다.
 *
 * 이 모듈의 목표
 * - 실제 파싱 규칙은 자주 바뀌므로, 핸들러(또는 스크립트)를 교체하기 쉬운 구조로 제공합니다.
 * - 여기서는 최소한의 공통 필드만 둡니다.
 */
public record SocketTypeConfig(
        String socketType,
        int maxFrameBytes,
        boolean allowEmptyFrame
) {
    public SocketTypeConfig {
        if (socketType == null || socketType.isBlank()) {
            throw new IllegalArgumentException("socketType is required");
        }
        if (maxFrameBytes <= 0) {
            throw new IllegalArgumentException("maxFrameBytes must be > 0");
        }
    }

    public static SocketTypeConfig recommendedDefaults(final String socketType) {
        return new SocketTypeConfig(
                socketType,
                256 * 1024, // 기본 상한 256KB
                false
        );
    }
}
