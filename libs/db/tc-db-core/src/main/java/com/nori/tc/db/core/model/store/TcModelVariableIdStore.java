package com.nori.tc.db.core.model.store;

import java.util.List;
import java.util.Optional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.model.upsert.UpsertTcModelVariableId;
import com.nori.tc.db.domain.common.model.VariableIdType;
import com.nori.tc.db.domain.model.TcModelVariableId;

/**
 * tc_model_variableid CRUD 인터페이스.
 *
 * <p>
 * - Unique(model_version_key, variable_id_type, variable_id)를 기준으로 upsert를 수행한다.
 * - variable_key는 DB IDENTITY이므로 애플리케이션에서 직접 생성하지 않는다.
 * </p>
 */
public interface TcModelVariableIdStore {

    
    /**
     * DB Core 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     * @return DB Core 계층 처리 결과
     */
    TcModelVariableId upsert(UpsertTcModelVariableId command);

    
    /**
     * DB Core 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param variableKey 대상 키 값
     * @return 조회 결과(Optional)
     */
    Optional<TcModelVariableId> findByVariableKey(long variableKey);

    
    /**
     * DB Core 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param modelVersionKey 대상 키 값
     * @param variableIdType DB Core 계층 처리에 사용하는 입력 값
     * @param variableId DB Core 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    Optional<TcModelVariableId> findByModelVersionKeyAndTypeAndVariableId(
            long modelVersionKey,
            VariableIdType variableIdType,
            String variableId
    );

    /**
     * 특정 모델(model_version_key)의 변수 ID 목록 조회.
     * - 페이징은 반드시 DB 레벨에서 처리해야 한다.
     */
    List<TcModelVariableId> findAllByModelVersionKey(long modelVersionKey, PageRequest page);

    
    /**
     * DB Core 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param variableKey 대상 키 값
     */
    void deleteByVariableKey(long variableKey);
}
