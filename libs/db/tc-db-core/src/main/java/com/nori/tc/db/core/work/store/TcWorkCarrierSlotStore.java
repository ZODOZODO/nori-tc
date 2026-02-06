package com.nori.tc.db.core.work.store;

import java.util.List;
import java.util.Optional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.work.upsert.UpsertTcWorkCarrierSlot;
import com.nori.tc.db.domain.work.TcWorkCarrierSlot;

/**
 * tc_work_carrier_slot CRUD 인터페이스.
 *
 * - (work_carrier_key, slot_no) 유니크 키 기반의 upsert 및 조회를 제공합니다.
 * - 대량 조회는 PageRequest(offset/limit)를 받아 페이징합니다.
 */
public interface TcWorkCarrierSlotStore {

    TcWorkCarrierSlot upsert(UpsertTcWorkCarrierSlot command);

    Optional<TcWorkCarrierSlot> findByWorkCarrierKeySlotNo(long workCarrierKey, int slotNo);

    List<TcWorkCarrierSlot> findAllByWorkCarrierKey(long workCarrierKey, PageRequest page);

    void deleteByWorkCarrierKeySlotNo(long workCarrierKey, int slotNo);
}
