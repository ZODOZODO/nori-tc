package com.nori.tc.comm.gateway.lifecycle.model;

import com.nori.tc.common.mailbox.MailboxTask;

import java.util.Objects;

/**
 * 설비(`eqpId`) 단위 lifecycle 상태 전이를 전달하는 내부 이벤트입니다.
 *
 * <p>이 이벤트는 `MailboxTask`를 구현하므로 `routingKey=eqpId` 기준 직렬 처리 대상이 됩니다.</p>
 * <p>즉, 동일 설비에 대한 START/END/CHANNEL 이벤트는 순서를 보장하면서 처리되고,
 * 서로 다른 설비 이벤트는 병렬로 처리할 수 있습니다.</p>
 */
public record EquipmentLifecycleEvent(
        String eqpId,
        EquipmentLifecycleEventType eventType,
        String traceId,
        long stateVersion,
        long timeoutMs,
        String reason,
        long createdAtEpochMs
) implements MailboxTask {

    /**
     * traceId 미지정 시 사용하는 기본 값입니다.
     */
    private static final String DEFAULT_TRACE_ID = "N/A";

    /**
     * 사유(reason) 상수입니다.
     */
    private static final String REASON_UI_START = "UI_START";
    private static final String REASON_UI_END = "UI_END";
    private static final String REASON_START_TIMEOUT = "START_TIMEOUT";
    private static final String REASON_END_TIMEOUT = "END_TIMEOUT";

    /**
     * 이벤트 생성 시 공통 검증/정규화를 수행합니다.
     *
     * <p>검증 항목:</p>
     * <p>1) eqpId/eventType 필수 여부</p>
     * <p>2) traceId/reason 기본값 정규화</p>
     * <p>3) stateVersion/timeoutMs 음수 방지</p>
     * <p>4) createdAtEpochMs 미지정 시 현재 시각 대체</p>
     */
    public EquipmentLifecycleEvent {
        // 설비 식별자는 mailbox 라우팅 키로 직접 사용되므로 공백/blank를 허용하지 않습니다.
        eqpId = normalizeEqpId(eqpId);
        // 이벤트 타입은 상태머신 분기 기준이므로 null을 허용하지 않습니다.
        eventType = Objects.requireNonNull(eventType, "eventType is null");
        // traceId는 빈 값이 들어와도 추적 가능하도록 기본값으로 정규화합니다.
        traceId = normalizeTraceId(traceId);
        // reason은 null 방지 목적의 최소 정규화만 수행합니다.
        reason = reason == null ? "" : reason;
        if (stateVersion < 0L) {
            throw new IllegalArgumentException("stateVersion must be >= 0");
        }
        if (timeoutMs < 0L) {
            throw new IllegalArgumentException("timeoutMs must be >= 0");
        }
        if (createdAtEpochMs <= 0L) {
            createdAtEpochMs = System.currentTimeMillis();
        }
    }

    /**
     * MailboxScheduler 라우팅 키를 반환합니다.
     *
     * <p>동일 eqpId 이벤트가 단일 실행 흐름으로 직렬화되도록 `eqpId`를 그대로 반환합니다.</p>
     */
    @Override
    public String routingKey() {
        return eqpId;
    }

    /**
     * UI START 요청 이벤트를 생성합니다.
     *
     * @param eqpId 대상 설비 ID
     * @param traceId UI 요청 추적 ID
     * @param stateVersion 요청 시점 상태 버전
     * @param timeoutMs START 완료 제한 시간(ms)
     * @return START_REQUESTED 이벤트
     */
    public static EquipmentLifecycleEvent startRequested(
            final String eqpId,
            final String traceId,
            final long stateVersion,
            final long timeoutMs
    ) {
        return new EquipmentLifecycleEvent(
                eqpId,
                EquipmentLifecycleEventType.START_REQUESTED,
                traceId,
                stateVersion,
                timeoutMs,
                REASON_UI_START,
                System.currentTimeMillis()
        );
    }

    /**
     * UI END 요청 이벤트를 생성합니다.
     *
     * @param eqpId 대상 설비 ID
     * @param traceId UI 요청 추적 ID
     * @param stateVersion 요청 시점 상태 버전
     * @param timeoutMs END 완료 제한 시간(ms)
     * @return END_REQUESTED 이벤트
     */
    public static EquipmentLifecycleEvent endRequested(
            final String eqpId,
            final String traceId,
            final long stateVersion,
            final long timeoutMs
    ) {
        return new EquipmentLifecycleEvent(
                eqpId,
                EquipmentLifecycleEventType.END_REQUESTED,
                traceId,
                stateVersion,
                timeoutMs,
                REASON_UI_END,
                System.currentTimeMillis()
        );
    }

    /**
     * START 타임아웃 이벤트를 생성합니다.
     *
     * @param eqpId 대상 설비 ID
     * @param traceId 요청 추적 ID
     * @param stateVersion 타임아웃 대상 상태 버전
     * @return START_TIMEOUT 이벤트
     */
    public static EquipmentLifecycleEvent startTimeout(
            final String eqpId,
            final String traceId,
            final long stateVersion
    ) {
        return new EquipmentLifecycleEvent(
                eqpId,
                EquipmentLifecycleEventType.START_TIMEOUT,
                traceId,
                stateVersion,
                0L,
                REASON_START_TIMEOUT,
                System.currentTimeMillis()
        );
    }

    /**
     * START 실패 이벤트를 생성합니다.
     *
     * <p>예: 아웃바운드 재시도 소진 등으로 더 이상 START 전이를 진행할 수 없는 경우</p>
     *
     * @param eqpId 대상 설비 ID
     * @param traceId 요청 추적 ID
     * @param stateVersion 실패 시점 상태 버전
     * @param reason 실패 사유
     * @return START_FAILED 이벤트
     */
    public static EquipmentLifecycleEvent startFailed(
            final String eqpId,
            final String traceId,
            final long stateVersion,
            final String reason
    ) {
        return new EquipmentLifecycleEvent(
                eqpId,
                EquipmentLifecycleEventType.START_FAILED,
                traceId,
                stateVersion,
                0L,
                reason,
                System.currentTimeMillis()
        );
    }

    /**
     * END 타임아웃 이벤트를 생성합니다.
     *
     * @param eqpId 대상 설비 ID
     * @param traceId 요청 추적 ID
     * @param stateVersion 타임아웃 대상 상태 버전
     * @return END_TIMEOUT 이벤트
     */
    public static EquipmentLifecycleEvent endTimeout(
            final String eqpId,
            final String traceId,
            final long stateVersion
    ) {
        return new EquipmentLifecycleEvent(
                eqpId,
                EquipmentLifecycleEventType.END_TIMEOUT,
                traceId,
                stateVersion,
                0L,
                REASON_END_TIMEOUT,
                System.currentTimeMillis()
        );
    }

    /**
     * 채널 CONNECTED 이벤트를 생성합니다.
     *
     * @param eqpId 대상 설비 ID
     * @param traceId 연관 trace ID
     * @param reason 연결 사유(예: INITIALIZE_REP_PASS)
     * @return CHANNEL_CONNECTED 이벤트
     */
    public static EquipmentLifecycleEvent channelConnected(
            final String eqpId,
            final String traceId,
            final String reason
    ) {
        return new EquipmentLifecycleEvent(
                eqpId,
                EquipmentLifecycleEventType.CHANNEL_CONNECTED,
                traceId,
                0L,
                0L,
                reason,
                System.currentTimeMillis()
        );
    }

    /**
     * 채널 DISCONNECTED 이벤트를 생성합니다.
     *
     * @param eqpId 대상 설비 ID
     * @param traceId 연관 trace ID
     * @param reason 종료 사유(예: UI_END, START_TIMEOUT)
     * @return CHANNEL_DISCONNECTED 이벤트
     */
    public static EquipmentLifecycleEvent channelDisconnected(
            final String eqpId,
            final String traceId,
            final String reason
    ) {
        return new EquipmentLifecycleEvent(
                eqpId,
                EquipmentLifecycleEventType.CHANNEL_DISCONNECTED,
                traceId,
                0L,
                0L,
                reason,
                System.currentTimeMillis()
        );
    }

    /**
     * eqpId를 정규화합니다.
     *
     * <p>blank 허용 시 mailbox 라우팅/상태 저장 키가 깨지므로 즉시 예외를 발생시킵니다.</p>
     *
     * @param eqpId 입력 eqpId
     * @return trim 처리된 eqpId
     */
    private static String normalizeEqpId(final String eqpId) {
        if (eqpId == null || eqpId.isBlank()) {
            throw new IllegalArgumentException("eqpId is required");
        }
        return eqpId.trim();
    }

    /**
     * traceId를 정규화합니다.
     *
     * <p>traceId가 비어 있으면 공통 기본값(`N/A`)을 사용합니다.</p>
     *
     * @param traceId 입력 traceId
     * @return trim 처리된 traceId 또는 기본값
     */
    private static String normalizeTraceId(final String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return DEFAULT_TRACE_ID;
        }
        return traceId.trim();
    }

    /**
     * lifecycle 상태머신 이벤트 타입 정의입니다.
     *
     * <p>각 타입은 상태머신 전이 조건 및 UI 응답 생성 분기에서 사용됩니다.</p>
     */
    public enum EquipmentLifecycleEventType {
        START_REQUESTED,
        END_REQUESTED,
        CHANNEL_CONNECTED,
        CHANNEL_DISCONNECTED,
        START_TIMEOUT,
        START_FAILED,
        END_TIMEOUT
    }
}
