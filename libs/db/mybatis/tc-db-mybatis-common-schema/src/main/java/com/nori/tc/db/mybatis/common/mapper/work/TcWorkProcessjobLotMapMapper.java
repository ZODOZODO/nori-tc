package com.nori.tc.db.mybatis.common.mapper.work;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Param;

import com.nori.tc.db.domain.work.TcWorkProcessjobLotMap;

/**
 * tc_work_processjob_lot_map Mapper.
 *
 * <p>
 * - PK: pj_lot_map_key (identity)
 * - Unique: (process_job_key, work_lot_key)
 * </p>
 */
public interface TcWorkProcessjobLotMapMapper {

    int insert(@Param("m") TcWorkProcessjobLotMap map);

    int update(@Param("m") TcWorkProcessjobLotMap map);

    Optional<TcWorkProcessjobLotMap> findByPjLotMapKey(@Param("pjLotMapKey") long pjLotMapKey);

    Optional<TcWorkProcessjobLotMap> findByProcessJobKeyAndWorkLotKey(
            @Param("processJobKey") long processJobKey,
            @Param("workLotKey") long workLotKey
    );

    List<TcWorkProcessjobLotMap> findAllByProcessJobKey(
            @Param("processJobKey") long processJobKey,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    int deleteByPjLotMapKey(@Param("pjLotMapKey") long pjLotMapKey);
}
