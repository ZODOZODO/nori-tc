package com.nori.tc.db.core.eqp;

import java.time.OffsetDateTime;

/**
 * tc_eqp_hsms upsert 입력(Command)
 */
public record UpsertTcEqpHsms(
        String eqpId,
        int deviceId,
        int t3Ms,
        int t5Ms,
        int t6Ms,
        int t7Ms,
        int t8Ms,
        boolean linktestEnabled,
        int linktestIntervalMs,
        int maxMsgBytes,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
