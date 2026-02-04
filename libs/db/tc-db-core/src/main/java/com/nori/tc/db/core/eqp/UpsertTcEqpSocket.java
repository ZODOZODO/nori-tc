package com.nori.tc.db.core.eqp;

import java.time.OffsetDateTime;

/**
 * tc_eqp_socket upsert 입력(Command)
 */
public record UpsertTcEqpSocket(
        String eqpId,
        String socketProtocolType,
        String charset,
        boolean heartbeatEnabled,
        int heartbeatIntervalMs,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
