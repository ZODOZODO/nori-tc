package com.nori.tc.db.core.eqp.upsert;

import java.time.OffsetDateTime;

import com.nori.tc.db.domain.common.EqpStateType;

/**
 * tc_eqp_state_hist insert 입력(Command)
 *
 * <p>
 * 상세 규칙:
 * - eqpKey: 필수 (FK)
 * - stateType: 필수 (OPER/CONN)
 * - fromState/toState: nullable
 * - changedAt: null이면 현재 시각으로 보정
 * - reasonCode/reasonDetail: nullable
 * </p>
 */
public record UpsertTcEqpStateHist(
        Long eqpKey,
        EqpStateType stateType,
        String fromState,
        String toState,
        OffsetDateTime changedAt,
        String reasonCode,
        String reasonDetail
) {
}
