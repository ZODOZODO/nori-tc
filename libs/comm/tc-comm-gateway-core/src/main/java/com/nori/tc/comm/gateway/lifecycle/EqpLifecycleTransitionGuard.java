package com.nori.tc.comm.gateway.lifecycle;

/**
 * lifecycle 상태 전이 시 stale 이벤트/버전 검증을 담당하는 유틸입니다.
 */
public final class EqpLifecycleTransitionGuard {

    /**
     * 유틸 클래스이므로 인스턴스화를 금지합니다.
     */
    private EqpLifecycleTransitionGuard() {
    }

    /**
     * 요청 이벤트가 최신 상태 버전보다 과거인지 판별합니다.
     *
     * @param incomingStateVersion 이벤트가 가진 상태 버전
     * @param latestStateVersion 현재 eqpId의 최신 상태 버전
     * @return stale 이벤트면 true
     */
    public static boolean isStaleRequest(
            final long incomingStateVersion,
            final long latestStateVersion
    ) {
        return incomingStateVersion < latestStateVersion;
    }

    /**
     * timeout 이벤트가 현재 pending 전이와 정확히 일치하는지 판별합니다.
     *
     * @param timeoutStateVersion timeout 이벤트 버전
     * @param pendingStateVersion pending 전이 버전
     * @return 현재 pending에 유효한 timeout 이벤트면 true
     */
    public static boolean isMatchingTimeout(
            final long timeoutStateVersion,
            final long pendingStateVersion
    ) {
        return timeoutStateVersion == pendingStateVersion;
    }
}
