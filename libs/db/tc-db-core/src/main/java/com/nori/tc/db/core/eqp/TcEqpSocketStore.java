package com.nori.tc.db.core.eqp;

import java.util.Optional;

import com.nori.tc.db.domain.eqp.TcEqpSocket;

/**
 * tc_eqp_socket CRUD 인터페이스.
 */
public interface TcEqpSocketStore {

    TcEqpSocket upsert(UpsertTcEqpSocket command);

    Optional<TcEqpSocket> findByEqpId(String eqpId);

    void deleteByEqpId(String eqpId);
}
