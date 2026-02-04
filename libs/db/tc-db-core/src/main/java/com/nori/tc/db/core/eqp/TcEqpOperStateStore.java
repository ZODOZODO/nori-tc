package com.nori.tc.db.core.eqp;

import java.util.Optional;

import com.nori.tc.db.domain.eqp.TcEqpOperState;

/**
 * tc_eqp_oper_state CRUD 인터페이스.
 */
public interface TcEqpOperStateStore {

    TcEqpOperState upsert(UpsertTcEqpOperState command);

    Optional<TcEqpOperState> findByEqpId(String eqpId);

    void deleteByEqpId(String eqpId);
}
