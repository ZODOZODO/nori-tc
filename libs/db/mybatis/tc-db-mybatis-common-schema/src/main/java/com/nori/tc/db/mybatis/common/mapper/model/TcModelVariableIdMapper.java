package com.nori.tc.db.mybatis.common.mapper.model;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Param;

import com.nori.tc.db.domain.common.VariableIdType;
import com.nori.tc.db.domain.model.TcModelVariableId;

/**
 * tc_model_variableid Mapper (FIX)
 *
 * - 이 모듈은 "보수적인 CRUD"만 제공한다.
 * - variable_key 생성(IDENTITY) 처리 때문에 insert 후 key를 직접 반환하지 않는다.
 * - upsert는 unique(model_key, variable_id_type, variable_id) 기반으로 수행한다.
 */
public interface TcModelVariableIdMapper {

    int insert(@Param("m") TcModelVariableId modelVariableId);

    int updateByUniqueKey(@Param("m") TcModelVariableId modelVariableId);

    Optional<TcModelVariableId> findByVariableKey(@Param("variableKey") long variableKey);

    Optional<TcModelVariableId> findByModelKeyAndTypeAndVariableId(
            @Param("modelKey") long modelKey,
            @Param("variableIdType") VariableIdType variableIdType,
            @Param("variableId") String variableId
    );

    List<TcModelVariableId> findAllByModelKey(
            @Param("modelKey") long modelKey,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    int deleteByVariableKey(@Param("variableKey") long variableKey);
}
