package com.nori.tc.db.core.eqp;

import java.util.Optional;

import com.nori.tc.db.domain.eqp.TcEqpConnState;

/**
 * tc_eqp_conn_state CRUD 인터페이스.
 *
 * - PK가 eqp_id인 1:1 테이블.
 */
public interface TcEqpConnStateStore {

    TcEqpConnState upsert(UpsertTcEqpConnState command);

    Optional<TcEqpConnState> findByEqpId(String eqpId);

    void deleteByEqpId(String eqpId);
}
