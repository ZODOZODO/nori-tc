package com.nori.tc.db.core.eqp;

import java.util.Optional;

import com.nori.tc.db.domain.eqp.TcEqpState;

/**
 * tc_eqp_state CRUD 인터페이스.
 */
public interface TcEqpStateStore {

    TcEqpState upsert(UpsertTcEqpState command);

    Optional<TcEqpState> findByEqpKey(long eqpKey);

    void deleteByEqpKey(long eqpKey);
}
