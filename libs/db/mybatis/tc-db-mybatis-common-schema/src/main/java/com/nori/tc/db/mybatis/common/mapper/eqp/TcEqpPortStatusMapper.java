package com.nori.tc.db.mybatis.common.mapper.eqp;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Param;

import com.nori.tc.db.domain.eqp.TcEqpPortStatus;

/**
 * tc_eqp_port_status Mapper (FIX)
 *
 * - Unique Key: (eqp_key, port_id)
 * - eqp_port_status_key는 IDENTITY이므로 insert 후 재조회 방식 사용
 */
public interface TcEqpPortStatusMapper {

    int insert(@Param("s") TcEqpPortStatus status);

    int update(@Param("s") TcEqpPortStatus status);

    Optional<TcEqpPortStatus> findByEqpKeyPortId(@Param("eqpKey") long eqpKey, @Param("portId") String portId);

    List<TcEqpPortStatus> findAllByEqpKey(@Param("eqpKey") long eqpKey, @Param("offset") int offset, @Param("limit") int limit);

    int deleteByEqpKeyPortId(@Param("eqpKey") long eqpKey, @Param("portId") String portId);
}
