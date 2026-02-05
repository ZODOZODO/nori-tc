package com.nori.tc.db.domain.eqp;

import java.time.OffsetDateTime;

import com.nori.tc.db.domain.common.eqp.ControlState;
import com.nori.tc.db.domain.common.eqp.EqpState;

/**
 * tc_eqp_state 테이블 1행에 대응하는 순수 DTO.
 *
 * PK/FK:
 * - eqp_key (tc_eqp.eqp_key) ON DELETE CASCADE
 *
 * nullable 컬럼은 Java에서 null 허용 타입으로 둡니다.
 */
public record TcEqpState(
        long eqpKey,
        ControlState controlState,
        EqpState eqpState,
        OffsetDateTime sinceAt,
        String reasonCode,
        String reasonDetail,
        OffsetDateTime updatedAt
) {
}
