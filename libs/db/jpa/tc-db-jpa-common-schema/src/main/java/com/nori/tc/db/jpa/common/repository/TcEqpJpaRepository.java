package com.nori.tc.db.jpa.common.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nori.tc.db.jpa.common.entity.TcEqpEntity;

/**
 * tc_eqp Repository
 */
public interface TcEqpJpaRepository extends JpaRepository<TcEqpEntity, String> {
}
