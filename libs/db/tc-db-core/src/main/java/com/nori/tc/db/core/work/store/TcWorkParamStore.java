package com.nori.tc.db.core.work.store;

import java.util.List;
import java.util.Optional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.work.upsert.UpsertTcWorkParam;
import com.nori.tc.db.domain.work.TcWorkParam;

/**
 * tc_work_param CRUD 인터페이스.
 *
 * <p>
 * - Unique(work_key, param_name)을 기준으로 upsert를 수행한다.
 * - 변경 대상은 param_value만 허용한다.
 * - 조회는 DB 페이징을 강제한다.
 * </p>
 */
public interface TcWorkParamStore {

    TcWorkParam upsert(UpsertTcWorkParam command);

    Optional<TcWorkParam> findByWorkKeyAndName(long workKey, String paramName);

    /**
     * 특정 작업(work_key)의 파라미터 목록 조회.
     * - 페이징은 반드시 DB 레벨에서 처리해야 한다.
     */
    List<TcWorkParam> findAllByWorkKey(long workKey, PageRequest page);

    void deleteByWorkParamKey(long workParamKey);
}
