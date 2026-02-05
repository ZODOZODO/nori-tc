package com.nori.tc.db.core.eqp.store;

import java.util.List;
import java.util.Optional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.eqp.upsert.UpsertTcEqpPortStatus;
import com.nori.tc.db.domain.eqp.TcEqpPortStatus;

/**
 * tc_eqp_port_status CRUD 인터페이스.
 */
public interface TcEqpPortStatusStore {

    TcEqpPortStatus upsert(UpsertTcEqpPortStatus command);

    Optional<TcEqpPortStatus> findByEqpKeyPortId(long eqpKey, String portId);

    List<TcEqpPortStatus> findAllByEqpKey(long eqpKey, PageRequest page);

    void deleteByEqpKeyPortId(long eqpKey, String portId);
}
