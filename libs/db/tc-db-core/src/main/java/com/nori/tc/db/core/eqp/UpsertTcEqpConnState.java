package com.nori.tc.db.core.eqp;

import java.time.OffsetDateTime;

import com.nori.tc.db.domain.common.ConnectionState;

/**
 * tc_eqp_conn_state upsert 입력(Command)
 *
 * - since_at/updated_at은 기본값(now())가 있으나,
 *   상태 갱신 시점의 정확성을 위해 입력으로 받을 수 있게 둡니다.
 * - null 허용 컬럼은 null로 전달 가능합니다.
 */
public record UpsertTcEqpConnState(
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
