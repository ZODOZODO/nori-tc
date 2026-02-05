package com.nori.tc.db.mybatis.common.mapper.eqp;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Param;

import com.nori.tc.db.domain.eqp.TcEqpParam;

/**
 * tc_eqp_param Mapper (FIX)
 *
 * - Unique(eqp_key, param_name, param_version) 기준으로 upsert 지원
 * - findAllByEqpKey는 반드시 DB 페이징을 적용한다.
 */
public interface TcEqpParamMapper {

    int insert(@Param("e") TcEqpParam param);

    int updateByUniqueKey(@Param("e") TcEqpParam param);

    Optional<TcEqpParam> findByEqpKeyAndNameVersion(
            @Param("eqpKey") long eqpKey,
            @Param("paramName") String paramName,
            @Param("paramVersion") String paramVersion
    );

    List<TcEqpParam> findAllByEqpKey(
            @Param("eqpKey") long eqpKey,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    int deleteByEqpParamKey(@Param("eqpParamKey") long eqpParamKey);
}
