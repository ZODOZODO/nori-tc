package com.nori.tc.db.core.outbox.upsert;

import java.time.OffsetDateTime;

import com.nori.tc.db.domain.common.outbox.TcMsgSendStatus;

/**
 * tc_msg_send_queue upsert 입력(Command).
 *
 * <p>
 * - msgKey가 있으면 해당 PK 기준으로 갱신을 시도한다.
 * - msgKey가 없으면 (topic, idempotency_key) 유니크 키를 기준으로
 *   존재 여부를 확인한 뒤 갱신/생성을 수행한다.
 * </p>
 *
 * <p>
 * 주의:
 * - created_at/updated_at은 DB/JPA 라이프사이클에서 관리하도록 두는 것이 안전하다.
 * - retry_count는 0 이상이어야 하며, 음수는 허용하지 않는다.
 * </p>
 */
public record UpsertTcMsgSendQueue(
        Long msgKey,
        String idempotencyKey,
        String topic,
        String messageKey,
        String headersJson,
        String payloadJson,
        TcMsgSendStatus status,
        int retryCount,
        OffsetDateTime nextRetryAt,
        String lockedBy,
        OffsetDateTime lockedUntil
) {
}
