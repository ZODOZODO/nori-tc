package com.nori.tc.db.jpa.common.repository.outbox;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nori.tc.db.jpa.common.entity.outbox.TcMsgSendLogEntity;

/**
 * tc_msg_send_log Repository
 */
public interface TcMsgSendLogJpaRepository extends JpaRepository<TcMsgSendLogEntity, Long> {

    Optional<TcMsgSendLogEntity> findByMsgKeyAndAttemptNo(long msgKey, int attemptNo);
}
