package com.nori.tc.db.mybatis.common.mapper.model;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Param;

import com.nori.tc.db.domain.model.TcModelSocketMessage;

/**
 * tc_model_socket_message Mapper (FIX)
 *
 * - Unique Key: (model_key, socket_msg_name)
 * - socket_msg_key는 IDENTITY이므로 insert 후 재조회 방식 사용
 */
public interface TcModelSocketMessageMapper {

    int insert(@Param("s") TcModelSocketMessage socketMessage);

    int update(@Param("s") TcModelSocketMessage socketMessage);

    Optional<TcModelSocketMessage> findByModelKeySocketMsgName(
            @Param("modelKey") long modelKey,
            @Param("socketMsgName") String socketMsgName
    );

    List<TcModelSocketMessage> findAllByModelKey(
            @Param("modelKey") long modelKey,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    int deleteByModelKeySocketMsgName(
            @Param("modelKey") long modelKey,
            @Param("socketMsgName") String socketMsgName
    );
}
