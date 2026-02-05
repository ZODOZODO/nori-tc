package com.nori.tc.db.jpa.common.repository.model;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nori.tc.db.jpa.common.entity.model.TcModelEventIdEntity;

/**
 * tc_model_eventid Repository
 */
public interface TcModelEventIdJpaRepository extends JpaRepository<TcModelEventIdEntity, Long> {

    Optional<TcModelEventIdEntity> findByModelKeyAndEventId(Long modelKey, String eventId);
}
