package com.nori.tc.db.domain.outbox;

import java.time.OffsetDateTime;

import com.nori.tc.db.domain.common.outbox.TcMsgSendStatus;

/**
 * tc_msg_send_queue 테이블 1행에 대응하는 순수 DTO.
 *
 * <p>
 * PK:
 * - msg_key : bigint identity (PK)
 * </p>
 *
 * <p>
 * Unique:
 * - (topic, idempotency_key)
 * </p>
 *
 * <p>
 * 주요 컬럼:
 * - idempotency_key : 중복 전송 방지 키 (필수)
 * - topic           : 메시지 토픽 (필수)
 * - message_key     : 라우팅 키/메시지 키 (선택)
 * - headers_json    : 헤더 JSON (선택)
 * - payload_json    : 본문 JSON (필수)
 * - status          : 전송 상태 (PENDING/SENDING/SENT/FAILED/DEAD)
 * - retry_count     : 재시도 횟수 (0 이상)
 * - next_retry_at   : 다음 재시도 시각 (선택)
 * - locked_by       : 처리자 식별자 (선택)
 * - locked_until    : 락 만료 시각 (선택)
 * - created_at      : 생성 시각 (DB 자동)
 * - updated_at      : 갱신 시각 (DB 자동)
 * </p>
 */
public record TcMsgSendQueue(
        long msgKey,
        String idempotencyKey,
        String topic,
        String messageKey,
        String headersJson,
        String payloadJson,
        TcMsgSendStatus status,
        int retryCount,
        OffsetDateTime nextRetryAt,
        String lockedBy,
        OffsetDateTime lockedUntil,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
