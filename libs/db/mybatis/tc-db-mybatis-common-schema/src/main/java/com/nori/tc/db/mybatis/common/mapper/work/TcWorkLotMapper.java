package com.nori.tc.db.mybatis.common.mapper.work;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Param;

import com.nori.tc.db.domain.work.TcWorkLot;

/**
 * tc_work_lot Mapper (FIX)
 *
 * - Unique(work_key, lot_id) 기준으로 upsert 지원
 * - findAllByWorkKey는 반드시 DB 페이징을 적용한다.
 * - nullable 컬럼이 존재하므로 insert/update 시 null 입력을 허용한다.
 */
public interface TcWorkLotMapper {

    int insert(@Param("w") TcWorkLot workLot);

    int updateByUniqueKey(@Param("w") TcWorkLot workLot);

    Optional<TcWorkLot> findByWorkKeyAndLotId(
            @Param("workKey") long workKey,
            @Param("lotId") String lotId
    );

    List<TcWorkLot> findAllByWorkKey(
            @Param("workKey") long workKey,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    int deleteByWorkLotKey(@Param("workLotKey") long workLotKey);
}
