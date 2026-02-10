package com.nori.tc.messaging.kafka.starter.contract;

import java.util.Map;

/**
 * Common Kafka command message contract for comm apps.
 *
 * This DTO defines the on-the-wire JSON structure that all comm apps
 * are expected to publish/consume for command routing.
 */
public record KafkaCommandMessage(
        String equipmentId,
        String commInterfaceType,
        String socketType,
        String payloadBase64,
        Map<String, String> attributes
) {
    public KafkaCommandMessage {
        if (equipmentId == null || equipmentId.isBlank()) {
            throw new IllegalArgumentException("equipmentId is required");
        }
        if (commInterfaceType == null || commInterfaceType.isBlank()) {
            throw new IllegalArgumentException("commInterfaceType is required");
        }
        if (payloadBase64 == null || payloadBase64.isBlank()) {
            throw new IllegalArgumentException("payloadBase64 is required");
        }
    }
}
