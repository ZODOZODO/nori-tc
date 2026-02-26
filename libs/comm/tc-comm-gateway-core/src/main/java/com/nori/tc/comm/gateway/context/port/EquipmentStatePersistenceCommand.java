package com.nori.tc.comm.gateway.context.port;

import java.util.Objects;

/**
 * 설비 상태/이력 영속화 입력 커맨드입니다.
 *
 * <p>DB 접근 최소화를 위해 호출부가 이미 보유한 설비 메타(eqpKey/eqpId)를
 * 영속화 계층으로 직접 전달하도록 만든 커맨드 타입입니다.</p>
 *
 * <p>이 타입은 DB 도메인 enum에 직접 의존하지 않도록 문자열 기반으로 설계하여,
 * 코어 계층이 DB 구현 세부사항에 결합되지 않도록 유지합니다.</p>
 */
public record EquipmentStatePersistenceCommand(
        Long eqpKey,
        String eqpId,
        String traceId,
        String eventType,
        String reasonCode,
        String detailMessage,
        String fromControlState,
        String fromEqpState,
        String toControlState,
        String toEqpState
) {

    /**
     * 커맨드 생성 시 필수 입력을 검증합니다.
     */
    public EquipmentStatePersistenceCommand {
        if (eqpKey == null || eqpKey <= 0L) {
            throw new IllegalArgumentException("eqpKey is required");
        }
        if (eqpId == null || eqpId.isBlank()) {
            throw new IllegalArgumentException("eqpId is required");
        }
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId is required");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType is required");
        }
    }

    /**
     * CREATE/UPDATE 이력 기록용 커맨드를 생성합니다.
     *
     * @param eqpKey 설비 키
     * @param eqpId 설비 ID
     * @param traceId traceId
     * @param eventType UI 이벤트 타입(EQP_CREATE/EQP_UPDATE)
     * @param detailMessage 상세 메시지
     * @param fromEqpState 이전 설비 상태 문자열(선택)
     * @return CREATE/UPDATE 기록 커맨드
     */
    public static EquipmentStatePersistenceCommand forCreateOrUpdate(
            final Long eqpKey,
            final String eqpId,
            final String traceId,
            final String eventType,
            final String detailMessage,
            final String fromEqpState
    ) {
        return new EquipmentStatePersistenceCommand(
                eqpKey,
                eqpId,
                traceId,
                eventType,
                eventType,
                detailMessage,
                null,
                fromEqpState,
                null,
                "REGISTERED"
        );
    }

    /**
     * START 결과 기록용 커맨드를 생성합니다.
     *
     * @param eqpKey 설비 키
     * @param eqpId 설비 ID
     * @param traceId traceId
     * @param detailMessage 상세 메시지
     * @param fromControlState 이전 제어 상태 문자열(선택)
     * @return START 기록 커맨드
     */
    public static EquipmentStatePersistenceCommand forStart(
            final Long eqpKey,
            final String eqpId,
            final String traceId,
            final String detailMessage,
            final String fromControlState
    ) {
        return new EquipmentStatePersistenceCommand(
                eqpKey,
                eqpId,
                traceId,
                "EQP_START",
                "EQP_START",
                detailMessage,
                fromControlState,
                null,
                "REMOTE",
                "IDLE"
        );
    }

    /**
     * END 결과 기록용 커맨드를 생성합니다.
     *
     * @param eqpKey 설비 키
     * @param eqpId 설비 ID
     * @param traceId traceId
     * @param detailMessage 상세 메시지
     * @param fromControlState 이전 제어 상태 문자열(선택)
     * @return END 기록 커맨드
     */
    public static EquipmentStatePersistenceCommand forEnd(
            final Long eqpKey,
            final String eqpId,
            final String traceId,
            final String detailMessage,
            final String fromControlState
    ) {
        return new EquipmentStatePersistenceCommand(
                eqpKey,
                eqpId,
                traceId,
                "EQP_END",
                "EQP_END",
                detailMessage,
                fromControlState,
                null,
                "OFFLINE",
                "DOWN"
        );
    }

    /**
     * DELETE 결과 기록용 커맨드를 생성합니다.
     *
     * @param eqpKey 설비 키
     * @param eqpId 설비 ID
     * @param traceId traceId
     * @param detailMessage 상세 메시지
     * @param fromEqpState 이전 설비 상태 문자열(선택)
     * @return DELETE 기록 커맨드
     */
    public static EquipmentStatePersistenceCommand forDelete(
            final Long eqpKey,
            final String eqpId,
            final String traceId,
            final String detailMessage,
            final String fromEqpState
    ) {
        return new EquipmentStatePersistenceCommand(
                eqpKey,
                eqpId,
                traceId,
                "EQP_DELETE",
                "EQP_DELETE",
                detailMessage,
                null,
                fromEqpState,
                "OFFLINE",
                "DELETED"
        );
    }

    /**
     * reasonCode가 비어 있으면 eventType으로 보정된 새 커맨드를 반환합니다.
     *
     * @return reasonCode가 보정된 커맨드
     */
    public EquipmentStatePersistenceCommand normalizeReasonCodeIfBlank() {
        final String normalizedReasonCode = (reasonCode == null || reasonCode.isBlank()) ? eventType : reasonCode.trim();
        if (Objects.equals(normalizedReasonCode, reasonCode)) {
            return this;
        }
        return new EquipmentStatePersistenceCommand(
                eqpKey,
                eqpId,
                traceId,
                eventType,
                normalizedReasonCode,
                detailMessage,
                fromControlState,
                fromEqpState,
                toControlState,
                toEqpState
        );
    }
}
