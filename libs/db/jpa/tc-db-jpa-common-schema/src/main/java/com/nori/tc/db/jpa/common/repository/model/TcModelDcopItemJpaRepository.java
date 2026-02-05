package com.nori.tc.db.jpa.common.repository.model;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nori.tc.db.jpa.common.entity.model.TcModelDcopItemEntity;

public interface TcModelDcopItemJpaRepository extends JpaRepository<TcModelDcopItemEntity, Long> {

    Optional<TcModelDcopItemEntity> findByModelKeyAndDcopItemName(long modelKey, String dcopItemName);

    List<TcModelDcopItemEntity> findByModelKey(long modelKey);

    void deleteByModelKeyAndDcopItemName(long modelKey, String dcopItemName);
}
