package com.nori.tc.comm.gateway.socket.socketType.core;

import java.util.Map;

/**
 * socketType decode 결과(표준)
 *
 * - messageName : 라우팅 정책의 키(OUTBOX/DIRECT 결정)
 * - attributes  : 부가 메타(예: seq, cmd, subtype 등)
 * - body        : 파싱된 본문(직렬화는 adapter에서)
 */
public record SocketTypeDecodeResult(
        String messageName,
        Map<String, String> attributes,
        Object body
) {
    public SocketTypeDecodeResult {
        if (messageName == null || messageName.isBlank()) {
            throw new IllegalArgumentException("messageName is required");
        }
        if (attributes == null) attributes = Map.of();
    }
}
