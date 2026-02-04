package com.nori.tc.db.mybatis.common.mapper;

import java.util.Optional;

import org.apache.ibatis.annotations.Param;

import com.nori.tc.db.domain.eqp.TcEqpOperState;

/**
 * tc_eqp_oper_state Mapper (FIX)
 *
 * - 1:1 테이블, PK=eqp_id
 * - since_at NOT NULL, DB default now()
 *   → insert 시 null이 들어오면 default가 깨질 수 있어 SQL에서 안전 처리한다.
 */
public interface TcEqpOperStateMapper {

    int insert(@Param("o") TcEqpOperState operState);

    int update(@Param("o") TcEqpOperState operState);

    Optional<TcEqpOperState> findByEqpId(@Param("eqpId") String eqpId);

    int deleteByEqpId(@Param("eqpId") String eqpId);
}
