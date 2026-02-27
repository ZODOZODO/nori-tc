package com.nori.tc.comm.core.inbound;

/**
 * Netty 채널에서 수신된 raw bytes chunk(코어 모델)
 *
 * 원칙
 * - channelRead에서는 파싱하지 않고, 이 chunk만 만들어 eqp별 큐에 적재합니다.
 * - chunk 자체는 "프레임"이 아닐 수 있습니다(부분 조각).
 *
 * 필드
 * - bytes             : 수신 raw bytes
 * - receivedAtEpochMs : 수신 시각(epoch millis)
 */
public record InboundChunk(
        byte[] bytes,
        long receivedAtEpochMs,
        String traceId
) {
    public InboundChunk {
        if (bytes == null) {
            throw new IllegalArgumentException("bytes is required");
        }
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId is required");
        }
    }
}
