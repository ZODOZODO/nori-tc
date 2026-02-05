package com.nori.tc.db.jpa.common.repository.eqp;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nori.tc.db.jpa.common.entity.eqp.TcEqpStateHistEntity;

/**
 * tc_eqp_state_hist Repository
 */
public interface TcEqpStateHistJpaRepository extends JpaRepository<TcEqpStateHistEntity, Long> {
}
