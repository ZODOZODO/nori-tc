package com.nori.tc.db.domain.outbox;

import java.time.OffsetDateTime;

import com.nori.tc.db.domain.common.outbox.MsgSendResult;

/**
 * tc_msg_send_log 테이블 1행에 대응하는 순수 DTO.
 *
 * PK:
 * - send_log_key (IDENTITY)
 *
 * FK:
 * - msg_key (tc_msg_send_queue.msg_key) ON DELETE CASCADE
 *
 * Index:
 * - (msg_key, attempt_no)
 * - (sent_at)
 *
 * Check:
 * - attempt_no >= 1
 * - result IN ('SUCCESS', 'FAIL')
 *
 * nullable 컬럼은 Java에서 null 허용 타입으로 둡니다.
 */
public record TcMsgSendLog(
        long sendLogKey,
        long msgKey,
        int attemptNo,
        MsgSendResult result,
        Integer kafkaPartition,
        Long kafkaOffset,
        String errorCode,
        String errorMessage,
        OffsetDateTime sentAt
) {
}
