package com.nori.tc.db.core.eqp.store;

import java.util.List;
import java.util.Optional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.eqp.upsert.UpsertTcEqpParam;
import com.nori.tc.db.domain.eqp.TcEqpParam;

/**
 * tc_eqp_param CRUD 인터페이스.
 *
 * <p>
 * - Unique(eqp_key, param_name, param_version)을 기준으로 upsert를 수행한다.
 * - param_value/description 갱신을 지원한다.
 * </p>
 */
public interface TcEqpParamStore {

    
    /**
     * DB Core 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     * @return DB Core 계층 처리 결과
     */
    TcEqpParam upsert(UpsertTcEqpParam command);

    
    /**
     * DB Core 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param eqpKey 설비 식별 정보
     * @param paramName DB Core 계층 처리에 사용하는 입력 값
     * @param paramVersion DB Core 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    Optional<TcEqpParam> findByEqpKeyAndNameVersion(long eqpKey, String paramName, String paramVersion);

    /**
     * 특정 설비(eqp_key)의 파라미터 목록 조회.
     * - 페이징은 반드시 DB 레벨에서 처리해야 한다.
     */
    List<TcEqpParam> findAllByEqpKey(long eqpKey, PageRequest page);

    /**
     * 특정 설비(eqp_key)와 버전(paramVersion)의 파라미터 목록 조회.
     *
     * @param eqpKey 설비 식별 키
     * @param paramVersion 파라미터 버전 (예: "v1.0", "EDIT")
     * @return 해당 설비/버전의 파라미터 목록
     */
    List<TcEqpParam> findAllByEqpKeyAndVersion(long eqpKey, String paramVersion);

    /**
     * 특정 설비(eqp_key)와 버전(paramVersion)의 파라미터 존재 여부를 확인합니다.
     *
     * <p>체크아웃 잠금 여부 확인에 사용합니다. EDIT 버전 존재 = 체크아웃 중.</p>
     *
     * @param eqpKey 설비 식별 키
     * @param paramVersion 확인할 파라미터 버전
     * @return 해당 버전의 파라미터가 1건 이상 존재하면 true
     */
    boolean existsByEqpKeyAndVersion(long eqpKey, String paramVersion);

    /**
     * 특정 설비(eqp_key)와 버전(paramVersion)의 파라미터 전체를 삭제합니다.
     *
     * <p>체크인 완료 후 EDIT 버전을 정리하는 데 사용합니다.</p>
     *
     * @param eqpKey 설비 식별 키
     * @param paramVersion 삭제할 파라미터 버전
     */
    void deleteAllByEqpKeyAndVersion(long eqpKey, String paramVersion);

    
    /**
     * DB Core 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param eqpParamKey 대상 키 값
     */
    void deleteByEqpParamKey(long eqpParamKey);
}
