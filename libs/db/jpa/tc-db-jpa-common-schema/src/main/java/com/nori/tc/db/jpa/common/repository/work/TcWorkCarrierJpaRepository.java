package com.nori.tc.db.jpa.common.repository.work;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nori.tc.db.jpa.common.entity.work.TcWorkCarrierEntity;

/**
 * tc_work_carrier Repository
 */
public interface TcWorkCarrierJpaRepository extends JpaRepository<TcWorkCarrierEntity, Long> {

    Optional<TcWorkCarrierEntity> findByWorkKeyAndCarrierId(Long workKey, String carrierId);

    List<TcWorkCarrierEntity> findByWorkKey(Long workKey);
}
