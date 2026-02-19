package com.nori.tc.common.task.policy;

/**
 * 공통 DLQ 레코드 표준입니다.
 *
 * <p>요구사항 정책에 따라 payload 본문은 포함하지 않고 payloadRef만 기록합니다.</p>
 *
 * @param sourceTopic 원본 토픽
 * @param sourcePartition 원본 파티션
 * @param sourceOffset 원본 오프셋
 * @param eqpId 설비 ID
 * @param messageType 메시지 타입(EQP/MES/UI)
 * @param messageName 메시지 이름
 * @param failureCategory 실패 카테고리
 * @param exceptionClass 예외 클래스명
 * @param exceptionMessage 예외 메시지(길이 제한 적용)
 * @param attempts 누적 시도 횟수
 * @param payloadRef payload 참조 키
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
     * DLQ 레코드 생성 시 기본 유효성 검증을 수행합니다.
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

