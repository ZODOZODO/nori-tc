package com.nori.tc.db.mybatis.common.mapper;

import java.util.Optional;

import org.apache.ibatis.annotations.Param;

import com.nori.tc.db.domain.eqp.TcEqpSocket;

/**
 * tc_eqp_socket Mapper (FIX)
 *
 * - 1:1 테이블, PK=eqp_id
 * - charset 기본값('UTF-8')은 DB default가 있으나,
 *   insert에 null이 들어오면 default가 깨질 수 있어 SQL에서 안전 처리한다.
 */
public interface TcEqpSocketMapper {

    int insert(@Param("s") TcEqpSocket socket);

    int update(@Param("s") TcEqpSocket socket);

    Optional<TcEqpSocket> findByEqpId(@Param("eqpId") String eqpId);

    int deleteByEqpId(@Param("eqpId") String eqpId);
}
