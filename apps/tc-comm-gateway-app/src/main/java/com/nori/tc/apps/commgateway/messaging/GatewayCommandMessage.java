package com.nori.tc.apps.commgateway.messaging;

import java.util.Map;

/**
 * Kafka에서 수신하는 커맨드 메시지 모델
 *
 * - payloadBase64는 설비로 전송할 raw frame(bytes)를 Base64로 인코딩한 값입니다.
 */
public record GatewayCommandMessage(
        String equipmentId,
        String commInterfaceType,
        String socketType,
        String payloadBase64,
        Map<String, String> attributes
) {
    public GatewayCommandMessage {
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
