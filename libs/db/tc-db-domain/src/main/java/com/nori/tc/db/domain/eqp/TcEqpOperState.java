package com.nori.tc.db.domain.eqp;

import java.time.OffsetDateTime;

/**
 * tc_eqp_oper_state 테이블 1행에 대응하는 순수 DTO.
 *
 * PK/FK:
 * - eqp_id (tc_eqp.eqp_id) ON DELETE CASCADE
 *
 * oper_state는 현재 DB에 enum 제약이 없으므로 String으로 둡니다.
 * (추후 제약이 생기면 enum으로 승격 가능)
 */
public record TcEqpOperState(
        String eqpId,
        String operState,
        OffsetDateTime sinceAt,
        String reasonCode,
        String reasonDetail,
        OffsetDateTime updatedAt
) {
}
