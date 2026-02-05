package com.nori.tc.db.jpa.common.repository.model;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nori.tc.db.jpa.common.entity.model.TcModelWorkflowEntity;

public interface TcModelWorkflowJpaRepository extends JpaRepository<TcModelWorkflowEntity, Long> {

    Optional<TcModelWorkflowEntity> findByModelKeyAndWorkflowNameAndMessageName(
            long modelKey,
            String workflowName,
            String messageName
    );
}
