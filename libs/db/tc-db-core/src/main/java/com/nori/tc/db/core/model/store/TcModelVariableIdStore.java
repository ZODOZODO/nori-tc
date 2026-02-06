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
 * - Unique(model_key, variable_id_type, variable_id)를 기준으로 upsert를 수행한다.
 * - variable_key는 DB IDENTITY이므로 애플리케이션에서 직접 생성하지 않는다.
 * </p>
 */
public interface TcModelVariableIdStore {

    TcModelVariableId upsert(UpsertTcModelVariableId command);

    Optional<TcModelVariableId> findByVariableKey(long variableKey);

    Optional<TcModelVariableId> findByModelKeyAndTypeAndVariableId(
            long modelKey,
            VariableIdType variableIdType,
            String variableId
    );

    /**
     * 특정 모델(model_key)의 변수 ID 목록 조회.
     * - 페이징은 반드시 DB 레벨에서 처리해야 한다.
     */
    List<TcModelVariableId> findAllByModelKey(long modelKey, PageRequest page);

    void deleteByVariableKey(long variableKey);
}
