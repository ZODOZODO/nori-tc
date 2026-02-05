package com.nori.tc.db.core.eqp.store;

import java.util.List;
import java.util.Optional;

import com.nori.tc.db.core.eqp.upsert.UpsertTcEqpGlobal;
import com.nori.tc.db.domain.eqp.TcEqpGlobal;

/**
 * tc_eqp_global CRUD 인터페이스.
 *
 * - (eqp_key, param_name) 유니크 키 기반으로 upsert/조회/삭제를 수행한다.
 * - eqp_key는 tc_eqp에 종속되므로, 상위 레이어에서 존재성 검증 여부를 결정한다.
 */
public interface TcEqpGlobalStore {

    TcEqpGlobal upsert(UpsertTcEqpGlobal command);

    Optional<TcEqpGlobal> findByEqpKeyAndParamName(long eqpKey, String paramName);

    List<TcEqpGlobal> findByEqpKey(long eqpKey);

    void deleteByEqpKeyAndParamName(long eqpKey, String paramName);
}
