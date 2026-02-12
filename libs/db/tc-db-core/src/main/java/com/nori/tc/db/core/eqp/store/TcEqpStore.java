package com.nori.tc.db.core.eqp.store;

import java.util.List;
import java.util.Optional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.eqp.upsert.UpsertTcEqp;
import com.nori.tc.db.domain.eqp.TcEqp;

/**
 * tc_eqp CRUD 인터페이스.
 *
 * 주의:
 * - deleteByEqpId는 하위 1:1 테이블들이 ON DELETE CASCADE이므로 연쇄 삭제됩니다.
 */
public interface TcEqpStore {

    
    /**
     * DB Core 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     * @return DB Core 계층 처리 결과
     */
    TcEqp upsert(UpsertTcEqp command);

    
    /**
     * DB Core 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param eqpId 설비 식별 정보
     * @return 조회 결과(Optional)
     */
    Optional<TcEqp> findByEqpId(String eqpId);

    
    /**
     * DB Core 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param page 페이징/조회 범위 조건
     * @return 조회/처리 결과 목록
     */
    List<TcEqp> findAll(PageRequest page);

    
    /**
     * DB Core 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param eqpId 설비 식별 정보
     */
    void deleteByEqpId(String eqpId);
}
