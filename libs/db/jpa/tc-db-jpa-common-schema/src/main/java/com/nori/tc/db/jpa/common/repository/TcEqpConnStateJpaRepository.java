package com.nori.tc.db.jpa.common.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nori.tc.db.jpa.common.entity.TcEqpConnStateEntity;

/**
 * tc_eqp_conn_state Repository
 */
public interface TcEqpConnStateJpaRepository extends JpaRepository<TcEqpConnStateEntity, String> {
}
