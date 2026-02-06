package com.nori.tc.db.core.work.store;

import java.util.List;
import java.util.Optional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.work.upsert.UpsertTcWorkLot;
import com.nori.tc.db.domain.work.TcWorkLot;

/**
 * tc_work_lot CRUD 인터페이스.
 *
 * <p>
 * - Unique(work_key, lot_id)를 기준으로 upsert를 수행한다.
 * - updated_at 은 DB에서 자동 갱신되므로 클라이언트가 직접 제어하지 않는다.
 * </p>
 */
public interface TcWorkLotStore {

    TcWorkLot upsert(UpsertTcWorkLot command);

    Optional<TcWorkLot> findByWorkKeyAndLotId(long workKey, String lotId);

    /**
     * 특정 작업(work_key)에 연결된 LOT 목록 조회.
     * - 페이징은 반드시 DB 레벨에서 처리해야 한다.
     */
    List<TcWorkLot> findAllByWorkKey(long workKey, PageRequest page);

    void deleteByWorkLotKey(long workLotKey);
}
