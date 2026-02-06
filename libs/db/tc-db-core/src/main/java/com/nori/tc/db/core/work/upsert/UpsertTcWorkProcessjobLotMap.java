package com.nori.tc.db.core.work.upsert;

import com.nori.tc.db.domain.common.work.TcWorkProcessjobLotMapOrder;

/**
 * tc_work_processjob_lot_map upsert 입력(Command).
 *
 * <p>
 * - pjLotMapKey가 있으면 해당 PK 기준으로 갱신을 시도한다.
 * - pjLotMapKey가 없으면 (process_job_key, work_lot_key) 유니크 키를 기준으로
 *   존재 여부를 확인한 뒤 갱신/생성을 수행한다.
 * </p>
 *
 * <p>
 * 주의:
 * - created_at/updated_at은 DB/JPA 라이프사이클에서 관리하도록 둔다.
 * - map_order는 null 이거나 FORWARD/REVERSE 값이어야 한다.
 * </p>
 */
public record UpsertTcWorkProcessjobLotMap(
        Long pjLotMapKey,
        long processJobKey,
        long workLotKey,
        String mapRole,
        TcWorkProcessjobLotMapOrder mapOrder
) {
}
