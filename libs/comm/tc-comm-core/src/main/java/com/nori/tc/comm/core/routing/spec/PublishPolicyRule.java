package com.nori.tc.comm.core.routing.spec;

import com.nori.tc.comm.core.routing.PublishMode;

import java.util.Map;

/**
 * 발행 정책 룰(선언형)
 *
 * 예)
 * - EXACT  "S6F11" -> OUTBOX
 * - PREFIX "S1F"   -> DIRECT_KAFKA (단, allow-list로 제한 권장)
 *
 * 주의
 * - DIRECT_KAFKA는 예외 allow-list로만 운영하는 것이 일반적으로 안전합니다.
 */
public record PublishPolicyRule(
        MessageMatchType matchType,
        String pattern,
        PublishMode publishMode,
        String topic,
        String key,
        Map<String, String> headers
) {
    public PublishPolicyRule {
        if (matchType == null) throw new IllegalArgumentException("matchType is required");
        if (pattern == null || pattern.isBlank()) throw new IllegalArgumentException("pattern is required");
        if (publishMode == null) throw new IllegalArgumentException("publishMode is required");
        if (headers == null) headers = Map.of();
    }
}
