package com.nori.tc.messaging.core.port;

import java.util.Map;

/**
 * 메시지 발행 요청(기술 중립)
 *
 * @param topic   발행 토픽(필수)
 * @param key     파티셔닝 키(옵션)
 * @param payload 메시지 페이로드(직렬화는 adapter에서)
 * @param headers 헤더(옵션). null 대신 빈 맵 권장
 */
public record MessagePublishRequest(
        String topic,
        String key,
        Object payload,
        Map<String, String> headers
) {
    public MessagePublishRequest {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("topic is required");
        }
        if (headers == null) {
            headers = Map.of();
        }
    }
}
