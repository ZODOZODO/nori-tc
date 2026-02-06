package com.nori.tc.db.mybatis.common.mapper.model;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Param;

import com.nori.tc.db.domain.model.TcModelReportId;

/**
 * tc_model_reportid Mapper (FIX)
 *
 * - Unique(model_key, report_id) 기준으로 upsert 지원
 * - findAll은 반드시 DB 페이징을 적용한다.
 */
public interface TcModelReportIdMapper {

    int insert(@Param("r") TcModelReportId report);

    int updateByUniqueKey(@Param("r") TcModelReportId report);

    Optional<TcModelReportId> findByReportKey(@Param("reportKey") long reportKey);

    Optional<TcModelReportId> findByModelKeyAndReportId(
            @Param("modelKey") long modelKey,
            @Param("reportId") String reportId
    );

    List<TcModelReportId> findAllByModelKey(
            @Param("modelKey") long modelKey,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    int deleteByReportKey(@Param("reportKey") long reportKey);
}