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

    
    /**
     * DB Core 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     * @return DB Core 계층 처리 결과
     */
    TcEqpGlobal upsert(UpsertTcEqpGlobal command);

    
    /**
     * DB Core 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param eqpKey 설비 식별 정보
     * @param paramName DB Core 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    Optional<TcEqpGlobal> findByEqpKeyAndParamName(long eqpKey, String paramName);

    
    /**
     * DB Core 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param eqpKey 설비 식별 정보
     * @return 조회/처리 결과 목록
     */
    List<TcEqpGlobal> findByEqpKey(long eqpKey);

    
    /**
     * DB Core 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param eqpKey 설비 식별 정보
     * @param paramName DB Core 계층 처리에 사용하는 입력 값
     */
    void deleteByEqpKeyAndParamName(long eqpKey, String paramName);
}
