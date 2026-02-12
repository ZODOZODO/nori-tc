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

    
    /**
     * DB Core 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     * @return DB Core 계층 처리 결과
     */
    TcModelParam upsert(UpsertTcModelParam command);

    
    /**
     * DB Core 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param modelKey 대상 키 값
     * @param paramName DB Core 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    Optional<TcModelParam> findByModelKeyAndName(long modelKey, String paramName);

    /**
     * 특정 모델(model_key)의 파라미터 목록 조회.
     * - 페이징은 반드시 DB 레벨에서 처리해야 한다.
     */
    List<TcModelParam> findAllByModelKey(long modelKey, PageRequest page);

    
    /**
     * DB Core 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param modelParamKey 대상 키 값
     */
    void deleteByModelParamKey(long modelParamKey);
}
