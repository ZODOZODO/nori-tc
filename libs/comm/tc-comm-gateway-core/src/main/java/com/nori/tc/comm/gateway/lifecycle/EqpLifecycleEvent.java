package com.nori.tc.comm.gateway.lifecycle;

import com.nori.tc.common.mailbox.MailboxTask;

import java.util.Objects;

/**
 * 설비(eqpid) 단위 lifecycle 상태 전이를 표현하는 내부 이벤트입니다.
 *
 * <p>이벤트는 상태머신의 mailbox 스케줄러를 통해 eqpId 단위로 직렬 처리됩니다.</p>
 */
public record EqpLifecycleEvent(
        String eqpId,
        EqpLifecycleEventType eventType,
        String traceId,
        long stateVersion,
        long timeoutMs,
        String reason,
        long createdAtEpochMs
) implements MailboxTask {

    /**
     * 생성 시 공통 검증/정규화를 수행합니다.
     */
    public EqpLifecycleEvent {
        eqpId = normalizeEqpId(eqpId);
        eventType = Objects.requireNonNull(eventType, "eventType is null");
        traceId = normalizeTraceId(traceId);
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
     * MailboxScheduler 라우팅 키로 eqpId를 반환합니다.
     */
    @Override
    public String routingKey() {
        return eqpId;
    }

    /**
     * START 요청 이벤트를 생성합니다.
     */
    public static EqpLifecycleEvent startRequested(
            final String eqpId,
            final String traceId,
            final long stateVersion,
            final long timeoutMs
    ) {
        return new EqpLifecycleEvent(
                eqpId,
                EqpLifecycleEventType.START_REQUESTED,
                traceId,
                stateVersion,
                timeoutMs,
                "UI_START",
                System.currentTimeMillis()
        );
    }

    /**
     * END 요청 이벤트를 생성합니다.
     */
    public static EqpLifecycleEvent endRequested(
            final String eqpId,
            final String traceId,
            final long stateVersion,
            final long timeoutMs
    ) {
        return new EqpLifecycleEvent(
                eqpId,
                EqpLifecycleEventType.END_REQUESTED,
                traceId,
                stateVersion,
                timeoutMs,
                "UI_END",
                System.currentTimeMillis()
        );
    }

    /**
     * START timeout 이벤트를 생성합니다.
     */
    public static EqpLifecycleEvent startTimeout(
            final String eqpId,
            final String traceId,
            final long stateVersion
    ) {
        return new EqpLifecycleEvent(
                eqpId,
                EqpLifecycleEventType.START_TIMEOUT,
                traceId,
                stateVersion,
                0L,
                "START_TIMEOUT",
                System.currentTimeMillis()
        );
    }

    /**
     * START 실패 이벤트를 생성합니다.
     *
     * <p>예: 아웃바운드 재시도 소진 등으로 더 이상 START 전이 진행이 불가능한 경우</p>
     */
    public static EqpLifecycleEvent startFailed(
            final String eqpId,
            final String traceId,
            final long stateVersion,
            final String reason
    ) {
        return new EqpLifecycleEvent(
                eqpId,
                EqpLifecycleEventType.START_FAILED,
                traceId,
                stateVersion,
                0L,
                reason,
                System.currentTimeMillis()
        );
    }

    /**
     * END timeout 이벤트를 생성합니다.
     */
    public static EqpLifecycleEvent endTimeout(
            final String eqpId,
            final String traceId,
            final long stateVersion
    ) {
        return new EqpLifecycleEvent(
                eqpId,
                EqpLifecycleEventType.END_TIMEOUT,
                traceId,
                stateVersion,
                0L,
                "END_TIMEOUT",
                System.currentTimeMillis()
        );
    }

    /**
     * 채널 CONNECTED 이벤트를 생성합니다.
     */
    public static EqpLifecycleEvent channelConnected(
            final String eqpId,
            final String traceId,
            final String reason
    ) {
        return new EqpLifecycleEvent(
                eqpId,
                EqpLifecycleEventType.CHANNEL_CONNECTED,
                traceId,
                0L,
                0L,
                reason,
                System.currentTimeMillis()
        );
    }

    /**
     * 채널 DISCONNECTED 이벤트를 생성합니다.
     */
    public static EqpLifecycleEvent channelDisconnected(
            final String eqpId,
            final String traceId,
            final String reason
    ) {
        return new EqpLifecycleEvent(
                eqpId,
                EqpLifecycleEventType.CHANNEL_DISCONNECTED,
                traceId,
                0L,
                0L,
                reason,
                System.currentTimeMillis()
        );
    }

    /**
     * normalizeEqpId 기능을 수행합니다.
     *
     * @param eqpId 입력 값
     * @return 처리 결과
     */

    private static String normalizeEqpId(final String eqpId) {
        if (eqpId == null || eqpId.isBlank()) {
            throw new IllegalArgumentException("eqpId is required");
        }
        return eqpId.trim();
    }

    /**
     * normalizeTraceId 기능을 수행합니다.
     *
     * @param traceId 입력 값
     * @return 처리 결과
     */

    private static String normalizeTraceId(final String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return "N/A";
        }
        return traceId.trim();
    }

    /**
     * lifecycle 상태머신 이벤트 타입입니다.
     */
    public enum EqpLifecycleEventType {
        START_REQUESTED,
        END_REQUESTED,
        CHANNEL_CONNECTED,
        CHANNEL_DISCONNECTED,
        START_TIMEOUT,
        START_FAILED,
        END_TIMEOUT
    }
}
