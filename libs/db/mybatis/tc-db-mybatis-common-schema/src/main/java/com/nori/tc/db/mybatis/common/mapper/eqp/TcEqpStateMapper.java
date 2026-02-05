package com.nori.tc.db.mybatis.common.mapper.eqp;

import java.util.Optional;

import org.apache.ibatis.annotations.Param;

import com.nori.tc.db.domain.eqp.TcEqpState;

/**
 * tc_eqp_state Mapper (FIX)
 *
 * - 1:1 테이블, PK=eqp_key
 * - NULL 허용 컬럼이 많으므로 insert/update 시 null 입력을 허용한다.
 */
public interface TcEqpStateMapper {

    int insert(@Param("s") TcEqpState state);

    int update(@Param("s") TcEqpState state);

    Optional<TcEqpState> findByEqpKey(@Param("eqpKey") long eqpKey);

    int deleteByEqpKey(@Param("eqpKey") long eqpKey);
}
