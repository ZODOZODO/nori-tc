package com.nori.tc.messaging.kafka.starter.contract;

import java.util.Map;

/**
 * Common Kafka event message contract for comm apps.
 *
 * This DTO defines the on-the-wire JSON structure used for outbound events.
 */
public record KafkaEventMessage(
        String equipmentId,
        String traceNo,
        String commInterfaceType,
        String socketType,
        String messageName,
        long occurredAt,
        Map<String, String> attributes,
        Object body
) {
    public KafkaEventMessage {
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
}
