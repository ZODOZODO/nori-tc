package com.nori.tc.db.jpa.common.repository.work;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nori.tc.db.jpa.common.entity.work.TcWorkLotEntity;

public interface TcWorkLotJpaRepository extends JpaRepository<TcWorkLotEntity, Long> {

    Optional<TcWorkLotEntity> findByWorkKeyAndLotId(long workKey, String lotId);
}
