package com.nori.tc.comm.gateway.socket.socketType;

/**
 * socketType encode 결과(표준)
 *
 * - bytes : TCP로 송신할 raw bytes
 * - description : 운영/로그용 설명
 */
public record SocketTypeEncodeResult(
        byte[] bytes,
        String description
) {
    public SocketTypeEncodeResult {
        if (bytes == null) throw new IllegalArgumentException("bytes is required");
        if (description == null) description = "";
    }
}
