package com.nori.tc.db.mybatis.common.mapper.model;

import java.util.Optional;

import org.apache.ibatis.annotations.Param;

import com.nori.tc.db.domain.model.TcModelEventId;

/**
 * tc_model_eventid Mapper (FIX)
 *
 * - (model_key, event_id) 조합이 유니크 키
 */
public interface TcModelEventIdMapper {

    int insert(@Param("e") TcModelEventId modelEventId);

    int update(@Param("e") TcModelEventId modelEventId);

    Optional<TcModelEventId> findByEventKey(@Param("eventKey") long eventKey);

    Optional<TcModelEventId> findByModelKeyAndEventId(
            @Param("modelKey") long modelKey,
            @Param("eventId") String eventId
    );

    int deleteByEventKey(@Param("eventKey") long eventKey);
}
