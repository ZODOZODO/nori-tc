package com.nori.tc.db.mybatis.common.mapper.work;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Param;

import com.nori.tc.db.domain.work.TcWorkControlJob;

/**
 * tc_work_controljob Mapper.
 *
 * <p>
 * - Unique: (work_key, controljob_id)
 * - PK: control_job_key (identity)
 * </p>
 */
public interface TcWorkControlJobMapper {

    int insert(@Param("c") TcWorkControlJob controlJob);

    int update(@Param("c") TcWorkControlJob controlJob);

    Optional<TcWorkControlJob> findByControlJobKey(@Param("controlJobKey") long controlJobKey);

    Optional<TcWorkControlJob> findByWorkKeyAndControljobId(
            @Param("workKey") long workKey,
            @Param("controljobId") String controljobId
    );

    List<TcWorkControlJob> findAllByWorkKey(
            @Param("workKey") long workKey,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    int deleteByControlJobKey(@Param("controlJobKey") long controlJobKey);
}
