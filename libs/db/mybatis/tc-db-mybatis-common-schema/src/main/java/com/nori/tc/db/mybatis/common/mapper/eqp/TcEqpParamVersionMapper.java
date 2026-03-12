package com.nori.tc.db.mybatis.common.mapper.eqp;

import com.nori.tc.db.domain.eqp.TcEqpParamVersion;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * tc_eqp_param_version Mapper입니다.
 */
public interface TcEqpParamVersionMapper {

    int insert(@Param("e") TcEqpParamVersion paramVersion);

    int updateByUniqueKey(@Param("e") TcEqpParamVersion paramVersion);

    Optional<TcEqpParamVersion> findByEqpKeyAndParamVersion(
            @Param("eqpKey") long eqpKey,
            @Param("paramVersion") String paramVersion
    );

    List<TcEqpParamVersion> findAllByEqpKey(
            @Param("eqpKey") long eqpKey,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    int deleteAllByEqpKey(@Param("eqpKey") long eqpKey);
}
