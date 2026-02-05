package com.nori.tc.db.mybatis.common.mapper.eqp;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.nori.tc.db.domain.eqp.TcEqpStateHist;

/**
 * tc_eqp_state_hist Mapper (FIX)
 */
@Mapper
public interface TcEqpStateHistMapper {

    int insert(@Param("h") TcEqpStateHist history);

    List<TcEqpStateHist> findAllByEqpKey(
            @Param("eqpKey") long eqpKey,
            @Param("limit") int limit,
            @Param("offset") int offset
    );
}
