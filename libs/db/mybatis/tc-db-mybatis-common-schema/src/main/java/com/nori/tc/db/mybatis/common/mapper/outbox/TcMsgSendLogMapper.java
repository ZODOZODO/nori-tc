package com.nori.tc.db.mybatis.common.mapper.outbox;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Param;

import com.nori.tc.db.domain.outbox.TcMsgSendLog;

/**
 * tc_msg_send_log Mapper (FIX)
 *
 * - 논리 키: (msg_key, attempt_no)
 */
public interface TcMsgSendLogMapper {

    int insert(@Param("l") TcMsgSendLog log);

    int update(@Param("l") TcMsgSendLog log);

    Optional<TcMsgSendLog> findByMsgKeyAttemptNo(
            @Param("msgKey") long msgKey,
            @Param("attemptNo") int attemptNo
    );

    List<TcMsgSendLog> findAllByMsgKey(
            @Param("msgKey") long msgKey,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    int deleteByMsgKeyAttemptNo(
            @Param("msgKey") long msgKey,
            @Param("attemptNo") int attemptNo
    );
}
