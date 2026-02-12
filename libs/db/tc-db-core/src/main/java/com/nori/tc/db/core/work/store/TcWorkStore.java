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

    
    /**
     * DB Core 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     * @return DB Core 계층 처리 결과
     */
    TcWork upsert(UpsertTcWork command);

    
    /**
     * DB Core 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param workKey 대상 키 값
     * @return 조회 결과(Optional)
     */
    Optional<TcWork> findByWorkKey(long workKey);

    
    /**
     * DB Core 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param eqpKey 설비 식별 정보
     * @param workId DB Core 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    Optional<TcWork> findByEqpKeyAndWorkId(long eqpKey, String workId);

    /**
     * 특정 설비(eqp_key)의 작업 목록 조회.
     * - 페이징은 반드시 DB 레벨에서 처리해야 한다.
     */
    List<TcWork> findAllByEqpKey(long eqpKey, PageRequest page);

    
    /**
     * DB Core 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param workKey 대상 키 값
     */
    void deleteByWorkKey(long workKey);
}
