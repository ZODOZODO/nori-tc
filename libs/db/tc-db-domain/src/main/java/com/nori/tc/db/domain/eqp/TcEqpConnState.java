package com.nori.tc.db.domain.eqp;

import java.time.OffsetDateTime;

import com.nori.tc.db.domain.common.ConnectionState;

/**
 * tc_eqp_conn_state 테이블 1행에 대응하는 순수 DTO.
 *
 * PK/FK:
 * - eqp_id (tc_eqp.eqp_id) ON DELETE CASCADE
 *
 * nullable 컬럼은 Java에서 null 허용 타입으로 둡니다.
 */
public record TcEqpConnState(
        String eqpId,
        ConnectionState connState,
        OffsetDateTime sinceAt,
        OffsetDateTime lastConnectAt,
        OffsetDateTime lastDisconnectAt,
        OffsetDateTime lastRxAt,
        OffsetDateTime lastTxAt,
        String lastErrorCode,
        String lastErrorMessage,
        OffsetDateTime updatedAt
) {
}
