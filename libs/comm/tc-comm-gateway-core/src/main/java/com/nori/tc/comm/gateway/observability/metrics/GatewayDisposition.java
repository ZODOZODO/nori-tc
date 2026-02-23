package com.nori.tc.comm.gateway.observability.metrics;

/**
 * Gateway 제어/명령 처리의 표준 disposition 상태입니다.
 *
 * <p>Phase 4 운영 강건화 요구사항에 따라,
 * 서로 다른 처리 경로(UI task, command, plugin reload)의 결과를
 * 동일한 상태 축으로 집계하기 위해 사용합니다.</p>
 *
 * <p>상태 정의:</p>
 * <p>1) ACCEPTED: 정상 처리 수락/완료</p>
 * <p>2) RETRY: 재시도 예약</p>
 * <p>3) DLQ: DLQ 이관</p>
 * <p>4) REJECTED: 거부 또는 실패 종료</p>
 */
public enum GatewayDisposition {
    ACCEPTED,
    RETRY,
    DLQ,
    REJECTED
}

