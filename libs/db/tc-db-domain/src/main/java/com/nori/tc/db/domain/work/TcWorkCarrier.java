package com.nori.tc.db.domain.work;

import java.time.OffsetDateTime;

/**
 * tc_work_carrier 테이블 1행에 대응하는 순수 DTO.
 *
 * <p>
 * [PK/FK]
 * - work_carrier_key (IDENTITY)
 * - work_key (tc_work.work_key) ON DELETE CASCADE
 *
 * [Unique]
 * - (work_key, carrier_id)
 *
 * [컬럼 특성]
 * - updated_at: DB에서 CURRENT_TIMESTAMP로 관리
 * - nullable 컬럼은 Java에서 null 허용 타입으로 둡니다.
 * - total_qty/good_qty/scrap_qty는 CHECK 제약으로 0 이상을 보장합니다.
 * </p>
 */
public record TcWorkCarrier(
        long workCarrierKey,
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
