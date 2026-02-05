package com.nori.tc.db.core.eqp.upsert;

import java.time.OffsetDateTime;

/**
 * tc_eqp_hsms upsert 입력(Command)
 *
 * - eqp_key는 tc_eqp의 PK를 참조하는 FK입니다.
 * - created_at/updated_at은 DB(또는 구현체)가 관리하는 것을 권장합니다.
 */
public record UpsertTcEqpHsms(
        long eqpKey,
        int deviceId,
        String connectionMode,
        int t3Timeout,
        int t5Timeout,
        int t6Timeout,
        int t7Timeout,
        int t8Timeout,
        boolean linkTestEnabled,
        int linkTestInterval,
        long maxMsgBytes,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
