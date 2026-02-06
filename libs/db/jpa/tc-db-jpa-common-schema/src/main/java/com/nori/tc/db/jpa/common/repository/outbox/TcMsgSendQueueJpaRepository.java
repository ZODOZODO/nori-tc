package com.nori.tc.db.jpa.common.repository.outbox;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nori.tc.db.jpa.common.entity.outbox.TcMsgSendQueueEntity;

/**
 * tc_msg_send_queue Spring Data JPA Repository.
 */
public interface TcMsgSendQueueJpaRepository extends JpaRepository<TcMsgSendQueueEntity, Long> {

    Optional<TcMsgSendQueueEntity> findByTopicAndIdempotencyKey(String topic, String idempotencyKey);
}
