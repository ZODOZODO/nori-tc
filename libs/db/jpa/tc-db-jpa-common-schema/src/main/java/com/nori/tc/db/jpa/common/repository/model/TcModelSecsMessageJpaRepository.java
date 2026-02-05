package com.nori.tc.db.jpa.common.repository.model;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nori.tc.db.jpa.common.entity.model.TcModelSecsMessageEntity;

/**
 * tc_model_secs_message Repository
 */
public interface TcModelSecsMessageJpaRepository extends JpaRepository<TcModelSecsMessageEntity, Long> {

    List<TcModelSecsMessageEntity> findByModelKey(long modelKey);

    Optional<TcModelSecsMessageEntity> findByModelKeyAndSecsMsgName(long modelKey, String secsMsgName);
}
