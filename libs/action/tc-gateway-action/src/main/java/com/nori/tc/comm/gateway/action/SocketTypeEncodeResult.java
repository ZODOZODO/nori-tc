package com.nori.tc.comm.gateway.action;

/**
 * socketType encode 결과(표준)
 *
 * <p>필드:</p>
 * <p>- bytes : TCP로 송신할 raw bytes</p>
 * <p>- description : 운영/로그용 설명</p>
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
