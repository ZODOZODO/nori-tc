package com.nori.tc.db.core.work.upsert;

/**
 * tc_work_lot upsert 입력(Command)
 *
 * <p>
 * Unique Key(work_key, lot_id)를 기준으로 레코드를 갱신하거나 신규 생성한다.
 * </p>
 *
 * <ul>
 *   <li>carrier_id / parent_lot_id / chamber_id 는 선택 입력(Nullable)</li>
 *   <li>updated_at 은 DB에서 CURRENT_TIMESTAMP 로 처리하므로 커맨드에는 포함하지 않는다.</li>
 * </ul>
 */
public record UpsertTcWorkLot(
        long workKey,
        String carrierId,
        String lotId,
        String parentLotId,
        String chamberId
) {
}
