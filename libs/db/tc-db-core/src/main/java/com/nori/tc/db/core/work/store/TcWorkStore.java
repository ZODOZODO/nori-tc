package com.nori.tc.db.core.work.store;

import java.util.List;
import java.util.Optional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.work.upsert.UpsertTcWork;
import com.nori.tc.db.domain.work.TcWork;

/**
 * tc_work CRUD 인터페이스.
 *
 * <p>
 * - Unique(eqp_key, work_id)을 기준으로 upsert를 수행한다.
 * - 작업 상태(work_state)와 시간(start/end)은 업무 흐름에 따라 갱신한다.
 * </p>
 */
public interface TcWorkStore {

    TcWork upsert(UpsertTcWork command);

    Optional<TcWork> findByWorkKey(long workKey);

    Optional<TcWork> findByEqpKeyAndWorkId(long eqpKey, String workId);

    /**
     * 특정 설비(eqp_key)의 작업 목록 조회.
     * - 페이징은 반드시 DB 레벨에서 처리해야 한다.
     */
    List<TcWork> findAllByEqpKey(long eqpKey, PageRequest page);

    void deleteByWorkKey(long workKey);
}
