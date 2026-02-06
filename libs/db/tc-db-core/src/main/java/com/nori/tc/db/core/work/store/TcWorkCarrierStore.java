package com.nori.tc.db.core.work.store;

import java.util.List;
import java.util.Optional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.work.upsert.UpsertTcWorkCarrier;
import com.nori.tc.db.domain.work.TcWorkCarrier;

/**
 * tc_work_carrier CRUD 인터페이스.
 */
public interface TcWorkCarrierStore {

    TcWorkCarrier upsert(UpsertTcWorkCarrier command);

    Optional<TcWorkCarrier> findByWorkKeyCarrierId(long workKey, String carrierId);

    List<TcWorkCarrier> findAllByWorkKey(long workKey, PageRequest page);

    void deleteByWorkKeyCarrierId(long workKey, String carrierId);
}
