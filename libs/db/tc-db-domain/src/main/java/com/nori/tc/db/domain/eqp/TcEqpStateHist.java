package com.nori.tc.db.domain.eqp;

import java.time.OffsetDateTime;

import com.nori.tc.db.domain.common.eqp.EqpStateType;

/**
 * tc_eqp_state_hist 테이블 1행에 대응하는 순수 DTO.
 *
 * <p>
 * PK/FK:
 * - state_hist_key : bigint identity (PK)
 * - eqp_key        : tc_eqp.eqp_key FK (ON DELETE CASCADE)
 * </p>
 *
 * <p>
 * 주요 컬럼:
 * - state_type   : OPER / CONN
 * - from_state   : 변경 전 상태 (nullable)
 * - to_state     : 변경 후 상태 (nullable)
 * - changed_at   : 변경 시각 (기본값 CURRENT_TIMESTAMP)
 * - reason_code  : 변경 사유 코드 (nullable)
 * - reason_detail: 변경 사유 상세 (nullable)
 * </p>
 */
public record TcEqpStateHist(
        long stateHistKey,
        long eqpKey,
        EqpStateType stateType,
        String fromState,
        String toState,
        OffsetDateTime changedAt,
        String reasonCode,
        String reasonDetail
) {
}
