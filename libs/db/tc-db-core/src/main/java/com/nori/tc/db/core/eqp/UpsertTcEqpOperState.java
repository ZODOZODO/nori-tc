package com.nori.tc.db.core.eqp;

import java.time.OffsetDateTime;

/**
 * tc_eqp_oper_state upsert 입력(Command)
 *
 * - oper_state는 현재 DB 제약이 없으므로 String 기반으로 둡니다.
 */
public record UpsertTcEqpOperState(
        String eqpId,
        String operState,
        OffsetDateTime sinceAt,
        String reasonCode,
        String reasonDetail,
        OffsetDateTime updatedAt
) {
}
