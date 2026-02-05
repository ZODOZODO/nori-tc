package com.nori.tc.db.mybatis.common.mapper.model;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Param;

import com.nori.tc.db.domain.model.TcModelParam;

/**
 * tc_model_param Mapper (FIX)
 *
 * - Unique(model_key, param_name) 기준으로 upsert 지원
 * - findAllByModelKey는 반드시 DB 페이징을 적용한다.
 */
public interface TcModelParamMapper {

    int insert(@Param("m") TcModelParam param);

    int updateByUniqueKey(@Param("m") TcModelParam param);

    Optional<TcModelParam> findByModelKeyAndName(
            @Param("modelKey") long modelKey,
            @Param("paramName") String paramName
    );

    List<TcModelParam> findAllByModelKey(
            @Param("modelKey") long modelKey,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    int deleteByModelParamKey(@Param("modelParamKey") long modelParamKey);
}
