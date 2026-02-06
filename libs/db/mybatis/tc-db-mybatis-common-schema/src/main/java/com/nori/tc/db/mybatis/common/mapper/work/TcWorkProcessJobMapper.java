package com.nori.tc.db.mybatis.common.mapper.work;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Param;

import com.nori.tc.db.domain.work.TcWorkProcessJob;

/**
 * tc_work_processjob Mapper (FIX)
 *
 * <p>
 * - Unique: (control_job_key, processjob_id)
 * - PK: process_job_key (identity)
 * </p>
 */
public interface TcWorkProcessJobMapper {

    int insert(@Param("p") TcWorkProcessJob processJob);

    int update(@Param("p") TcWorkProcessJob processJob);

    Optional<TcWorkProcessJob> findByProcessJobKey(@Param("processJobKey") long processJobKey);

    Optional<TcWorkProcessJob> findByControlJobKeyAndProcessjobId(
            @Param("controlJobKey") long controlJobKey,
            @Param("processjobId") String processjobId
    );

    List<TcWorkProcessJob> findAllByControlJobKey(
            @Param("controlJobKey") long controlJobKey,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    int deleteByProcessJobKey(@Param("processJobKey") long processJobKey);
}
