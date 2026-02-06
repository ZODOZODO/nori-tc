package com.nori.tc.db.mybatis.common.mapper.work;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Param;

import com.nori.tc.db.domain.work.TcWorkParam;

/**
 * tc_work_param Mapper (FIX)
 *
 * <p>
 * - Unique(work_key, param_name) 기준으로 upsert 지원
 * - findAllByWorkKey는 반드시 DB 페이징을 적용한다.
 * - work_param_key는 IDENTITY라서 insert 후 직접 반환하지 않는다.
 * </p>
 */
public interface TcWorkParamMapper {

    int insert(@Param("w") TcWorkParam param);

    int updateByUniqueKey(@Param("w") TcWorkParam param);

    Optional<TcWorkParam> findByWorkKeyAndName(
            @Param("workKey") long workKey,
            @Param("paramName") String paramName
    );

    List<TcWorkParam> findAllByWorkKey(
            @Param("workKey") long workKey,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    int deleteByWorkParamKey(@Param("workParamKey") long workParamKey);
}
