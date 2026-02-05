package com.nori.tc.db.core.eqp;

import java.time.OffsetDateTime;

/**
 * tc_eqp_socket upsert 입력(Command)
 */
public record UpsertTcEqpSocket(
        long eqpKey,
        String socketProtocolType,
        String connectionMode,
        String charset,
        boolean heartbeatEnabled,
        int heartbeatInterval,
        int readTimeout,
        int writeTimeout,
        int maxFrameSizeBytes,
        boolean keepAliveEnabled,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
