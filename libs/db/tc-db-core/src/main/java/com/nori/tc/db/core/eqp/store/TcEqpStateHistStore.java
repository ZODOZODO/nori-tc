package com.nori.tc.db.core.eqp.store;

import java.util.List;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.eqp.upsert.UpsertTcEqpStateHist;
import com.nori.tc.db.domain.eqp.TcEqpStateHist;

/**
 * tc_eqp_state_hist CRUD 인터페이스.
 *
 * <p>
 * - 이력 테이블이므로 기본 동작은 append(insert)입니다.
 * - 조회는 eqp_key 기준으로 최신순 페이징을 제공합니다.
 * </p>
 */
public interface TcEqpStateHistStore {

    /**
     * 이력 append (insert 전용)
     */
    void append(UpsertTcEqpStateHist command);

    /**
     * 특정 설비(eqp_key)의 상태 변경 이력 조회 (최신순)
     * - 페이징은 반드시 DB 레벨에서 처리해야 한다.
     */
    List<TcEqpStateHist> findAllByEqpKey(long eqpKey, PageRequest page);
}
