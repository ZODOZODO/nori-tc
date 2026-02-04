package com.nori.tc.db.jpa.common.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nori.tc.db.jpa.common.entity.TcEqpLogEntity;

/**
 * tc_eqp_log Repository
 */
public interface TcEqpLogJpaRepository extends JpaRepository<TcEqpLogEntity, String> {
}
