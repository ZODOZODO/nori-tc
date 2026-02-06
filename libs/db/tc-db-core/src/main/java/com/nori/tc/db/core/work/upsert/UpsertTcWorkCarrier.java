package com.nori.tc.db.core.work.upsert;

import java.time.OffsetDateTime;

/**
 * tc_work_carrier upsert 입력(Command)
 *
 * <p>
 * - work_key + carrier_id가 비즈니스 유니크 키로 사용됩니다.
 * - updated_at은 DB/JPA Auditing이 관리하므로 입력값은 참고용입니다.
 * - qty 컬럼은 음수가 될 수 없으므로 상위 계층에서 검증을 권장합니다.
 * </p>
 */
public record UpsertTcWorkCarrier(
        long workKey,
        String carrierId,
        String portId,
        String slotMap,
        Integer totalQty,
        Integer goodQty,
        Integer scrapQty,
        OffsetDateTime updatedAt
) {
}
