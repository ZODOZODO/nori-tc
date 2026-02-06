package com.nori.tc.db.core.work.upsert;

import java.time.OffsetDateTime;

/**
 * tc_work_carrier_slot upsert 입력(Command)
 *
 * - workCarrierKey/slotNo는 유니크 키로 사용됩니다.
 * - updatedAt은 DB에서 CURRENT_TIMESTAMP로 갱신됩니다.
 */
public record UpsertTcWorkCarrierSlot(
        long workCarrierKey,
        int slotNo,
        String slotState,
        String lotId,
        OffsetDateTime updatedAt
) {
}
