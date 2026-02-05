package com.nori.tc.db.mybatis.common.mapper.model;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Param;

import com.nori.tc.db.domain.model.TcModelDcopItem;

/**
 * tc_model_dcop_item Mapper (FIX)
 *
 * - Unique(model_key, dcop_item_name)을 기준으로 upsert/조회/삭제를 수행한다.
 * - [FIX] findAll은 DB 페이징(offset/limit)을 필수로 사용한다.
 */
public interface TcModelDcopItemMapper {

    int insert(@Param("i") TcModelDcopItem item);

    int update(@Param("i") TcModelDcopItem item);

    Optional<TcModelDcopItem> findByModelKeyAndName(
            @Param("modelKey") long modelKey,
            @Param("dcopItemName") String dcopItemName
    );

    List<TcModelDcopItem> findAll(
            @Param("modelKey") long modelKey,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    int deleteByModelKeyAndName(
            @Param("modelKey") long modelKey,
            @Param("dcopItemName") String dcopItemName
    );
}
