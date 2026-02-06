package com.nori.tc.db.mybatis.common.mapper.outbox;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Param;

import com.nori.tc.db.domain.common.outbox.TcMsgSendStatus;
import com.nori.tc.db.domain.outbox.TcMsgSendQueue;

/**
 * tc_msg_send_queue Mapper (FIX)
 *
 * <p>
 * - Unique: (topic, idempotency_key)
 * - PK: msg_key (identity)
 * </p>
 */
public interface TcMsgSendQueueMapper {

    int insert(@Param("q") TcMsgSendQueue queue);

    int update(@Param("q") TcMsgSendQueue queue);

    Optional<TcMsgSendQueue> findByMsgKey(@Param("msgKey") long msgKey);

    Optional<TcMsgSendQueue> findByTopicAndIdempotencyKey(
            @Param("topic") String topic,
            @Param("idempotencyKey") String idempotencyKey
    );

    List<TcMsgSendQueue> findAllByStatus(
            @Param("status") TcMsgSendStatus status,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    int deleteByMsgKey(@Param("msgKey") long msgKey);
}
