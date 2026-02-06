package com.nori.tc.db.domain.common.outbox;

/**
 * 메시지 발송 결과 코드 (tc_msg_send_log.result).
 *
 * <p>
 * DB CHECK 제약:
 * - SUCCESS, FAIL 값만 허용합니다.
 * </p>
 *
 * <p>
 * 설계 메모:
 * - 결과 코드 자체는 상태 분기용 최소값으로 유지합니다.
 * - 상세 원인(에러 코드/메시지)은 tc_msg_send_log.error_code/error_message에 기록합니다.
 * </p>
 */
public enum MsgSendResult {

    /**
     * 발송 성공
     */
    SUCCESS,

    /**
     * 발송 실패
     */
    FAIL
}
