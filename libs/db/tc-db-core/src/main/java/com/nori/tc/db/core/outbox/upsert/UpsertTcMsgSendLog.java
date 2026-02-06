package com.nori.tc.db.core.outbox.upsert;

import java.time.OffsetDateTime;

import com.nori.tc.db.domain.common.outbox.MsgSendResult;

/**
 * tc_msg_send_log upsert 입력(Command)
 *
 * <p>
 * 상세 규칙:
 * - msgKey: 반드시 양수 (FK)
 * - attemptNo: 1 이상 (DB CHECK)
 * - result: SUCCESS/FAIL (DB CHECK)
 * - sentAt: null이면 현재 시각으로 보정 (DB default와 동일한 의미)
 * - errorCode/errorMessage: 실패 원인을 기록할 때만 사용 (nullable)
 * </p>
 */
public record UpsertTcMsgSendLog(
        Long msgKey,
        Integer attemptNo,
        MsgSendResult result,
        Integer kafkaPartition,
        Long kafkaOffset,
        String errorCode,
        String errorMessage,
        OffsetDateTime sentAt
) {
}
