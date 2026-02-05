package com.nori.tc.db.core.eqp;

import java.util.List;
import java.util.Optional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.domain.eqp.TcEqpParam;

/**
 * tc_eqp_param CRUD 인터페이스.
 *
 * <p>
 * - Unique(eqp_key, param_name, param_version)을 기준으로 upsert를 수행한다.
 * - param_value만 갱신하는 패턴을 기본으로 한다.
 * </p>
 */
public interface TcEqpParamStore {

    TcEqpParam upsert(UpsertTcEqpParam command);

    Optional<TcEqpParam> findByEqpKeyAndNameVersion(long eqpKey, String paramName, String paramVersion);

    /**
     * 특정 설비(eqp_key)의 파라미터 목록 조회.
     * - 페이징은 반드시 DB 레벨에서 처리해야 한다.
     */
    List<TcEqpParam> findAllByEqpKey(long eqpKey, PageRequest page);

    void deleteByEqpParamKey(long eqpParamKey);
}
