package com.nori.tc.db.mybatis.common.mapper;

import java.util.Optional;

import org.apache.ibatis.annotations.Param;

import com.nori.tc.db.domain.eqp.TcEqpLog;

/**
 * tc_eqp_log Mapper (FIX)
 *
 * - 1:1 테이블, PK=eqp_id
 */
public interface TcEqpLogMapper {

    int insert(@Param("l") TcEqpLog log);

    int update(@Param("l") TcEqpLog log);

    Optional<TcEqpLog> findByEqpId(@Param("eqpId") String eqpId);

    int deleteByEqpId(@Param("eqpId") String eqpId);
}
