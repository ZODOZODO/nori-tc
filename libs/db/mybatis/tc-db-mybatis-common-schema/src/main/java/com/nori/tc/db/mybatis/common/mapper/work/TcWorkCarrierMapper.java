package com.nori.tc.db.mybatis.common.mapper.work;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Param;

import com.nori.tc.db.domain.work.TcWorkCarrier;

/**
 * tc_work_carrier Mapper (FIX)
 *
 * <p>
 * - Unique Key: (work_key, carrier_id)
 * - work_carrier_key는 IDENTITY이므로 insert 후 재조회 방식 사용
 * </p>
 */
public interface TcWorkCarrierMapper {

    int insert(@Param("c") TcWorkCarrier carrier);

    int update(@Param("c") TcWorkCarrier carrier);

    Optional<TcWorkCarrier> findByWorkKeyCarrierId(@Param("workKey") long workKey, @Param("carrierId") String carrierId);

    List<TcWorkCarrier> findAllByWorkKey(@Param("workKey") long workKey, @Param("offset") int offset, @Param("limit") int limit);

    int deleteByWorkKeyCarrierId(@Param("workKey") long workKey, @Param("carrierId") String carrierId);
}
