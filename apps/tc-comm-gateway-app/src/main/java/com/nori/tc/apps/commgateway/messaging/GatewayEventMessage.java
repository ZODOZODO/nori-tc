package com.nori.tc.apps.commgateway.messaging;

import java.util.Map;

/**
 * Kafka로 발행하는 이벤트 메시지 모델
 */
public record GatewayEventMessage(
        String equipmentId,
        String traceNo,
        String commInterfaceType,
        String socketType,
        String messageName,
        long occurredAt,
        Map<String, String> attributes,
        Object body
) {
    public GatewayEventMessage {
        if (equipmentId == null || equipmentId.isBlank()) {
            throw new IllegalArgumentException("equipmentId is required");
        }
        if (traceNo == null || traceNo.isBlank()) {
            throw new IllegalArgumentException("traceNo is required");
        }
        if (commInterfaceType == null || commInterfaceType.isBlank()) {
            throw new IllegalArgumentException("commInterfaceType is required");
        }
        if (messageName == null || messageName.isBlank()) {
            throw new IllegalArgumentException("messageName is required");
        }
    }

    public static GatewayEventMessage fromParsed(
            final String equipmentId,
            final String traceNo,
            final String commInterfaceType,
            final String socketType,
            final String messageName,
            final long occurredAt,
            final Map<String, String> attributes,
            final Object body
    ) {
        return new GatewayEventMessage(
                equipmentId,
                traceNo,
                commInterfaceType,
                socketType,
                messageName,
                occurredAt,
                attributes,
                body
        );
    }
}
