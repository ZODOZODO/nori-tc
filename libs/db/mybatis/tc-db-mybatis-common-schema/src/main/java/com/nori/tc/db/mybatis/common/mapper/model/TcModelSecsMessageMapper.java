package com.nori.tc.db.mybatis.common.mapper.model;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Param;

import com.nori.tc.db.domain.model.TcModelSecsMessage;

/**
 * tc_model_secs_message Mapper (FIX)
 *
 * - model_key + secs_msg_name 유니크
 */
public interface TcModelSecsMessageMapper {

    int insert(@Param("m") TcModelSecsMessage message);

    int update(@Param("m") TcModelSecsMessage message);

    Optional<TcModelSecsMessage> findBySecsMsgKey(@Param("secsMsgKey") long secsMsgKey);

    Optional<TcModelSecsMessage> findByModelKeyAndName(
            @Param("modelKey") long modelKey,
            @Param("secsMsgName") String secsMsgName
    );

    List<TcModelSecsMessage> findByModelKey(@Param("modelKey") long modelKey);

    int deleteBySecsMsgKey(@Param("secsMsgKey") long secsMsgKey);
}
