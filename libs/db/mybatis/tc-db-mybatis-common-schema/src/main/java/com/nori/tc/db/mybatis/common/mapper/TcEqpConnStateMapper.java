package com.nori.tc.db.mybatis.common.mapper;

import java.util.Optional;

import org.apache.ibatis.annotations.Param;

import com.nori.tc.db.domain.eqp.TcEqpConnState;

/**
 * tc_eqp_conn_state Mapper (FIX)
 *
 * - 1:1 테이블, PK=eqp_id
 * - since_at은 NOT NULL이며 DB default now()가 있으나,
 *   insert 시 null이 들어오면 default가 깨질 수 있으므로 SQL에서 안전 처리한다.
 */
public interface TcEqpConnStateMapper {

    int insert(@Param("s") TcEqpConnState state);

    int update(@Param("s") TcEqpConnState state);

    Optional<TcEqpConnState> findByEqpId(@Param("eqpId") String eqpId);

    int deleteByEqpId(@Param("eqpId") String eqpId);
}
