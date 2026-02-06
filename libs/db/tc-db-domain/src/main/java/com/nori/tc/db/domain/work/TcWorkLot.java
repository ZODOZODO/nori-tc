package com.nori.tc.db.domain.work;

import java.time.OffsetDateTime;

/**
 * tc_work_lot 테이블 1행에 대응하는 순수 DTO.
 *
 * [PK]
 * - work_lot_key: bigint IDENTITY (DB에서 자동 생성)
 *
 * [FK]
 * - work_key -> tc_work.work_key (ON DELETE CASCADE)
 *
 * [Unique]
 * - (work_key, lot_id) 조합이 유니크 (업서트 기준 키)
 *
 * [Nullable]
 * - carrier_id / parent_lot_id / chamber_id 는 NULL 허용 컬럼이므로 Java에서도 null 허용 타입으로 둔다.
 *
 * [Timestamp]
 * - updated_at 은 DB에서 CURRENT_TIMESTAMP 로 자동 갱신되므로 읽기 전용 성격이다.
 */
public record TcWorkLot(
        long workLotKey,
        long workKey,
        String carrierId,
        String lotId,
        String parentLotId,
        String chamberId,
        OffsetDateTime updatedAt
) {
}
