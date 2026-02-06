package com.nori.tc.db.domain.common.outbox;

/**
 * tc_msg_send_queue.status 상태 값 정의.
 *
 * <p>
 * DB 체크 제약(ck_tc_msg_send_queue_status)을 코드 레벨에서도 동일하게 맞춘다.
 * </p>
 *
 * <ul>
 *     <li>PENDING: 전송 대기</li>
 *     <li>SENDING: 전송 중(락 점유)</li>
 *     <li>SENT: 전송 완료</li>
 *     <li>FAILED: 전송 실패(재시도 가능)</li>
 *     <li>DEAD: 재시도 한도 초과/영구 실패</li>
 * </ul>
 */
public enum TcMsgSendStatus {
    PENDING,
    SENDING,
    SENT,
    FAILED,
    DEAD
}
