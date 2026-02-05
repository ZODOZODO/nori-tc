package com.nori.tc.db.jpa.common.repository.model;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nori.tc.db.jpa.common.entity.model.TcModelParamEntity;

public interface TcModelParamJpaRepository extends JpaRepository<TcModelParamEntity, Long> {

    Optional<TcModelParamEntity> findByModelKeyAndParamName(
            long modelKey,
            String paramName
    );
}
