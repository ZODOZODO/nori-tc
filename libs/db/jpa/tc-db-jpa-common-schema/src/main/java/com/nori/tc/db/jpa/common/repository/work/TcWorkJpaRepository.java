package com.nori.tc.db.jpa.common.repository.work;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nori.tc.db.jpa.common.entity.work.TcWorkEntity;

public interface TcWorkJpaRepository extends JpaRepository<TcWorkEntity, Long> {

    Optional<TcWorkEntity> findByEqpKeyAndWorkId(long eqpKey, String workId);
}
