package com.nori.tc.common.task.policy;

import java.util.Objects;

/**
 * 실패 처리 정책 평가에 필요한 공통 컨텍스트입니다.
 *
 * @param sourceTopic 실패가 발생한 원본 토픽
 * @param sourcePartition 실패가 발생한 원본 파티션
 * @param sourceOffset 실패가 발생한 원본 오프셋
 * @param eqpId 라우팅 기준 설비 ID
 * @param messageType 메시지 타입(EQP/MES/UI 등)
 * @param messageName 메시지 이름
 * @param attempt 현재 시도 횟수(1부터 시작)
 * @param payloadRef DLQ에 기록할 payload 참조 키
 * @param failureCategory 1차 분류 카테고리
 * @param failure 실제 실패 예외(없을 수 있음)
 * @param timeoutTriggered timeout guard 트리거 여부
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
     * 컨텍스트 생성 시 기본 유효성 검증을 수행합니다.
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

