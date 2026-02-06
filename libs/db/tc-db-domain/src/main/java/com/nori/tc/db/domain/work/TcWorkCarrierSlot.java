package com.nori.tc.db.domain.work;

import java.time.OffsetDateTime;

/**
 * tc_work_carrier_slot 테이블 1행에 대응하는 순수 DTO.
 *
 * PK/FK:
 * - carrier_slot_key (IDENTITY)
 * - work_carrier_key (tc_work_carrier.work_carrier_key) ON DELETE CASCADE
 *
 * Unique:
 * - (work_carrier_key, slot_no)
 *
 * Check:
 * - slot_no >= 1
 *
 * nullable 컬럼은 Java에서 null 허용 타입으로 둡니다.
 */
public record TcWorkCarrierSlot(
        long carrierSlotKey,
        long workCarrierKey,
        int slotNo,
        String slotState,
        String lotId,
        OffsetDateTime updatedAt
) {
}
