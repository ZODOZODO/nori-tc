package com.nori.tc.business.domain.runtime;

import java.util.Objects;

/**
 * Business Core 런타임에 유입되는 입력 레코드 표준 모델입니다.
 *
 * @param topic 원본 토픽
 * @param partition 원본 파티션
 * @param offset 원본 오프셋
 * @param eqpId 라우팅 기준 설비 ID
 * @param messageType 메시지 타입(EQP/MES/UI)
 * @param messageName 메시지 이름
 * @param payloadRef payload 참조 키
 * @param payload 원본 payload(옵션)
 */
public record BusinessInboundRecord(
        String topic,
        int partition,
        long offset,
        String eqpId,
        BusinessMessageType messageType,
        String messageName,
        String payloadRef,
        String payload
) {

    /**
     * 레코드 생성 시 최소 유효성 검증을 수행합니다.
     */
    public BusinessInboundRecord {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("topic is required");
        }
        if (partition < 0) {
            throw new IllegalArgumentException("partition must be >= 0");
        }
        if (offset < 0L) {
            throw new IllegalArgumentException("offset must be >= 0");
        }
        if (eqpId == null || eqpId.isBlank()) {
            throw new IllegalArgumentException("eqpId is required");
        }
        Objects.requireNonNull(messageType, "messageType is null");
        if (messageName == null || messageName.isBlank()) {
            throw new IllegalArgumentException("messageName is required");
        }
        if (payloadRef == null || payloadRef.isBlank()) {
            throw new IllegalArgumentException("payloadRef is required");
        }
    }
}


