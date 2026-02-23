package com.nori.tc.comm.gateway.lifecycle.model;

import java.util.Objects;

/**
 * lifecycle 상태머신이 확정한 전이 결과입니다.
 *
 * <p>이 모델은 START/END 전이의 최종 상태(APPLIED/FAILED)를
 * 외부 어댑터(UI 지연 응답 발행 등)에 전달하기 위한 표준 이벤트입니다.</p>
 *
 * @param eqpId 대상 설비 ID
 * @param transition 전이 타입(START/END)
 * @param status 최종 상태(APPLIED/FAILED)
 * @param traceId 연관 traceId
 * @param stateVersion 전이 상태 버전
 * @param reason 결과 사유 코드/메시지
 */
public record EquipmentLifecycleOutcome(
        String eqpId,
        Transition transition,
        Status status,
        String traceId,
        long stateVersion,
        String reason
) {

    /**
     * 생성 시 필드 유효성을 검증합니다.
     */
    public EquipmentLifecycleOutcome {
        if (eqpId == null || eqpId.isBlank()) {
            throw new IllegalArgumentException("eqpId is required");
        }
        eqpId = eqpId.trim();
        transition = Objects.requireNonNull(transition, "transition is null");
        status = Objects.requireNonNull(status, "status is null");
        if (traceId == null || traceId.isBlank()) {
            traceId = "N/A";
        } else {
            traceId = traceId.trim();
        }
        if (stateVersion < 0L) {
            throw new IllegalArgumentException("stateVersion must be >= 0");
        }
        reason = reason == null ? "" : reason.trim();
    }

    /**
     * START 전이 성공 결과를 생성합니다.
     */
    public static EquipmentLifecycleOutcome startApplied(
            final String eqpId,
            final String traceId,
            final long stateVersion,
            final String reason
    ) {
        return new EquipmentLifecycleOutcome(eqpId, Transition.START, Status.APPLIED, traceId, stateVersion, reason);
    }

    /**
     * START 전이 실패 결과를 생성합니다.
     */
    public static EquipmentLifecycleOutcome startFailed(
            final String eqpId,
            final String traceId,
            final long stateVersion,
            final String reason
    ) {
        return new EquipmentLifecycleOutcome(eqpId, Transition.START, Status.FAILED, traceId, stateVersion, reason);
    }

    /**
     * END 전이 성공 결과를 생성합니다.
     */
    public static EquipmentLifecycleOutcome endApplied(
            final String eqpId,
            final String traceId,
            final long stateVersion,
            final String reason
    ) {
        return new EquipmentLifecycleOutcome(eqpId, Transition.END, Status.APPLIED, traceId, stateVersion, reason);
    }

    /**
     * END 전이 실패 결과를 생성합니다.
     */
    public static EquipmentLifecycleOutcome endFailed(
            final String eqpId,
            final String traceId,
            final long stateVersion,
            final String reason
    ) {
        return new EquipmentLifecycleOutcome(eqpId, Transition.END, Status.FAILED, traceId, stateVersion, reason);
    }

    /**
     * lifecycle 전이 타입입니다.
     */
    public enum Transition {
        START,
        END
    }

    /**
     * lifecycle 전이 확정 상태입니다.
     */
    public enum Status {
        APPLIED,
        FAILED
    }
}

