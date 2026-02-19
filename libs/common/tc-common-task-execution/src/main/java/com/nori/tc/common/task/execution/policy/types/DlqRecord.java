package com.nori.tc.common.task.execution.policy.types;

/**
 * DLQ 저장용 실패 레코드입니다.
 *
 * @param sourceTopic 원본 토픽
 * @param sourcePartition 원본 파티션
 * @param sourceOffset 원본 오프셋
 * @param eqpId 장비 식별자
 * @param messageType 메시지 타입
 * @param messageName 메시지 명
 * @param failureCategory 실패 카테고리
 * @param exceptionClass 예외 클래스명
 * @param exceptionMessage 예외 메시지
 * @param attempts 시도 횟수
 * @param payloadRef 원문 payload 참조 키
 * @param occurredAtEpochMs 발생 시각(epoch millis)
 */
public record DlqRecord(
        String sourceTopic,
        int sourcePartition,
        long sourceOffset,
        String eqpId,
        String messageType,
        String messageName,
        TaskFailureCategory failureCategory,
        String exceptionClass,
        String exceptionMessage,
        int attempts,
        String payloadRef,
        long occurredAtEpochMs
) {

    /**
     * 저장 가능한 최소 유효성을 검증합니다.
     */
    public DlqRecord {
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
        if (failureCategory == null) {
            throw new IllegalArgumentException("failureCategory is required");
        }
        if (exceptionClass == null || exceptionClass.isBlank()) {
            throw new IllegalArgumentException("exceptionClass is required");
        }
        if (exceptionMessage == null) {
            throw new IllegalArgumentException("exceptionMessage is required");
        }
        if (attempts <= 0) {
            throw new IllegalArgumentException("attempts must be >= 1");
        }
        if (payloadRef == null || payloadRef.isBlank()) {
            throw new IllegalArgumentException("payloadRef is required");
        }
        if (occurredAtEpochMs < 0L) {
            throw new IllegalArgumentException("occurredAtEpochMs must be >= 0");
        }
    }
}