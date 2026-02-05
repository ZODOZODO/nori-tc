package com.nori.tc.db.jpa.common.repository.model;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nori.tc.db.jpa.common.entity.model.TcModelSocketMessageEntity;

/**
 * tc_model_socket_message Repository
 */
public interface TcModelSocketMessageJpaRepository extends JpaRepository<TcModelSocketMessageEntity, Long> {

    Optional<TcModelSocketMessageEntity> findByModelKeyAndSocketMsgName(long modelKey, String socketMsgName);
}
