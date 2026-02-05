package com.nori.tc.db.mybatis.common.mapper.eqp;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Param;

import com.nori.tc.db.domain.eqp.TcEqpGlobal;

/**
 * tc_eqp_global Mapper (FIX)
 *
 * - 유니크 키: (eqp_key, param_name)
 */
public interface TcEqpGlobalMapper {

    int insert(@Param("g") TcEqpGlobal global);

    int update(@Param("g") TcEqpGlobal global);

    Optional<TcEqpGlobal> findByEqpKeyAndParamName(@Param("eqpKey") long eqpKey, @Param("paramName") String paramName);

    List<TcEqpGlobal> findByEqpKey(@Param("eqpKey") long eqpKey);

    int deleteByEqpKeyAndParamName(@Param("eqpKey") long eqpKey, @Param("paramName") String paramName);
}
