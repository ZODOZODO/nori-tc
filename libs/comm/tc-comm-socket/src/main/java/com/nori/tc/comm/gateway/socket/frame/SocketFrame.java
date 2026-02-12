package com.nori.tc.comm.gateway.socket.frame;

/**
 * SOCKET 프레임(추출 결과)
 *
 * 정의
 * - "프레임"은 socketType별 규칙에 따라 경계가 결정된 1개의 메시지 단위 bytes 입니다.
 *
 * 주의
 * - HSMS처럼 length 기반이 아니라,
 *   라인 구분/패턴 구분/헤더+길이/커스텀 프로토콜 등 다양할 수 있습니다.
 * - 따라서 실제 "추출 규칙"은 socketTypeHandler 쪽에서 다루고,
 *   여기서는 결과만 표준화합니다.
 */
public record SocketFrame(
        byte[] bytes,
        long extractedAtEpochMs
) {
    public SocketFrame {
        if (bytes == null) {
            throw new IllegalArgumentException("bytes is required");
        }
    }
}
