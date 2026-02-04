package com.nori.tc.db.core.eqp;

import java.util.List;
import java.util.Optional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.domain.eqp.TcEqp;

/**
 * tc_eqp CRUD 인터페이스.
 *
 * 주의:
 * - deleteByEqpId는 하위 1:1 테이블들이 ON DELETE CASCADE이므로 연쇄 삭제됩니다.
 */
public interface TcEqpStore {

    TcEqp upsert(UpsertTcEqp command);

    Optional<TcEqp> findByEqpId(String eqpId);

    List<TcEqp> findAll(TcEqpSearchCriteria criteria, PageRequest page);

    void deleteByEqpId(String eqpId);
}
