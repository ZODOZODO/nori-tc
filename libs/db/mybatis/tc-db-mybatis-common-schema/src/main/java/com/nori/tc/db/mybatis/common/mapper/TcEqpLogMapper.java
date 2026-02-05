package com.nori.tc.db.mybatis.common.mapper;

import java.util.Optional;

import org.apache.ibatis.annotations.Param;

import com.nori.tc.db.domain.eqp.TcEqpLog;

/**
 * tc_eqp_log Mapper (FIX)
 *
 * - 1:1 테이블, PK=eqp_key
 */
public interface TcEqpLogMapper {

    int insert(@Param("l") TcEqpLog log);

    int update(@Param("l") TcEqpLog log);

    Optional<TcEqpLog> findByEqpKey(@Param("eqpKey") long eqpKey);

    int deleteByEqpKey(@Param("eqpKey") long eqpKey);
}
