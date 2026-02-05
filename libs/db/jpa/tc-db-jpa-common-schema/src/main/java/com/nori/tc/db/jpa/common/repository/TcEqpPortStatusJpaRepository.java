package com.nori.tc.db.jpa.common.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nori.tc.db.jpa.common.entity.TcEqpPortStatusEntity;

/**
 * tc_eqp_port_status Repository
 */
public interface TcEqpPortStatusJpaRepository extends JpaRepository<TcEqpPortStatusEntity, Long> {

    Optional<TcEqpPortStatusEntity> findByEqpKeyAndPortId(Long eqpKey, String portId);

    List<TcEqpPortStatusEntity> findByEqpKey(Long eqpKey);
}
