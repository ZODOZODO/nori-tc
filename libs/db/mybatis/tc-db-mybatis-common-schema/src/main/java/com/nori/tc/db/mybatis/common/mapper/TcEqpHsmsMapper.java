package com.nori.tc.db.mybatis.common.mapper;

import java.util.Optional;

import org.apache.ibatis.annotations.Param;

import com.nori.tc.db.domain.eqp.TcEqpHsms;

/**
 * tc_eqp_hsms Mapper (FIX)
 *
 * - 1:1 테이블, PK=eqp_key
 * - created_at/updated_at은 DB default(now())를 신뢰하고 insert에서 생략한다.
 * - update 시 updated_at은 CURRENT_TIMESTAMP로 갱신한다.
 */
public interface TcEqpHsmsMapper {

    int insert(@Param("h") TcEqpHsms hsms);

    int update(@Param("h") TcEqpHsms hsms);

    Optional<TcEqpHsms> findByEqpKey(@Param("eqpKey") long eqpKey);

    int deleteByEqpKey(@Param("eqpKey") long eqpKey);
}
