package com.nori.tc.db.core.model.store;

import java.util.List;
import java.util.Optional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.model.upsert.UpsertTcModelParam;
import com.nori.tc.db.domain.model.TcModelParam;

/**
 * tc_model_param CRUD 인터페이스.
 *
 * <p>
 * - Unique(model_key, param_name)을 기준으로 upsert를 수행한다.
 * - param_value만 갱신하는 패턴을 기본으로 한다.
 * </p>
 */
public interface TcModelParamStore {

    TcModelParam upsert(UpsertTcModelParam command);

    Optional<TcModelParam> findByModelKeyAndName(long modelKey, String paramName);

    /**
     * 특정 모델(model_key)의 파라미터 목록 조회.
     * - 페이징은 반드시 DB 레벨에서 처리해야 한다.
     */
    List<TcModelParam> findAllByModelKey(long modelKey, PageRequest page);

    void deleteByModelParamKey(long modelParamKey);
}
