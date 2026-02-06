package com.nori.tc.db.jpa.common.repository.model;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nori.tc.db.domain.common.model.VariableIdType;
import com.nori.tc.db.jpa.common.entity.model.TcModelVariableIdEntity;

/**
 * tc_model_variableid Repository
 */
public interface TcModelVariableIdJpaRepository extends JpaRepository<TcModelVariableIdEntity, Long> {

    Optional<TcModelVariableIdEntity> findByModelKeyAndVariableIdTypeAndVariableId(
            long modelKey,
            VariableIdType variableIdType,
            String variableId
    );
}
