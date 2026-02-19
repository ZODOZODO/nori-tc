package com.nori.tc.common.task.execution.policy.types;

import java.util.Objects;

/**
 * 실패 처리 정책 판단에 필요한 컨텍스트입니다.
 *
 * @param sourceTopic 원본 토픽
 * @param sourcePartition 원본 파티션
 * @param sourceOffset 원본 오프셋
 * @param eqpId 장비 식별자
 * @param messageType 메시지 타입(EQP/MES/UI 등)
 * @param messageName 메시지 명
 * @param attempt 현재 시도 횟수(1부터 시작)
 * @param payloadRef 원문 payload 저장 참조 키
 * @param failureCategory 1차 분류 실패 유형
 * @param failure 원인 예외
 * @param timeoutTriggered timeout 인터럽트 발생 여부
 * @param occurredAtEpochMs 실패 발생 시각(epoch millis)
 */
public record TaskFailureContext(
        String sourceTopic,
        int sourcePartition,
        long sourceOffset,
        String eqpId,
        String messageType,
        String messageName,
        int attempt,
        String payloadRef,
        TaskFailureCategory failureCategory,
        Throwable failure,
        boolean timeoutTriggered,
        long occurredAtEpochMs
) {

    /**
     * 필수 필드의 기본 유효성을 검증합니다.
     */
    public TaskFailureContext {
        if (sourceTopic == null || sourceTopic.isBlank()) {
            throw new IllegalArgumentException("sourceTopic is required");
        }
        if (sourcePartition < 0) {
            throw new IllegalArgumentException("sourcePartition must be >= 0");
        }
        if (sourceOffset < 0L) {
            throw new IllegalArgumentException("sourceOffset must be >= 0");
        }
        if (eqpId == null || eqpId.isBlank()) {
            throw new IllegalArgumentException("eqpId is required");
        }
        if (messageType == null || messageType.isBlank()) {
            throw new IllegalArgumentException("messageType is required");
        }
        if (messageName == null || messageName.isBlank()) {
            throw new IllegalArgumentException("messageName is required");
        }
        if (attempt <= 0) {
            throw new IllegalArgumentException("attempt must be >= 1");
        }
        if (payloadRef == null || payloadRef.isBlank()) {
            throw new IllegalArgumentException("payloadRef is required");
        }
        Objects.requireNonNull(failureCategory, "failureCategory is null");
        if (occurredAtEpochMs < 0L) {
            throw new IllegalArgumentException("occurredAtEpochMs must be >= 0");
        }
    }
}