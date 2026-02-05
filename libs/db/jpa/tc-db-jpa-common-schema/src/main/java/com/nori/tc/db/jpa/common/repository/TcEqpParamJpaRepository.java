package com.nori.tc.db.jpa.common.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nori.tc.db.jpa.common.entity.TcEqpParamEntity;

public interface TcEqpParamJpaRepository extends JpaRepository<TcEqpParamEntity, Long> {

    Optional<TcEqpParamEntity> findByEqpKeyAndParamNameAndParamVersion(
            long eqpKey,
            String paramName,
            String paramVersion
    );
}
