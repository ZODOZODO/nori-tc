package com.nori.tc.db.core.eqp.upsert;

import java.time.OffsetDateTime;

import com.nori.tc.db.domain.common.ControlState;
import com.nori.tc.db.domain.common.EqpState;

/**
 * tc_eqp_state upsert 입력(Command)
 *
 * - control_state/eqp_state는 DB 제약 기반 enum으로 유지합니다.
 * - updated_at은 DB(또는 구현체)에서 자동 갱신하는 것을 권장합니다.
 */
public record UpsertTcEqpState(
        long eqpKey,
        ControlState controlState,
        EqpState eqpState,
        OffsetDateTime sinceAt,
        String reasonCode,
        String reasonDetail,
        OffsetDateTime updatedAt
) {
}
