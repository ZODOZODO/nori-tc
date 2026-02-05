package com.nori.tc.db.core.eqp;

import java.util.Optional;

import com.nori.tc.db.domain.eqp.TcEqpHsms;

/**
 * tc_eqp_hsms CRUD 인터페이스.
 */
public interface TcEqpHsmsStore {

    TcEqpHsms upsert(UpsertTcEqpHsms command);

    Optional<TcEqpHsms> findByEqpKey(long eqpKey);

    void deleteByEqpKey(long eqpKey);
}
