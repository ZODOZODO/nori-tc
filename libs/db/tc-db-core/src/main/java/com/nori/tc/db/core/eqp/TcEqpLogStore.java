package com.nori.tc.db.core.eqp;

import java.util.Optional;

import com.nori.tc.db.domain.eqp.TcEqpLog;

/**
 * tc_eqp_log CRUD 인터페이스.
 */
public interface TcEqpLogStore {

    TcEqpLog upsert(UpsertTcEqpLog command);

    Optional<TcEqpLog> findByEqpId(String eqpId);

    void deleteByEqpId(String eqpId);
}
