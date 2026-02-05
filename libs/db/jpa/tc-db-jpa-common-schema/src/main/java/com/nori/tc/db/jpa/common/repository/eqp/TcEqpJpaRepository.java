package com.nori.tc.db.jpa.common.repository.eqp;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nori.tc.db.jpa.common.entity.eqp.TcEqpEntity;

/**
 * tc_eqp Repository
 */
public interface TcEqpJpaRepository extends JpaRepository<TcEqpEntity, Long> {

    Optional<TcEqpEntity> findByEqpId(String eqpId);

    void deleteByEqpId(String eqpId);
}
