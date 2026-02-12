package com.nori.tc.db.core.eqp.store;

import java.util.Optional;

import com.nori.tc.db.core.eqp.upsert.UpsertTcEqpState;
import com.nori.tc.db.domain.eqp.TcEqpState;

/**
 * tc_eqp_state CRUD 인터페이스.
 */
public interface TcEqpStateStore {

    
    /**
     * DB Core 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     * @return DB Core 계층 처리 결과
     */
    TcEqpState upsert(UpsertTcEqpState command);

    
    /**
     * DB Core 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param eqpKey 설비 식별 정보
     * @return 조회 결과(Optional)
     */
    Optional<TcEqpState> findByEqpKey(long eqpKey);

    
    /**
     * DB Core 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param eqpKey 설비 식별 정보
     */
    void deleteByEqpKey(long eqpKey);
}
