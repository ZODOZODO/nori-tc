package com.nori.tc.db.jpa.common.repository.eqp;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nori.tc.db.jpa.common.entity.eqp.TcEqpGlobalEntity;

/**
 * tc_eqp_global Repository
 */
public interface TcEqpGlobalJpaRepository extends JpaRepository<TcEqpGlobalEntity, Long> {

    Optional<TcEqpGlobalEntity> findByEqpKeyAndParamName(Long eqpKey, String paramName);

    List<TcEqpGlobalEntity> findByEqpKey(Long eqpKey);

    void deleteByEqpKeyAndParamName(Long eqpKey, String paramName);
}
