package com.nori.tc.db.mybatis.common.mapper.eqp;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Param;

import com.nori.tc.db.domain.eqp.TcEqpSocketProtocolType;

/**
 * tc_eqp_socket_protocol_type Mapper (FIX)
 *
 * - 코드성 테이블이지만, 조회 시 리스트가 커질 수 있으므로 LIMIT/OFFSET을 강제합니다.
 */
public interface TcEqpSocketProtocolTypeMapper {

    int insert(@Param("t") TcEqpSocketProtocolType type);

    int update(@Param("t") TcEqpSocketProtocolType type);

    Optional<TcEqpSocketProtocolType> findBySocketProtocolType(@Param("socketProtocolType") String socketProtocolType);

    List<TcEqpSocketProtocolType> findAll(@Param("offset") int offset, @Param("limit") int limit);

    int deleteBySocketProtocolType(@Param("socketProtocolType") String socketProtocolType);
}
